package ar.com.ciudaddigital.tenants.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.tenants.internal.AdministracionDeModulos.ModuloDeMunicipio;
import ar.com.ciudaddigital.tenants.internal.AltaDeMunicipio.Administrador;
import ar.com.ciudaddigital.tenants.internal.AltaDeMunicipio.SolicitudDeAlta;
import ar.com.ciudaddigital.tenants.internal.AltaDeMunicipio.SolicitudInvalida;
import ar.com.ciudaddigital.tenants.internal.TenantConfig.Tema;

/**
 * Alta y estado de los municipios (ADR 0005).
 *
 * <p>Superficie cross-tenant: es la única parte del sistema que ve a todos
 * los municipios a la vez. Protegida por sesión de usuario de plataforma
 * ({@link ConfiguracionDeSeguridadDePlataforma}, ADR 0010), no por el
 * token compartido que usaba R2.
 */
@RestController
@RequestMapping("/api/admin/municipios")
class AdministracionDeMunicipiosController {

    private final AltaDeMunicipio alta;
    private final TenantRepository repositorio;
    private final MigradorDeTenant migrador;
    private final AdministracionDeModulos administracionDeModulos;
    private final InformacionComercialDeMunicipios informacionComercial;
    private final SolicitudDeModuloRepository solicitudDeModuloRepositorio;

    AdministracionDeMunicipiosController(AltaDeMunicipio alta, TenantRepository repositorio,
            MigradorDeTenant migrador, AdministracionDeModulos administracionDeModulos,
            InformacionComercialDeMunicipios informacionComercial,
            SolicitudDeModuloRepository solicitudDeModuloRepositorio) {
        this.alta = alta;
        this.repositorio = repositorio;
        this.migrador = migrador;
        this.administracionDeModulos = administracionDeModulos;
        this.informacionComercial = informacionComercial;
        this.solicitudDeModuloRepositorio = solicitudDeModuloRepositorio;
    }

    @PostMapping
    ResponseEntity<MunicipioResponse> darDeAlta(@RequestBody AltaRequest request) {
        TenantEntity tenant = alta.darDeAlta(new SolicitudDeAlta(
                request.slug(),
                request.nombreMunicipio(),
                request.direccion(),
                request.telefono(),
                request.email(),
                request.tema(),
                request.administrador(),
                request.modulos()));

        return ResponseEntity.status(HttpStatus.CREATED).body(describir(tenant));
    }

    /**
     * Estado de cada municipio, incluida la versión de esquema que tiene
     * aplicada su base.
     *
     * <p>La versión se consulta por tenant y no se asume compartida: con
     * una base por municipio, un release puede quedar aplicado en unos y
     * no en otros, y eso tiene que verse.
     */
    @GetMapping
    List<MunicipioResponse> listar() {
        return repositorio.findAllByOrderBySlugAsc().stream()
                .map(this::describir)
                .toList();
    }


    /**
     * Aplica a cada municipio activo las migraciones que le falten, y
     * devuelve en qué versión quedó cada uno.
     *
     * <p>Con una base por municipio, un release nuevo no se aplica solo:
     * las bases existentes siguen en la versión anterior hasta que alguien
     * las migra. Un municipio que falla no interrumpe a los demás, y su
     * error aparece en el reporte en vez de quedar escondido.
     */
    @PostMapping("/migraciones")
    List<MigracionResponse> migrar() {
        return repositorio.findAllByOrderBySlugAsc().stream()
                .filter(tenant -> tenant.getEstado() == EstadoTenant.ACTIVO)
                .map(this::migrarUno)
                .toList();
    }

    private MigracionResponse migrarUno(TenantEntity tenant) {
        try {
            return new MigracionResponse(
                    tenant.getSlug(), migrador.migrar(tenant.getNombreBaseDatos()), null);
        } catch (RuntimeException e) {
            return new MigracionResponse(
                    tenant.getSlug(),
                    migrador.versionActual(tenant.getNombreBaseDatos()),
                    e.getMessage());
        }
    }

