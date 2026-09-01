package ar.com.ciudaddigital.eventos.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * Alta protegida, lectura pública y cancelación de eventos de la agenda
 * (ADR 0030).
 *
 * <p>El listado no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloEventos} declara como
 * {@code rutasDeLecturaPublica()}, protegida solo por el gating de
 * entitlement (ADR 0012 §1). Publicar y cancelar sí requieren sesión y el
 * permiso {@code eventos.gestionar}.
 */
@RestController
@RequestMapping("/api/eventos")
class EventosController {

    private final GestionDeEventos gestion;

    EventosController(GestionDeEventos gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('eventos.gestionar')")
    ResponseEntity<EventoResponse> publicar(@RequestBody PublicarEventoRequest request, Authentication autenticacion) {
        CategoriaDeEvento categoria = categoriaDe(request.categoria());
        ActorAutenticado actor = actorDe(autenticacion);
        EventoEntity evento = gestion.publicar(
                request.nombre(), categoria, request.ubicacion(), request.descripcion(),
                request.fechaInicio(), request.fechaFin(), request.horaInicio(),
                actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(EventoResponse.de(evento));
    }

    @GetMapping
    List<EventoResponse> buscar(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {

        CategoriaDeEvento categoriaDeEvento = categoria == null || categoria.isBlank() ? null : categoriaDe(categoria);
        EstadoDeEvento estadoDeEvento = estado == null || estado.isBlank() ? null : estadoDe(estado);
        return gestion.buscar(categoriaDeEvento, estadoDeEvento, q).stream().map(EventoResponse::de).toList();
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('eventos.gestionar')")
    EventoResponse actualizarEstado(@PathVariable Long id, @RequestBody ActualizarEstadoRequest request) {
        EstadoDeEvento estadoNuevo = estadoDe(request.estadoNuevo());
        // La única transición válida es PROGRAMADO → CANCELADO (ADR 0030 §3):
        // se valida acá que el pedido sea exactamente ese destino antes de
        // llamar a GestionDeEventos#cancelar, que ya no recibe el estado
        // nuevo como parámetro porque no hay otro destino posible.
        if (estadoNuevo != EstadoDeEvento.CANCELADO) {
            throw new SolicitudInvalida("No se puede pasar a " + estadoNuevo + ".");
        }
        EventoEntity evento = gestion.cancelar(id);
        return EventoResponse.de(evento);
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

    private static CategoriaDeEvento categoriaDe(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar una categoría de evento.");
        }
        try {
            return CategoriaDeEvento.valueOf(categoria);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("La categoría de evento '" + categoria + "' no existe.");
        }
    }

    private static EstadoDeEvento estadoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoDeEvento.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /** Un id de evento que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(EventoNoEncontrado.class)
    ResponseEntity<ErrorResponse> eventoNoEncontrado(EventoNoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("No encontramos ese evento."));
    }

    record PublicarEventoRequest(
            String nombre,
            String categoria,
            String ubicacion,
            String descripcion,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            LocalTime horaInicio) {
    }

    record ActualizarEstadoRequest(String estadoNuevo) {
    }

    record EventoResponse(
            Long id,
            String nombre,
            String categoria,
            String ubicacion,
            String descripcion,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            LocalTime horaInicio,
            String estado,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn,
            Instant actualizadoEn) {

        static EventoResponse de(EventoEntity evento) {
            return new EventoResponse(
                    evento.getId(),
                    evento.getNombre(),
                    evento.getCategoria().name(),
                    evento.getUbicacion(),
                    evento.getDescripcion(),
                    evento.getFechaInicio(),
                    evento.getFechaFin(),
                    evento.getHoraInicio(),
                    evento.getEstado().name(),
                    evento.getPublicadoPorNombre(),
                    evento.getPublicadoPorEmail(),
                    evento.getCreadoEn(),
                    evento.getActualizadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
