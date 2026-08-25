package ar.com.ciudaddigital.mesaentradas.internal;

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
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.acceso.ActorAutenticado;

/**
 * Alta pública de trámites de Mesa de Entradas y su gestión por el
 * municipio (ADR 0015).
 *
 * <p>El alta no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloMesaDeEntradas} declara como
 * {@code rutasDeEscrituraPublica()}, protegida solo por el gating de
 * entitlement y el {@code permitAll()} de {@code POST} que arma la cadena
 * de seguridad a partir de esa declaración — mismo mecanismo que
 * {@code ReclamosController} (ADR 0014 §1), reutilizado tal cual por
 * ADR 0015 §4. Listar y avanzar el estado sí requieren sesión y permiso.
 */
@RestController
@RequestMapping("/api/mesaentradas")
class MesaDeEntradasController {

    private final GestionDeExpedientes gestion;

    MesaDeEntradasController(GestionDeExpedientes gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    ResponseEntity<ExpedientePublicoResponse> iniciar(@RequestBody IniciarExpedienteRequest request) {
        TipoDeTramite tipo = tipoDe(request.tipo());
        DatosPropiosDelTramite datos = new DatosPropiosDelTramite(
                request.domicilioACertificar(), request.rubroComercial(), request.direccionLocal(),
                request.direccionObra(), request.descripcionObra());
        ExpedienteEntity expediente =
                gestion.iniciar(tipo, request.solicitanteNombre(), request.solicitanteContacto(), datos);
        return ResponseEntity.status(HttpStatus.CREATED).body(ExpedientePublicoResponse.de(expediente));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('mesaentradas.ver')")
    List<ExpedienteResponse> listar() {
        return gestion.listar().stream().map(ExpedienteResponse::de).toList();
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('mesaentradas.gestionar')")
    ExpedienteResponse avanzarEstado(
            @PathVariable Long id, @RequestBody AvanzarEstadoRequest request, Authentication autenticacion) {

        String nombre;
        String email;
        if (autenticacion.getPrincipal() instanceof ActorAutenticado actor) {
            nombre = actor.nombre();
            email = actor.email();
        } else {
            // No debería pasar: el permiso ya exige sesión de acceso, así
            // que el principal siempre es un ActorAutenticado. Si no lo es,
            // es un problema del mecanismo de autenticación, no una
            // solicitud inválida del vecino.
            throw new IllegalStateException("No hay un actor autenticado para avanzar el expediente.");
        }

        EstadoDeExpediente nuevoEstado = estadoDe(request.estado());
        ExpedienteEntity expediente = gestion.avanzar(id, nuevoEstado, request.comentario(), nombre, email);
        return ExpedienteResponse.de(expediente);
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    private static TipoDeTramite tipoDe(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un tipo de trámite.");
        }
        try {
            return TipoDeTramite.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El tipo de trámite '" + tipo + "' no existe.");
        }
    }

    private static EstadoDeExpediente estadoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un estado.");
        }
        try {
            return EstadoDeExpediente.valueOf(estado);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalida("El estado '" + estado + "' no existe.");
        }
    }

    record IniciarExpedienteRequest(
            String tipo,
            String solicitanteNombre,
            String solicitanteContacto,
            String domicilioACertificar,
            String rubroComercial,
            String direccionLocal,
            String direccionObra,
            String descripcionObra) {
    }

    record AvanzarEstadoRequest(String estado, String comentario) {
    }

    /**
     * Confirmación al vecino que inició el trámite: deliberadamente sin
     * {@code solicitanteContacto}/{@code domicilioACertificar} — mismo
     * criterio que {@code ReclamoPublicoResponse}, no es una vista de
     * gestión.
     */
    record ExpedientePublicoResponse(Long id, String tipo, String estado, Instant creadoEn) {

        static ExpedientePublicoResponse de(ExpedienteEntity expediente) {
            return new ExpedientePublicoResponse(
                    expediente.getId(), expediente.getTipo().name(), expediente.getEstado().name(),
                    expediente.getCreadoEn());
        }
    }

    record ExpedienteResponse(
            Long id,
            String tipo,
            String estado,
            String solicitanteNombre,
            String solicitanteContacto,
            String domicilioACertificar,
            String rubroComercial,
            String direccionLocal,
            String direccionObra,
            String descripcionObra,
            Instant creadoEn,
            Instant actualizadoEn,
            List<MovimientoResponse> movimientos) {

        static ExpedienteResponse de(ExpedienteEntity expediente) {
            return new ExpedienteResponse(
                    expediente.getId(),
                    expediente.getTipo().name(),
                    expediente.getEstado().name(),
                    expediente.getSolicitanteNombre(),
                    expediente.getSolicitanteContacto(),
                    expediente.getDomicilioACertificar(),
                    expediente.getRubroComercial(),
                    expediente.getDireccionLocal(),
                    expediente.getDireccionObra(),
                    expediente.getDescripcionObra(),
                    expediente.getCreadoEn(),
                    expediente.getActualizadoEn(),
                    expediente.getMovimientos().stream().map(MovimientoResponse::de).toList());
        }
    }

    record MovimientoResponse(
            String estadoAnterior, String estadoNuevo, String actorNombre, String actorEmail, String comentario,
            Instant fecha) {

        static MovimientoResponse de(MovimientoDeExpedienteEntity movimiento) {
            return new MovimientoResponse(
                    movimiento.getEstadoAnterior() == null ? null : movimiento.getEstadoAnterior().name(),
                    movimiento.getEstadoNuevo().name(),
                    movimiento.getActorNombre(),
                    movimiento.getActorEmail(),
                    movimiento.getComentario(),
                    movimiento.getFecha());
        }
    }

    record ErrorResponse(String error) {
    }
}
