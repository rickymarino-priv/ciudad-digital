package ar.com.ciudaddigital.tenants.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.tenants.internal.AltaDeMunicipio.Administrador;
import ar.com.ciudaddigital.tenants.internal.AltaDeMunicipio.SolicitudDeAlta;
import ar.com.ciudaddigital.tenants.internal.AltaDeMunicipio.SolicitudInvalida;
import ar.com.ciudaddigital.tenants.internal.TenantConfig.Tema;

/**
 * Alta y estado de los municipios (ADR 0005).
 *
 * <p>Superficie cross-tenant: es la única parte del sistema que ve a todos
 * los municipios a la vez. Está protegida provisoriamente por
 * {@link AdminTokenFilter} hasta que R3 traiga autenticación real.
 */
@RestController
@RequestMapping("/api/admin/municipios")
class AdministracionDeMunicipiosController {

    private final AltaDeMunicipio alta;
    private final TenantRepository repositorio;
    private final MigradorDeTenant migrador;

    AdministracionDeMunicipiosController(AltaDeMunicipio alta, TenantRepository repositorio,
            MigradorDeTenant migrador) {
        this.alta = alta;
        this.repositorio = repositorio;
        this.migrador = migrador;
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
                request.administrador()));

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

    private MunicipioResponse describir(TenantEntity tenant) {
        String version = tenant.getEstado() == EstadoTenant.ACTIVO
                ? migrador.versionActual(tenant.getNombreBaseDatos())
                : null;

        return new MunicipioResponse(
                tenant.getSlug(),
                tenant.getNombreMunicipio(),
                tenant.getSubdominio(),
                tenant.getEstado().name(),
                tenant.getNombreBaseDatos(),
                version);
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(AprovisionamientoFallido.class)
    ResponseEntity<ErrorResponse> aprovisionamientoFallido(AprovisionamientoFallido e) {
        return ResponseEntity.internalServerError().body(new ErrorResponse(e.getMessage()));
    }

    record AltaRequest(
            String slug,
            String nombreMunicipio,
            String direccion,
            String telefono,
            String email,
            Tema tema,
            Administrador administrador) {
    }

    record MunicipioResponse(
            String slug,
            String nombreMunicipio,
            String subdominio,
            String estado,
            String nombreBaseDatos,
            String versionDeEsquema) {
    }

    record MigracionResponse(String slug, String versionDeEsquema, String error) {
    }

    record ErrorResponse(String error) {
    }
}
