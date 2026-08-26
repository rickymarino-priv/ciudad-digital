package ar.com.ciudaddigital.tasas.internal;

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
import ar.com.ciudaddigital.tasas.internal.GestionDeTasas.IniciarPagoResultado;

/**
 * Alta protegida, búsqueda pública, e inicio/confirmación pública de pago
 * de tasas municipales (backlog R13, ADR 0018).
 *
 * <p>El alta requiere sesión y el permiso {@code tasas.publicar}. La
 * búsqueda es la ruta que {@code DescriptorDelModuloTasas} declara como
 * {@code rutasDeLecturaPublica()} (ADR 0012 §1), mismo mecanismo que
 * {@code CementerioController}. Iniciar y confirmar un pago son las rutas
 * que declara como {@code rutasDeEscrituraPublica()} (ADR 0014 §1, ADR
 * 0018 §4): un vecino sin cuenta paga su tasa, y —contra el único
 * adaptador que existe hoy— el propio frontend confirma el resultado
 * haciendo de pasarela. Las tres corren sin sesión, protegidas solo por el
 * gating de entitlement.
 */
@RestController
@RequestMapping("/api/tasas")
class TasasController {

    private final GestionDeTasas gestion;

    TasasController(GestionDeTasas gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tasas.publicar')")
    ResponseEntity<TasaResponse> publicar(@RequestBody PublicarTasaRequest request, Authentication autenticacion) {
        String nombre;
        String email;
        if (autenticacion.getPrincipal() instanceof ActorAutenticado actor) {
            nombre = actor.nombre();
            email = actor.email();
        } else {
            // No debería pasar: el permiso ya exige sesión de acceso, así
            // que el principal siempre es un ActorAutenticado. Si no lo es,
            // es un problema del mecanismo de autenticación, no una
            // solicitud inválida del agente.
            throw new IllegalStateException("No hay un actor autenticado para firmar la publicación.");
        }

        TasaEntity tasa = gestion.publicar(
                request.numeroCuenta(), request.concepto(), request.periodo(), request.monto(), nombre, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(TasaResponse.de(tasa));
    }

    @GetMapping
    List<TasaResponse> buscar(@RequestParam String numeroCuenta) {
        return gestion.buscarPorCuenta(numeroCuenta).stream().map(TasaResponse::de).toList();
    }

    @PostMapping("/{id}/pagos")
    IniciarPagoResponse iniciarPago(@PathVariable Long id) {
        IniciarPagoResultado resultado = gestion.iniciarPago(id);
        return new IniciarPagoResponse(resultado.referenciaExterna(), resultado.urlDePago());
    }

    @PostMapping("/pagos/confirmar")
    TasaResponse confirmarPago(@RequestBody ConfirmarPagoRequest request) {
        TasaEntity tasa = gestion.confirmarPago(request.referenciaExterna(), request.aprobado());
        return TasaResponse.de(tasa);
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /** Un id de tasa que no existe (inventado o de otro municipio, ver ADR 0001) da 404 genérico. */
    @ExceptionHandler(TasaNoEncontrada.class)
    ResponseEntity<ErrorResponse> tasaNoEncontrada(TasaNoEncontrada e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("No encontramos esa tasa."));
    }

    /**
     * Mensaje genérico, siempre el mismo, sin importar si la referencia no
     * matchea ninguna fila o directamente no tiene forma de referencia
     * real (ADR 0017 §4, mismo criterio aplicado acá a la confirmación de
     * pago).
     */
    @ExceptionHandler(PagoNoEncontrado.class)
    ResponseEntity<ErrorResponse> pagoNoEncontrado(PagoNoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No encontramos un pago con esa referencia."));
    }

    record PublicarTasaRequest(String numeroCuenta, String concepto, String periodo, BigDecimal monto) {
    }

    record ConfirmarPagoRequest(String referenciaExterna, boolean aprobado) {
    }

    record IniciarPagoResponse(String referenciaExterna, String urlDePago) {
    }

    /**
     * Shape único para alta, búsqueda pública y confirmación (spec CD-21):
     * a diferencia de {@code cementerio}, acá no hay un dato de tercero
     * que minimizar en la versión pública —{@code publicadoPorNombre}/
     * {@code publicadoPorEmail} es la firma institucional del municipio,
     * mismo criterio que {@code boletin}/{@code transparencia}—.
     * Deliberadamente sin {@code referenciaExternaPago}: es un detalle
     * interno de la integración con la pasarela, no algo que el vecino
     * necesite ver.
     */
    record TasaResponse(
            Long id,
            String numeroCuenta,
            String concepto,
            String periodo,
            BigDecimal monto,
            String estado,
            Instant fechaPago,
            String publicadoPorNombre,
            String publicadoPorEmail,
            Instant creadoEn) {

        static TasaResponse de(TasaEntity tasa) {
            return new TasaResponse(
                    tasa.getId(),
                    tasa.getNumeroCuenta(),
                    tasa.getConcepto(),
                    tasa.getPeriodo(),
                    tasa.getMonto(),
                    tasa.getEstado().name(),
                    tasa.getFechaPago(),
                    tasa.getPublicadoPorNombre(),
                    tasa.getPublicadoPorEmail(),
                    tasa.getCreadoEn());
        }
    }

    record ErrorResponse(String error) {
    }
}