    /**
     * Crea un administrador en un municipio existente, para el caso en que
     * el municipio se quedó sin ninguno (ADR 0010).
     */
    @PostMapping("/{slug}/administrador")
    ResponseEntity<Void> agregarAdministrador(@PathVariable String slug,
            @RequestBody Administrador administrador) {

        alta.agregarAdministrador(slug, administrador);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Módulos contratados de un municipio, con el estado de cada uno del
     * catálogo (ADR 0012 §8). Cross-tenant y de plataforma, como el resto
     * de este controller: prender o apagar un módulo es una decisión
     * comercial del proveedor, no del municipio.
     */
    @GetMapping("/{slug}/modulos")
    ModulosDeMunicipioResponse modulos(@PathVariable String slug) {
        return describirModulos(administracionDeModulos.municipio(slug));
    }

    /**
     * Reemplaza la lista completa de módulos habilitados. Un código
     * desconocido rechaza la solicitud entera sin persistir nada; para
     * apagar todos los módulos hay que mandar la lista vacía de forma
     * explícita, {@code null} o ausente no cuenta como "apagar todo".
     */
    @PutMapping("/{slug}/modulos")
    ModulosDeMunicipioResponse actualizarModulos(@PathVariable String slug,
            @RequestBody(required = false) ModulosRequest request) {

        if (request == null || request.modulos() == null) {
            throw new SolicitudInvalida(
                    "Hay que indicar la lista de módulos; mandá una lista vacía para apagarlos "
                            + "todos.");
        }

        TenantEntity tenant = administracionDeModulos.reemplazarModulos(slug, request.modulos());
        return describirModulos(tenant);
    }

    /**
     * Edita el contrato mínimo de un municipio (ADR 0019): tramo
     * poblacional y estado de facturación, con nota libre. Igual que los
     * módulos, es una decisión comercial del proveedor, no del municipio.
     */
    @PatchMapping("/{slug}/comercial")
    MunicipioResponse actualizarComercial(@PathVariable String slug,
            @RequestBody ComercialRequest request) {
        TenantEntity tenant = informacionComercial.actualizar(
                slug, request.tramoPoblacional(), request.estadoFacturacion(),
                request.notaFacturacion());
        return describir(tenant);
    }

    /**
     * Solicitudes de alta/baja de módulo de un municipio, más reciente
     * primero (ADR 0022 §2). Cross-tenant y de plataforma, como el resto de
     * este controller: la solicitud vive en la base de control, así que no
     * hace falta pasar por ninguna interfaz pública para leerla —a
     * diferencia de {@code ConsolaDelMunicipioController}, que sí necesita
     * esa indirección porque corre en la base de tenant.
     */
    @GetMapping("/{slug}/solicitudes-de-modulo")
    List<SolicitudDeModuloAdminResponse> solicitudesDeModulo(@PathVariable String slug) {
        TenantEntity tenant = administracionDeModulos.municipio(slug);
        return solicitudDeModuloRepositorio.findByTenantIdOrderByCreadaEnDesc(tenant.getId()).stream()
                .map(SolicitudDeModuloAdminResponse::de)
                .toList();
    }

    /**
     * Marca una solicitud como atendida (ADR 0022 §3): deja constancia de
     * que la plataforma la vio y actuó por fuera del sistema, con el
     * mecanismo ya existente. Nunca prende ni apaga el módulo por sí sola.
     */
    @PatchMapping("/{slug}/solicitudes-de-modulo/{id}/atender")
    SolicitudDeModuloAdminResponse atenderSolicitudDeModulo(@PathVariable String slug, @PathVariable Long id) {
        TenantEntity tenant = administracionDeModulos.municipio(slug);
        SolicitudDeModuloEntity solicitud = solicitudDeModuloRepositorio.findByIdAndTenantId(id, tenant.getId())
                // Mismo código que "no existe el municipio" (SolicitudInvalida → 400): este
                // controller no tiene hoy ningún 404, la spec sugería 404 para "no existe la
                // solicitud" pero se prioriza la consistencia con lo que ya hace este archivo
                // por sobre el código HTTP exacto sugerido.
                .orElseThrow(() -> new SolicitudInvalida(
                        "No hay ninguna solicitud con el id " + id + " para el municipio " + slug + "."));

        solicitud.marcarAtendida();
        return SolicitudDeModuloAdminResponse.de(solicitudDeModuloRepositorio.save(solicitud));
    }

    private ModulosDeMunicipioResponse describirModulos(TenantEntity tenant) {
        return new ModulosDeMunicipioResponse(
                tenant.getSlug(), administracionDeModulos.describir(tenant));
    }

    private MunicipioResponse describir(TenantEntity tenant) {
        String version = tenant.getEstado() == EstadoTenant.ACTIVO
                ? migrador.versionActual(tenant.getNombreBaseDatos())
                : null;

        int cantidadDeModulosContratados = tenant.getConfig() == null
                ? 0
                : tenant.getConfig().modulosHabilitados().size();

        int cantidadDeSolicitudesPendientes = (int) solicitudDeModuloRepositorio.countByTenantIdAndEstado(
                tenant.getId(), EstadoDeSolicitudDeModulo.PENDIENTE);

        return new MunicipioResponse(
                tenant.getSlug(),
                tenant.getNombreMunicipio(),
                tenant.getSubdominio(),
                tenant.getEstado().name(),
                tenant.getNombreBaseDatos(),
                version,
                tenant.getTramoPoblacional().name(),
                tenant.getEstadoFacturacion().name(),
                tenant.getNotaFacturacion(),
                cantidadDeModulosContratados,
                cantidadDeSolicitudesPendientes);
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(AprovisionamientoFallido.class)
    ResponseEntity<ErrorResponse> aprovisionamientoFallido(AprovisionamientoFallido e) {
        return ResponseEntity.internalServerError().body(new ErrorResponse(e.getMessage()));
    }

    /** Atender una solicitud que ya estaba {@code ATENDIDA} (ADR 0022 §3). */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> solicitudDeModuloYaAtendida(IllegalStateException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    record AltaRequest(
            String slug,
            String nombreMunicipio,
            String direccion,
            String telefono,
            String email,
            Tema tema,
            Administrador administrador,
            List<String> modulos) {
    }

    record MunicipioResponse(
            String slug,
            String nombreMunicipio,
            String subdominio,
            String estado,
            String nombreBaseDatos,
            String versionDeEsquema,
            String tramoPoblacional,
            String estadoFacturacion,
            String notaFacturacion,
            int cantidadDeModulosContratados,
            int cantidadDeSolicitudesPendientes) {
    }

    record MigracionResponse(String slug, String versionDeEsquema, String error) {
    }

    record ModulosRequest(List<String> modulos) {
    }

    /**
     * Shape propio de este controller, distinto del
     * {@code SolicitudDeModuloResponse} de {@code municipio.internal}: son
     * módulos separados, cada uno con su propio ciclo de vida de contrato
     * de API (ADR 0022 §2).
     */
    record SolicitudDeModuloAdminResponse(
            Long id, String moduloCodigo, String tipo, String justificacion, String estado,
            Instant creadaEn, Instant atendidaEn) {

        static SolicitudDeModuloAdminResponse de(SolicitudDeModuloEntity solicitud) {
            return new SolicitudDeModuloAdminResponse(
                    solicitud.getId(),
                    solicitud.getModuloCodigo(),
                    solicitud.getTipo().name(),
                    solicitud.getJustificacion(),
                    solicitud.getEstado().name(),
                    instanteDe(solicitud.getCreadaEn()),
                    instanteDe(solicitud.getAtendidaEn()));
        }

        private static Instant instanteDe(OffsetDateTime fecha) {
            return fecha == null ? null : fecha.toInstant();
        }
    }

    record ComercialRequest(String tramoPoblacional, String estadoFacturacion, String notaFacturacion) {
    }

    record ModulosDeMunicipioResponse(String slug, List<ModuloDeMunicipio> modulos) {
    }

    record ErrorResponse(String error) {
    }
}
