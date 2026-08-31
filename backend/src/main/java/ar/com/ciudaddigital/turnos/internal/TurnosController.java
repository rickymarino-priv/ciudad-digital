package ar.com.ciudaddigital.turnos.internal;

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
import ar.com.ciudaddigital.turnos.internal.GestionDeReservas.ReservaPublicaResponse;

/**
 * Catálogo protegido/público de actividades municipales y sus franjas
 * horarias, reserva pública anónima de turnos, y agenda de gestión de
 * reservas (ADR 0026).
 *
 * <p>Publicar una actividad, crear una franja, cambiar el estado de una
 * actividad y listar las reservas de una franja requieren sesión y el
 * permiso {@code turnos.gestionar}. El listado de actividades y el de
 * franjas no llevan {@code @PreAuthorize}: son las rutas que
 * {@code DescriptorDelModuloTurnos} declara como
 * {@code rutasDeLecturaPublica()} (ADR 0012 §1). El alta de reservas
 * tampoco lleva {@code @PreAuthorize}: es la ruta que se declara como
 * {@code rutasDeEscrituraPublica()} (ADR 0026 §4).
 */
@RestController
@RequestMapping("/api/turnos")
class TurnosController {

    private final GestionDeAgenda gestionDeAgenda;
    private final GestionDeReservas gestionDeReservas;

    TurnosController(GestionDeAgenda gestionDeAgenda, GestionDeReservas gestionDeReservas) {
        this.gestionDeAgenda = gestionDeAgenda;
        this.gestionDeReservas = gestionDeReservas;
    }

    @PostMapping("/actividades")
    @PreAuthorize("hasAuthority('turnos.gestionar')")
    ResponseEntity<ActividadResponse> publicarActividad(
            @RequestBody PublicarActividadRequest request, Authentication autenticacion) {

        TipoDeActividad tipo = tipoDe(request.tipo());
        ActorAutenticado actor = actorDe(autenticacion);
        ActividadEntity actividad = gestionDeAgenda.publicarActividad(
                request.nombre(), tipo, request.descripcion(), request.ubicacion(), actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ActividadResponse.de(actividad));
    }

    @GetMapping("/actividades")
    List<ActividadResponse> buscarActividades(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {

        TipoDeActividad tipoDeActividad = tipo == null || tipo.isBlank() ? null : tipoDe(tipo);
        EstadoDeActividad estadoDeActividad = estado == null || estado.isBlank() ? null : estadoDe(estado);
        return gestionDeAgenda.buscarActividades(tipoDeActividad, estadoDeActividad, q).stream()
                .map(ActividadResponse::de).toList();
    }

    @PatchMapping("/actividades/{id}/estado")
    @PreAuthorize("hasAuthority('turnos.gestionar')")
    ActividadResponse cambiarEstadoDeActividad(@PathVariable Long id, @RequestBody ActualizarEstadoRequest request) {
        EstadoDeActividad estadoNuevo = estadoDe(request.estadoNuevo());
        ActividadEntity actividad = gestionDeAgenda.cambiarEstadoDeActividad(id, estadoNuevo);
        return ActividadResponse.de(actividad);
    }

    @PostMapping("/actividades/{id}/franjas")
    @PreAuthorize("hasAuthority('turnos.gestionar')")
    ResponseEntity<FranjaHorariaResponse> crearFranja(@PathVariable Long id, @RequestBody CrearFranjaRequest request) {
        FranjaHorariaEntity franja = gestionDeAgenda.crearFranja(
                id, request.fecha(), request.horaInicio(), request.horaFin(), request.cupoTotal());
        return ResponseEntity.status(HttpStatus.CREATED).body(FranjaHorariaResponse.de(franja));
    }

    @GetMapping("/franjas")
    List<FranjaHorariaResponse> buscarFranjas(@RequestParam(required = true) Long actividadId) {
        return gestionDeAgenda.buscarFranjas(actividadId).stream().map(FranjaHorariaResponse::de).toList();
    }

    @PostMapping("/reservas")
    ResponseEntity<ReservaPublicaResponse> reservar(@RequestBody ReservarTurnoRequest request) {
        ReservaPublicaResponse confirmacion = gestionDeReservas.reservar(
                request.franjaId(), request.nombreSolicitante(), request.dniSolicitante(), request.contacto());
        return ResponseEntity.status(HttpStatus.CREATED).body(confirmacion);
    }

    @GetMapping("/reservas")
    @PreAuthorize("hasAuthority('turnos.gestionar')")
    List<TurnoResponse> listarReservasParaGestion(@RequestParam(required = true) Long franjaId) {
        return gestionDeReservas.listarParaGestion(franjaId).stream().map(TurnoResponse::de).toList();
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

    private static TipoDeActividad tipoDe(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un tipo de actividad.");
        }
        try {
            return TipoDeActividad.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El tipo de actividad '" + tipo + "' no existe.");
        }
    }

    private static EstadoDeActividad estadoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoDeActividad.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /** Un id de actividad que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(ActividadNoEncontrada.class)
    ResponseEntity<ErrorResponse> actividadNoEncontrada(ActividadNoEncontrada e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("No encontramos esa actividad."));
    }

    /** Un id de franja que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(FranjaNoEncontrada.class)
    ResponseEntity<ErrorResponse> franjaNoEncontrada(FranjaNoEncontrada e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("No encontramos esa franja."));
    }

    /**
     * Primer uso de 409 Conflict del proyecto (ADR 0026 §7): el cupo se
     * agotó entre que el vecino vio el catálogo y confirmó la reserva, no
     * una solicitud malformada ni un recurso inexistente.
     */
    @ExceptionHandler(CupoAgotado.class)
    ResponseEntity<ErrorResponse> cupoAgotado(CupoAgotado e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("No queda cupo disponible para esta franja."));
    }

