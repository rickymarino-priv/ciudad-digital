package ar.com.ciudaddigital.multas.internal;

import java.math.BigDecimal;
import java.time.Instant;
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
import ar.com.ciudaddigital.multas.internal.GestionDeMultas.IniciarPagoResultado;

/**
 * Alta protegida, búsqueda pública, descargo y su resolución, e
 * inicio/confirmación pública de pago de multas de tránsito (ADR 0021).
 *
 * <p>Labrar y resolver un descargo requieren sesión y permiso. La
 * búsqueda, presentar un descargo, e iniciar/confirmar un pago son las
 * rutas que {@code DescriptorDelModuloMultas} declara como públicas (ADR
 * 0012 §1, ADR 0014 §1, ADR 0018 §4, ADR 0021 §5/§6/§7): corren sin
 * sesión, protegidas solo por el gating de entitlement.
 */
@RestController
@RequestMapping("/api/multas")
class MultasController {

    private final GestionDeMultas gestion;

    MultasController(GestionDeMultas gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('multas.labrar')")
    ResponseEntity<MultaResponse> labrar(@RequestBody LabrarMultaRequest request, Authentication autenticacion) {
        ActorAutenticado actor = actorDe(autenticacion);
        MultaEntity multa = gestion.labrar(
                request.patente(), request.dni(), request.descripcionInfraccion(), request.monto(),
                actor.nombre(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(MultaResponse.de(multa));
    }

    @GetMapping
    List<MultaResponse> buscar(
            @RequestParam(required = false) String patente, @RequestParam(required = false) String dni) {

        return gestion.buscar(patente, dni).stream().map(MultaResponse::de).toList();
    }

    @GetMapping("/gestion")
    @PreAuthorize("hasAnyAuthority('multas.labrar', 'multas.resolverDescargo')")
    List<MultaResponse> listarParaGestion() {
        return gestion.listarParaGestion().stream().map(MultaResponse::de).toList();
    }

    @PostMapping("/{id}/descargo")
    MultaResponse presentarDescargo(@PathVariable Long id, @RequestBody PresentarDescargoRequest request) {
        MultaEntity multa = gestion.presentarDescargo(id, request.texto(), request.contacto());
        return MultaResponse.de(multa);
    }

    @PostMapping("/{id}/resolver-descargo")
    @PreAuthorize("hasAuthority('multas.resolverDescargo')")
    MultaResponse resolverDescargo(
            @PathVariable Long id, @RequestBody ResolverDescargoRequest request, Authentication autenticacion) {

        ActorAutenticado actor = actorDe(autenticacion);
        MultaEntity multa = gestion.resolverDescargo(
                id, request.comentario(), request.confirmar(), actor.nombre(), actor.email());
        return MultaResponse.de(multa);
    }

    @PostMapping("/{id}/pagos")
    IniciarPagoResponse iniciarPago(@PathVariable Long id) {
        IniciarPagoResultado resultado = gestion.iniciarPago(id);
        return new IniciarPagoResponse(resultado.referenciaExterna(), resultado.urlDePago());
    }

    @PostMapping("/pagos/confirmar")
    MultaResponse confirmarPago(@RequestBody ConfirmarPagoRequest request) {
        MultaEntity multa = gestion.confirmarPago(request.referenciaExterna(), request.aprobado());
        return MultaResponse.de(multa);
    }

    private static ActorAutenticado actorDe(Authentication autenticacion) {
        if (autenticacion.getPrincipal() instanceof ActorAutenticado actor) {
            return actor;
        }
        // No debería pasar: el permiso ya exige sesión de acceso, así que el
        // principal siempre es un ActorAutenticado. Si no lo es, es un
        // problema del mecanismo de autenticación, no una solicitud
        // inválida del agente (mismo criterio que TasasController#publicar).
        throw new IllegalStateException("No hay un actor autenticado para firmar la operación.");
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /** Un id de multa que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(MultaNoEncontrada.class)
    ResponseEntity<ErrorResponse> multaNoEncontrada(MultaNoEncontrada e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("No encontramos esa multa."));
    }

    /**
     * Mensaje genérico, siempre el mismo, sin importar si la referencia no
     * matchea ninguna fila o directamente no tiene forma de referencia
     * real (ADR 0017 §4, ADR 0021 §7).
     */
    @ExceptionHandler(PagoNoEncontrado.class)
    ResponseEntity<ErrorResponse> pagoNoEncontrado(PagoNoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No encontramos un pago con esa referencia."));
    }

    record LabrarMultaRequest(String patente, String dni, String descripcionInfraccion, BigDecimal monto) {
    }

    record PresentarDescargoRequest(String texto, String contacto) {
    }

    record ResolverDescargoRequest(String comentario, boolean confirmar) {
    }

    record ConfirmarPagoRequest(String referenciaExterna, boolean aprobado) {
    }

    record IniciarPagoResponse(String referenciaExterna, String urlDePago) {
    }

    /**
     * Shape único para alta, búsqueda pública, descargo/resolución y
     * confirmación (mismo criterio que {@code TasaResponse}, spec CD-25):
     * no hay un dato de tercero que minimizar más allá de lo que ya es
     * público por diseño al buscar por patente/DNI.
     *
     * <p>{@code montoAPagar} es el monto vigente al momento de responder
     * ({@code MultaEntity#montoAPagar(Instant.now())}), con el descuento ya
     * aplicado si corresponde: el frontend lo muestra tal cual, sin
     * reimplementar la regla de descuento. Para una multa ya {@code PAGADA}
     * o {@code ANULADA} el método ya no aplica ningún descuento (exige
     * {@code NOTIFICADA}), así que devuelve el monto original — no hace
     * falta una columna nueva para guardar el monto efectivamente cobrado.
     */
    record MultaResponse(
            Long id,
            String patente,
            String dni,
            String descripcionInfraccion,
            BigDecimal montoOriginal,
            BigDecimal montoAPagar,
            String estado,
            Instant notificadaEn,
            String labradaPorNombre,
            String labradaPorEmail,
            String descargoTexto,
            String descargoContacto,
            Instant descargoPresentadoEn,
            String resolucionComentario,
            String resueltoPorNombre,
            String resueltoPorEmail,
            Instant resueltoEn,
            Instant fechaPago) {

        static MultaResponse de(MultaEntity multa) {
            return new MultaResponse(
                    multa.getId(),
                    multa.getPatente(),
                    multa.getDni(),
                    multa.getDescripcionInfraccion(),
                    multa.getMontoOriginal(),
                    multa.montoAPagar(Instant.now()),
                    multa.getEstado().name(),
                    multa.getNotificadaEn(),
                    multa.getLabradaPorNombre(),
                    multa.getLabradaPorEmail(),
                    multa.getDescargoTexto(),
                    multa.getDescargoContacto(),
                    multa.getDescargoPresentadoEn(),
                    multa.getResolucionComentario(),
                    multa.getResueltoPorNombre(),
                    multa.getResueltoPorEmail(),
                    multa.getResueltoEn(),
                    multa.getFechaPago());
        }
    }

    record ErrorResponse(String error) {
    }
}
