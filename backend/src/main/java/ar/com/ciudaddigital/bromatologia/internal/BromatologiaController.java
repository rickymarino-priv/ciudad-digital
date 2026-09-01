package ar.com.ciudaddigital.bromatologia.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.acceso.ActorAutenticado;

/**
 * Alta protegida y lectura pública del padrón de comercios, y alta y
 * lectura protegidas del historial de inspecciones (ADR 0032).
 *
 * <p>{@code GET /comercios} es la única ruta que
 * {@code DescriptorDelModuloBromatologia} declara como
 * {@code rutasDeLecturaPublica()} (ADR 0012 §1). Todo lo demás —alta de
 * comercio, alta de inspección y, a propósito, también la
 * <strong>lectura</strong> del historial de inspecciones— requiere sesión
 * y el único permiso {@code bromatologia.gestionar} (ADR 0032 §4/§5): a
 * diferencia de todos los módulos anteriores con estado público, acá el
 * historial que motiva ese estado no tiene ninguna vía de lectura sin
 * sesión.
 */
@RestController
@RequestMapping("/api/bromatologia")
class BromatologiaController {

    private final GestionDeBromatologia gestion;

    BromatologiaController(GestionDeBromatologia gestion) {
        this.gestion = gestion;
    }

    @PostMapping("/comercios")
    @PreAuthorize("hasAuthority('bromatologia.gestionar')")
    ResponseEntity<ComercioResponse> registrarComercio(
            @RequestBody RegistrarComercioRequest request, Authentication autenticacion) {

        RubroBromatologico rubro = rubroDe(request.rubro());
        ActorAutenticado actor = actorDe(autenticacion);
        ComercioBromatologicoEntity comercio = gestion.registrarComercio(
                request.nombre(), rubro, request.direccion(),
                request.fechaHabilitacion(), request.fechaVencimientoHabilitacion(),
                actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ComercioResponse.de(comercio));
    }

    @GetMapping("/comercios")
    List<ComercioResponse> buscarComercios(
            @RequestParam(required = false) String rubro,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {

        RubroBromatologico rubroBromatologico = rubro == null || rubro.isBlank() ? null : rubroDe(rubro);
        EstadoBromatologico estadoBromatologico = estado == null || estado.isBlank() ? null : estadoDe(estado);
        return gestion.buscarComercios(rubroBromatologico, estadoBromatologico, q)
                .stream().map(ComercioResponse::de).toList();
    }

    @PostMapping("/comercios/{id}/inspecciones")
    @PreAuthorize("hasAuthority('bromatologia.gestionar')")
    ResponseEntity<InspeccionResponse> registrarInspeccion(
            @PathVariable Long id, @RequestBody RegistrarInspeccionRequest request, Authentication autenticacion) {

        EstadoBromatologico resultado = estadoDe(request.resultado());
        ActorAutenticado actor = actorDe(autenticacion);
        InspeccionBromatologicaEntity inspeccion = gestion.registrarInspeccion(
                id, request.fecha(), resultado, request.observaciones(), actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(InspeccionResponse.de(inspeccion));
    }

    @GetMapping("/comercios/{id}/inspecciones")
    @PreAuthorize("hasAuthority('bromatologia.gestionar')")
    List<InspeccionResponse> buscarInspecciones(@PathVariable Long id) {
        return gestion.buscarInspecciones(id).stream().map(InspeccionResponse::de).toList();
    }

    private static ActorAutenticado actorDe(Authentication autenticacion) {
        if (autenticacion.getPrincipal() instanceof ActorAutenticado actor) {
            return actor;
        }
        // No debería pasar: el permiso ya exige sesión de acceso, así que el
        // principal siempre es un ActorAutenticado. Si no lo es, es un
        // problema del mecanismo de autenticación, no una solicitud
        // inválida del agente (mismo criterio que DefensaCivilController#actorDe).
        throw new IllegalStateException("No hay un actor autenticado para firmar la operación.");
    }

    private static RubroBromatologico rubroDe(String rubro) {
        if (rubro == null || rubro.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un rubro.");
        }
        try {
            return RubroBromatologico.valueOf(rubro);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El rubro '" + rubro + "' no existe.");
        }
    }

    private static EstadoBromatologico estadoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoBromatologico.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /** Un id de comercio que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(ComercioNoEncontrado.class)
    ResponseEntity<ErrorResponse> comercioNoEncontrado(ComercioNoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("No encontramos ese comercio."));
    }

    record RegistrarComercioRequest(
            String nombre, String rubro, String direccion,
            LocalDate fechaHabilitacion, LocalDate fechaVencimientoHabilitacion) {
    }

    record RegistrarInspeccionRequest(LocalDate fecha, String resultado, String observaciones) {
    }

    /** Sin ningún campo de inspección (ADR 0032 §2): el padrón público no anticipa el historial protegido. */
    record ComercioResponse(
            Long id,
            String nombre,
            String rubro,
            String direccion,
            String estado,
            LocalDate fechaHabilitacion,
            LocalDate fechaVencimientoHabilitacion,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn,
            Instant actualizadoEn) {

        static ComercioResponse de(ComercioBromatologicoEntity comercio) {
            return new ComercioResponse(
                    comercio.getId(),
                    comercio.getNombre(),
                    comercio.getRubro().name(),
                    comercio.getDireccion(),
                    comercio.getEstado().name(),
                    comercio.getFechaHabilitacion(),
                    comercio.getFechaVencimientoHabilitacion(),
                    comercio.getPublicadoPorNombre(),
                    comercio.getPublicadoPorEmail(),
                    comercio.getCreadoEn(),
                    comercio.getActualizadoEn());
        }
    }

    /** Incluye {@code comercioId}: a diferencia del comercio, la inspección siempre se lee en su contexto. */
    record InspeccionResponse(
            Long id,
            Long comercioId,
            LocalDate fecha,
            String resultado,
            String observaciones,
            String inspeccionadoPorNombre,
            String inspeccionadoPorEmail,
            Instant creadoEn) {

        static InspeccionResponse de(InspeccionBromatologicaEntity inspeccion) {
            return new InspeccionResponse(
                    inspeccion.getId(),
                    inspeccion.getComercioId(),
                    inspeccion.getFecha(),
                    inspeccion.getResultado().name(),
                    inspeccion.getObservaciones(),
                    inspeccion.getInspeccionadoPorNombre(),
                    inspeccion.getInspeccionadoPorEmail(),
                    inspeccion.getCreadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
