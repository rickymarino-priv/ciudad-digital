package ar.com.ciudaddigital.espaciosverdes.internal;

import java.math.BigDecimal;
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
 * Alta protegida, lectura pública y actualización de estado de espacios
 * verdes (ADR 0029).
 *
 * <p>El listado no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloEspaciosVerdes} declara como
 * {@code rutasDeLecturaPublica()}, protegida solo por el gating de
 * entitlement (ADR 0012 §1). Registrar y actualizar el estado sí
 * requieren sesión y el permiso {@code espaciosverdes.gestionar}.
 */
@RestController
@RequestMapping("/api/espaciosverdes")
class EspaciosVerdesController {

    private final GestionDeEspaciosVerdes gestion;

    EspaciosVerdesController(GestionDeEspaciosVerdes gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('espaciosverdes.gestionar')")
    ResponseEntity<EspacioVerdeResponse> registrar(
            @RequestBody RegistrarEspacioVerdeRequest request, Authentication autenticacion) {

        TipoDeEspacioVerde tipo = tipoDe(request.tipo());
        ActorAutenticado actor = actorDe(autenticacion);
        EspacioVerdeEntity espacioVerde = gestion.registrar(
                request.nombre(), tipo, request.ubicacion(), request.descripcion(), request.superficie(),
                actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(EspacioVerdeResponse.de(espacioVerde));
    }

    @GetMapping
    List<EspacioVerdeResponse> buscar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String q) {

        EstadoDeEspacioVerde estadoDeEspacioVerde = estado == null || estado.isBlank() ? null : estadoDe(estado);
        TipoDeEspacioVerde tipoDeEspacioVerde = tipo == null || tipo.isBlank() ? null : tipoDe(tipo);
        return gestion.buscar(estadoDeEspacioVerde, tipoDeEspacioVerde, q).stream()
                .map(EspacioVerdeResponse::de).toList();
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('espaciosverdes.gestionar')")
    EspacioVerdeResponse actualizarEstado(@PathVariable Long id, @RequestBody ActualizarEstadoRequest request) {
        EstadoDeEspacioVerde estadoNuevo = estadoDe(request.estadoNuevo());
        EspacioVerdeEntity espacioVerde = gestion.actualizarEstado(id, estadoNuevo);
        return EspacioVerdeResponse.de(espacioVerde);
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

    private static TipoDeEspacioVerde tipoDe(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un tipo de espacio verde.");
        }
        try {
            return TipoDeEspacioVerde.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El tipo de espacio verde '" + tipo + "' no existe.");
        }
    }

    private static EstadoDeEspacioVerde estadoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoDeEspacioVerde.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /** Un id de espacio verde que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(EspacioVerdeNoEncontrado.class)
    ResponseEntity<ErrorResponse> espacioVerdeNoEncontrado(EspacioVerdeNoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No encontramos ese espacio verde."));
    }

    record RegistrarEspacioVerdeRequest(
            String nombre, String tipo, String ubicacion, String descripcion, BigDecimal superficie) {
    }

    record ActualizarEstadoRequest(String estadoNuevo) {
    }

    record EspacioVerdeResponse(
            Long id,
            String nombre,
            String tipo,
            String ubicacion,
            String descripcion,
            BigDecimal superficie,
            String estado,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn,
            Instant actualizadoEn) {

        static EspacioVerdeResponse de(EspacioVerdeEntity espacioVerde) {
            return new EspacioVerdeResponse(
                    espacioVerde.getId(),
                    espacioVerde.getNombre(),
                    espacioVerde.getTipo().name(),
                    espacioVerde.getUbicacion(),
                    espacioVerde.getDescripcion(),
                    espacioVerde.getSuperficie(),
                    espacioVerde.getEstado().name(),
                    espacioVerde.getPublicadoPorNombre(),
                    espacioVerde.getPublicadoPorEmail(),
                    espacioVerde.getCreadoEn(),
                    espacioVerde.getActualizadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
