package ar.com.ciudaddigital.arbolado.internal;

import java.time.Instant;
import java.time.LocalDate;
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
 * Alta protegida, lectura pública y actualización de estado sanitario de
 * árboles urbanos (ADR 0024).
 *
 * <p>El listado no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloArbolado} declara como
 * {@code rutasDeLecturaPublica()}, protegida solo por el gating de
 * entitlement (ADR 0012 §1). Registrar y actualizar el estado sí
 * requieren sesión y el permiso {@code arbolado.gestionar}.
 */
@RestController
@RequestMapping("/api/arbolado")
class ArboladoController {

    private final GestionDeArbolado gestion;

    ArboladoController(GestionDeArbolado gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('arbolado.gestionar')")
    ResponseEntity<ArbolUrbanoResponse> registrar(
            @RequestBody RegistrarArbolRequest request, Authentication autenticacion) {

        ActorAutenticado actor = actorDe(autenticacion);
        ArbolUrbanoEntity arbol = gestion.registrar(
                request.especie(), request.ubicacion(), request.descripcion(), request.fechaDePlantacion(),
                actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ArbolUrbanoResponse.de(arbol));
    }

    @GetMapping
    List<ArbolUrbanoResponse> buscar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {

        EstadoDeArbol estadoDeArbol = estado == null || estado.isBlank() ? null : estadoDe(estado);
        return gestion.buscar(estadoDeArbol, q).stream().map(ArbolUrbanoResponse::de).toList();
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('arbolado.gestionar')")
    ArbolUrbanoResponse actualizarEstado(@PathVariable Long id, @RequestBody ActualizarEstadoRequest request) {
        EstadoDeArbol estadoNuevo = estadoDe(request.estadoNuevo());
        ArbolUrbanoEntity arbol = gestion.actualizarEstado(id, estadoNuevo);
        return ArbolUrbanoResponse.de(arbol);
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

    private static EstadoDeArbol estadoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoDeArbol.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /** Un id de árbol que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(ArbolNoEncontrado.class)
    ResponseEntity<ErrorResponse> arbolNoEncontrado(ArbolNoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("No encontramos ese árbol."));
    }

    record RegistrarArbolRequest(
            String especie, String ubicacion, String descripcion, LocalDate fechaDePlantacion) {
    }

    record ActualizarEstadoRequest(String estadoNuevo) {
    }

    record ArbolUrbanoResponse(
            Long id,
            String especie,
            String ubicacion,
            String descripcion,
            String estado,
            LocalDate fechaDePlantacion,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn,
            Instant actualizadoEn) {

        static ArbolUrbanoResponse de(ArbolUrbanoEntity arbol) {
            return new ArbolUrbanoResponse(
                    arbol.getId(),
                    arbol.getEspecie(),
                    arbol.getUbicacion(),
                    arbol.getDescripcion(),
                    arbol.getEstado().name(),
                    arbol.getFechaDePlantacion(),
                    arbol.getPublicadoPorNombre(),
                    arbol.getPublicadoPorEmail(),
                    arbol.getCreadoEn(),
                    arbol.getActualizadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
