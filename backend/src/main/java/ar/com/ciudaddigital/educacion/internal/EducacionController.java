package ar.com.ciudaddigital.educacion.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.acceso.ActorAutenticado;

/**
 * Alta protegida, lectura pública y actualización de estado de
 * instituciones educativas municipales (ADR 0028).
 *
 * <p>El listado no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloEducacion} declara como
 * {@code rutasDeLecturaPublica()}, protegida solo por el gating de
 * entitlement (ADR 0012 §1). Registrar y actualizar el estado sí
 * requieren sesión y el permiso {@code educacion.gestionar}.
 */
@RestController
@RequestMapping("/api/educacion")
class EducacionController {

    private final GestionDeEducacion gestion;

    EducacionController(GestionDeEducacion gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('educacion.gestionar')")
    ResponseEntity<InstitucionEducativaResponse> registrar(
            @RequestBody RegistrarInstitucionRequest request, Authentication autenticacion) {

        TipoDeInstitucionEducativa tipo = tipoDe(request.tipo());
        ActorAutenticado actor = actorDe(autenticacion);
        InstitucionEducativaEntity institucion = gestion.registrar(
                request.nombre(), tipo, request.ubicacion(), request.descripcion(),
                actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(InstitucionEducativaResponse.de(institucion));
    }

    @GetMapping
    List<InstitucionEducativaResponse> buscar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String q) {

        EstadoDeInstitucion estadoDeInstitucion = estado == null || estado.isBlank() ? null : estadoDe(estado);
        TipoDeInstitucionEducativa tipoDeInstitucion = tipo == null || tipo.isBlank() ? null : tipoDe(tipo);
        return gestion.buscar(estadoDeInstitucion, tipoDeInstitucion, q).stream()
                .map(InstitucionEducativaResponse::de).toList();
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('educacion.gestionar')")
    InstitucionEducativaResponse actualizarEstado(@PathVariable Long id, @RequestBody ActualizarEstadoRequest request) {
        EstadoDeInstitucion estadoNuevo = estadoDe(request.estadoNuevo());
        InstitucionEducativaEntity institucion = gestion.actualizarEstado(id, estadoNuevo);
        return InstitucionEducativaResponse.de(institucion);
    }

    private static ActorAutenticado actorDe(Authentication autenticacion) {
        if (autenticacion.getPrincipal() instanceof ActorAutenticado actor) {
            return actor;
        }
        // No debería pasar: el permiso ya exige sesión de acceso, así que el
        // principal siempre es un ActorAutenticado. Si no lo es, es un
        // problema del mecanismo de autenticación, no una solicitud
        // inválida del agente (mismo criterio que ObrasController#actorDe).
        throw new IllegalStateException("No hay un actor autenticado para firmar la operación.");
    }

    private static TipoDeInstitucionEducativa tipoDe(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un tipo de institución.");
        }
        try {
            return TipoDeInstitucionEducativa.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El tipo de institución '" + tipo + "' no existe.");
        }
    }

    private static EstadoDeInstitucion estadoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoDeInstitucion.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /** Un id de institución que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(InstitucionEducativaNoEncontrada.class)
    ResponseEntity<ErrorResponse> institucionNoEncontrada(InstitucionEducativaNoEncontrada e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No encontramos esa institución."));
    }

    record RegistrarInstitucionRequest(String nombre, String tipo, String ubicacion, String descripcion) {
    }

    record ActualizarEstadoRequest(String estadoNuevo) {
    }

    record InstitucionEducativaResponse(
            Long id,
            String nombre,
            String tipo,
            String ubicacion,
            String descripcion,
            String estado,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn,
            Instant actualizadoEn) {

        static InstitucionEducativaResponse de(InstitucionEducativaEntity institucion) {
            return new InstitucionEducativaResponse(
                    institucion.getId(),
                    institucion.getNombre(),
                    institucion.getTipo().name(),
                    institucion.getUbicacion(),
                    institucion.getDescripcion(),
                    institucion.getEstado().name(),
                    institucion.getPublicadoPorNombre(),
                    institucion.getPublicadoPorEmail(),
                    institucion.getCreadoEn(),
                    institucion.getActualizadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