    /** Segundo uso de 409 Conflict del proyecto (ADR 0026 §7): ese DNI ya tiene un lugar en esta franja. */
    @ExceptionHandler(ReservaDuplicada.class)
    ResponseEntity<ErrorResponse> reservaDuplicada(ReservaDuplicada e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Ya existe una reserva con ese DNI para esta franja."));
    }

    record PublicarActividadRequest(String nombre, String tipo, String descripcion, String ubicacion) {
    }

    record ActualizarEstadoRequest(String estadoNuevo) {
    }

    record CrearFranjaRequest(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, Integer cupoTotal) {
    }

    record ReservarTurnoRequest(Long franjaId, String nombreSolicitante, String dniSolicitante, String contacto) {
    }

    record ActividadResponse(
            Long id,
            String nombre,
            String tipo,
            String descripcion,
            String ubicacion,
            String estado,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn,
            Instant actualizadoEn) {

        static ActividadResponse de(ActividadEntity actividad) {
            return new ActividadResponse(
                    actividad.getId(),
                    actividad.getNombre(),
                    actividad.getTipo().name(),
                    actividad.getDescripcion(),
                    actividad.getUbicacion(),
                    actividad.getEstado().name(),
                    actividad.getPublicadoPorNombre(),
                    actividad.getPublicadoPorEmail(),
                    actividad.getCreadoEn(),
                    actividad.getActualizadoEn());
        }
    }

    record FranjaHorariaResponse(
            Long id,
            Long actividadId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin,
            Integer cupoTotal,
            Integer cupoDisponible,
            Instant creadoEn) {

        static FranjaHorariaResponse de(FranjaHorariaEntity franja) {
            return new FranjaHorariaResponse(
                    franja.getId(),
                    franja.getActividadId(),
                    franja.getFecha(),
                    franja.getHoraInicio(),
                    franja.getHoraFin(),
                    franja.getCupoTotal(),
                    franja.getCupoDisponible(),
                    franja.getCreadoEn());
        }
    }

    /**
     * Shape completo, con los datos personales del solicitante: la única
     * vista que los expone, y ya está detrás de {@code turnos.gestionar}
     * (ADR 0026 §5).
     */
    record TurnoResponse(
            Long id, Long franjaId, String nombreSolicitante, String dniSolicitante, String contacto,
            Instant creadoEn) {

        static TurnoResponse de(TurnoEntity turno) {
            return new TurnoResponse(
                    turno.getId(), turno.getFranjaId(), turno.getNombreSolicitante(), turno.getDniSolicitante(),
                    turno.getContacto(), turno.getCreadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
