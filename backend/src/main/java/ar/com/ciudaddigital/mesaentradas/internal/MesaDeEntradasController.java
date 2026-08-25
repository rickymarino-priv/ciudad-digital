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
import ar.com.ciudaddigital.mesaentradas.internal.GestionDeExpedientes.ExpedienteCreado;

/**
 * Alta pública de trámites de Mesa de Entradas, su gestión por el
 * municipio (ADR 0015) y la consulta pública por token de seguimiento
 * (ADR 0017).
 *
 * <p>El alta no lleva {@code @PreAuthorize}: es la ruta que
 * {@code DescriptorDelModuloMesaDeEntradas} declara como
 * {@code rutasDeEscrituraPublica()}, protegida solo por el gating de
 * entitlement y el {@code permitAll()} de {@code POST} que arma la cadena
 * de seguridad a partir de esa declaración — mismo mecanismo que
 * {@code ReclamosController} (ADR 0014 §1), reutilizado tal cual por
 * ADR 0015 §4. La consulta por token tampoco lleva {@code @PreAuthorize}:
 * es la ruta que {@code DescriptorDelModuloMesaDeEntradas} declara como
 * {@code rutasDeLecturaPublica()} (ADR 0017 §4). Listar y avanzar el
 * estado sí requieren sesión y permiso.
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
        ExpedienteCreado creado =
                gestion.iniciar(tipo, request.solicitanteNombre(), request.solicitanteContacto(), datos);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ExpedientePublicoResponse.de(creado.expediente(), creado.tokenDeSeguimiento()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('mesaentradas.ver')")
    List<ExpedienteResponse> listar() {
        return gestion.listar().stream().map(ExpedienteResponse::de).toList();
    }

    @GetMapping("/seguimiento/{token}")
    SeguimientoDeExpedienteResponse consultarPorToken(@PathVariable String token) {
        return SeguimientoDeExpedienteResponse.de(gestion.consultarPorToken(token));
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

    /**
     * Mensaje genérico, siempre el mismo, sin importar si el token no
     * matchea ninguna fila o el string ni siquiera tiene forma de token
     * (ADR 0017 §4).
     */
    @ExceptionHandler(TokenNoEncontrado.class)
    ResponseEntity<ErrorResponse> tokenNoEncontrado(TokenNoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No encontramos un trámite con ese código."));
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
     * gestión. {@code tokenDeSeguimiento} es la única vez en toda la vida
     * del expediente que ese valor viaja en claro (ADR 0017 §4): ni la
     * entidad ni ningún otro endpoint lo vuelven a exponer.
     */
    record ExpedientePublicoResponse(Long id, String tipo, String estado, Instant creadoEn,
            String tokenDeSeguimiento) {

        static ExpedientePublicoResponse de(ExpedienteEntity expediente, String tokenDeSeguimiento) {
            return new ExpedientePublicoResponse(
                    expediente.getId(), expediente.getTipo().name(), expediente.getEstado().name(),
                    expediente.getCreadoEn(), tokenDeSeguimiento);
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

    /**
     * Lo que ve el vecino que consulta con su token de seguimiento (ADR
     * 0017 §5): mismo shape que {@link ExpedienteResponse} menos
     * {@code solicitanteContacto} (dato propio, redundante para quien ya lo
     * escribió) y con {@code movimientos} mapeado a
     * {@link MovimientoSeguimientoResponse}, sin actor: quién de la planta
     * municipal atendió el trámite es un dato interno del municipio. Los
     * campos propios del tipo de trámite sí se incluyen tal cual: son
     * datos que el propio vecino ya cargó.
     */
    record SeguimientoDeExpedienteResponse(
            Long id,
            String tipo,
            String estado,
            String solicitanteNombre,
            String domicilioACertificar,
            String rubroComercial,
            String direccionLocal,
            String direccionObra,
            String descripcionObra,
            Instant creadoEn,
            Instant actualizadoEn,
            List<MovimientoSeguimientoResponse> movimientos) {

        static SeguimientoDeExpedienteResponse de(ExpedienteEntity expediente) {
            return new SeguimientoDeExpedienteResponse(
                    expediente.getId(),
                    expediente.getTipo().name(),
                    expediente.getEstado().name(),
                    expediente.getSolicitanteNombre(),
                    expediente.getDomicilioACertificar(),
                    expediente.getRubroComercial(),
                    expediente.getDireccionLocal(),
                    expediente.getDireccionObra(),
                    expediente.getDescripcionObra(),
                    expediente.getCreadoEn(),
                    expediente.getActualizadoEn(),
                    expediente.getMovimientos().stream().map(MovimientoSeguimientoResponse::de).toList());
        }
    }

    /**
     * Un movimiento del historial, sin {@code actorNombre}/{@code actorEmail}
     * (ADR 0017 §5): quién de la planta municipal lo hizo no es algo que el
     * vecino necesite para saber en qué quedó su trámite.
     */
    record MovimientoSeguimientoResponse(
            String estadoAnterior, String estadoNuevo, String comentario, Instant fecha) {

        static MovimientoSeguimientoResponse de(MovimientoDeExpedienteEntity movimiento) {
            return new MovimientoSeguimientoResponse(
                    movimiento.getEstadoAnterior() == null ? null : movimiento.getEstadoAnterior().name(),
                    movimiento.getEstadoNuevo().name(),
                    movimiento.getComentario(),
                    movimiento.getFecha());
        }
    }

    record ErrorResponse(String error) {
    }
}
