package ar.com.ciudaddigital.multas.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.ciudaddigital.pagos.PasarelaDePago;
import ar.com.ciudaddigital.pagos.ResultadoDeInicioDePago;
import ar.com.ciudaddigital.pagos.SolicitudDePago;

/**
 * Alta protegida, búsqueda pública, descargo y su resolución, e
 * inicio/confirmación de pago de las multas de tránsito del municipio del
 * request en curso (ADR 0021).
 */
@Service
class GestionDeMultas {

    private static final int LARGO_MAXIMO_PATENTE = 20;
    private static final int LARGO_MAXIMO_DNI = 20;
    private static final int LARGO_MAXIMO_DESCRIPCION_INFRACCION = 500;
    private static final int LARGO_MAXIMO_LABRADA_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_LABRADA_POR_EMAIL = 200;
    private static final int LARGO_MAXIMO_DESCARGO_TEXTO = 2000;
    private static final int LARGO_MAXIMO_DESCARGO_CONTACTO = 200;
    private static final int LARGO_MAXIMO_RESOLUCION_COMENTARIO = 2000;
    private static final int LARGO_MAXIMO_RESUELTO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_RESUELTO_POR_EMAIL = 200;

    private final MultaRepository multas;
    private final PasarelaDePago pasarelaDePago;

    GestionDeMultas(MultaRepository multas, PasarelaDePago pasarelaDePago) {
        this.multas = multas;
        this.pasarelaDePago = pasarelaDePago;
    }

    @Transactional("tenantTransactionManager")
    MultaEntity labrar(String patente, String dni, String descripcionInfraccion, BigDecimal monto,
            String labradaPorNombre, String labradaPorEmail) {

        if (patente == null || patente.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la patente.");
        }
        if (patente.length() > LARGO_MAXIMO_PATENTE) {
            throw new SolicitudInvalida("La patente no puede superar los " + LARGO_MAXIMO_PATENTE + " caracteres.");
        }
        // El DNI del conductor no siempre se puede identificar en el momento de
        // labrar el acta: la patente alcanza (ADR 0021, modelo de la Tarea 1).
        String dniNormalizado = (dni == null || dni.isBlank()) ? null : dni;
        if (dniNormalizado != null && dniNormalizado.length() > LARGO_MAXIMO_DNI) {
            throw new SolicitudInvalida("El DNI no puede superar los " + LARGO_MAXIMO_DNI + " caracteres.");
        }
        if (descripcionInfraccion == null || descripcionInfraccion.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar la descripción de la infracción.");
        }
        if (descripcionInfraccion.length() > LARGO_MAXIMO_DESCRIPCION_INFRACCION) {
            throw new SolicitudInvalida(
                    "La descripción de la infracción no puede superar los "
                            + LARGO_MAXIMO_DESCRIPCION_INFRACCION + " caracteres.");
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SolicitudInvalida("El monto tiene que ser mayor a cero.");
        }
        // labradaPorNombre/labradaPorEmail salen del actor autenticado, no de la
        // solicitud: si alguno faltara sería un problema del mecanismo de
        // autenticación, no una solicitud inválida del agente (mismo criterio que
        // GestionDeTasas#publicar).
        if (labradaPorNombre != null && labradaPorNombre.length() > LARGO_MAXIMO_LABRADA_POR_NOMBRE) {
            throw new SolicitudInvalida(
                    "El nombre de quien labra no puede superar los "
                            + LARGO_MAXIMO_LABRADA_POR_NOMBRE + " caracteres.");
        }
        if (labradaPorEmail != null && labradaPorEmail.length() > LARGO_MAXIMO_LABRADA_POR_EMAIL) {
            throw new SolicitudInvalida(
                    "El correo de quien labra no puede superar los "
                            + LARGO_MAXIMO_LABRADA_POR_EMAIL + " caracteres.");
        }

        MultaEntity multa = MultaEntity.labrar(
                patente, dniNormalizado, descripcionInfraccion, monto, labradaPorNombre, labradaPorEmail);
        return multas.save(multa);
    }

    /**
     * Exactamente uno de {@code patente}/{@code dni} tiene que venir con
     * valor (ADR 0021 §6): listar sin ninguno de los dos expondría todas
     * las multas del municipio, y aceptar los dos a la vez es ambiguo
     * sobre cuál usar.
     */
    List<MultaEntity> buscar(String patente, String dni) {
        boolean tienePatente = patente != null && !patente.isBlank();
        boolean tieneDni = dni != null && !dni.isBlank();

        if (tienePatente == tieneDni) {
            throw new SolicitudInvalida("Hay que indicar patente o DNI, pero no los dos ni ninguno.");
        }
        return tienePatente
                ? multas.findByPatenteOrderByNotificadaEnDesc(patente)
                : multas.findByDniOrderByNotificadaEnDesc(dni);
    }

    /**
     * Todas las multas del municipio, para la gestión interna (Tarea 3):
     * a diferencia de {@link #buscar}, no exige un identificador porque
     * quien llama ya tiene el permiso que habilita ver el listado
     * completo.
     */
    List<MultaEntity> listarParaGestion() {
        return multas.findAllByOrderByNotificadaEnDesc();
    }

    @Transactional("tenantTransactionManager")
    MultaEntity presentarDescargo(Long id, String texto, String contacto) {
        if (texto == null || texto.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el texto del descargo.");
        }
        if (texto.length() > LARGO_MAXIMO_DESCARGO_TEXTO) {
            throw new SolicitudInvalida(
                    "El texto del descargo no puede superar los " + LARGO_MAXIMO_DESCARGO_TEXTO + " caracteres.");
        }
        String contactoNormalizado = (contacto == null || contacto.isBlank()) ? null : contacto;
        if (contactoNormalizado != null && contactoNormalizado.length() > LARGO_MAXIMO_DESCARGO_CONTACTO) {
            throw new SolicitudInvalida(
                    "El contacto no puede superar los " + LARGO_MAXIMO_DESCARGO_CONTACTO + " caracteres.");
        }

        MultaEntity multa = multas.findById(id)
                .orElseThrow(() -> new MultaNoEncontrada("No existe la multa " + id + "."));
        multa.presentarDescargo(texto, contactoNormalizado);
        return multas.save(multa);
    }

    @Transactional("tenantTransactionManager")
    MultaEntity resolverDescargo(Long id, String comentario, boolean confirmar,
            String resueltoPorNombre, String resueltoPorEmail) {

        if (comentario == null || comentario.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un comentario de la resolución.");
        }
        if (comentario.length() > LARGO_MAXIMO_RESOLUCION_COMENTARIO) {
            throw new SolicitudInvalida(
                    "El comentario no puede superar los " + LARGO_MAXIMO_RESOLUCION_COMENTARIO + " caracteres.");
        }
        if (resueltoPorNombre != null && resueltoPorNombre.length() > LARGO_MAXIMO_RESUELTO_POR_NOMBRE) {
            throw new SolicitudInvalida(
                    "El nombre de quien resuelve no puede superar los "
                            + LARGO_MAXIMO_RESUELTO_POR_NOMBRE + " caracteres.");
        }
        if (resueltoPorEmail != null && resueltoPorEmail.length() > LARGO_MAXIMO_RESUELTO_POR_EMAIL) {
            throw new SolicitudInvalida(
                    "El correo de quien resuelve no puede superar los "
                            + LARGO_MAXIMO_RESUELTO_POR_EMAIL + " caracteres.");
        }

        MultaEntity multa = multas.findById(id)
                .orElseThrow(() -> new MultaNoEncontrada("No existe la multa " + id + "."));
        multa.resolverDescargo(comentario, confirmar, resueltoPorNombre, resueltoPorEmail);
        return multas.save(multa);
    }

    @Transactional("tenantTransactionManager")
    IniciarPagoResultado iniciarPago(Long id) {
        MultaEntity multa = multas.findById(id)
                .orElseThrow(() -> new MultaNoEncontrada("No existe la multa " + id + "."));

        // Segunda validación (además de la que hace MultaEntity.iniciarPago):
        // acá ya conocemos el mensaje específico por estado antes de tocar la
        // entidad, para no gastar una llamada a la pasarela con una multa que
        // de todos modos no se puede cobrar.
        switch (multa.getEstado()) {
            case EN_DESCARGO ->
                throw new SolicitudInvalida("No se puede pagar una multa con un descargo en trámite.");
            case PAGADA -> throw new SolicitudInvalida("Esta multa ya está pagada.");
            case ANULADA -> throw new SolicitudInvalida("Esta multa fue anulada.");
            case NOTIFICADA, CONFIRMADA -> { /* estado válido para pagar */ }
        }

        BigDecimal montoAPagar = multa.montoAPagar(Instant.now());
        ResultadoDeInicioDePago resultado = pasarelaDePago.iniciarPago(new SolicitudDePago(
                id.toString(), montoAPagar, multa.getDescripcionInfraccion()));

        multa.iniciarPago(resultado.referenciaExterna());
        multas.save(multa);
        return new IniciarPagoResultado(resultado.referenciaExterna(), resultado.urlDePago());
    }

    /**
     * Confirma (o rechaza) el pago en curso identificado por su
     * {@code referenciaExterna} (ADR 0021 §7, ADR 0018 §4). Una referencia
     * vacía se trata igual que "no encontrada", nunca como
     * {@link SolicitudInvalida} — mismo criterio que
     * {@code GestionDeTasas#confirmarPago} (ADR 0017 §4) — para no
     * distinguirle a quien manda datos inventados un formato inválido de
     * una referencia que no existe.
     */
    @Transactional("tenantTransactionManager")
    MultaEntity confirmarPago(String referenciaExterna, boolean aprobado) {
        if (referenciaExterna == null || referenciaExterna.isBlank()) {
            throw new PagoNoEncontrado("No encontramos un pago con esa referencia.");
        }

        MultaEntity multa = multas.findByReferenciaExternaPago(referenciaExterna)
                .orElseThrow(() -> new PagoNoEncontrado("No encontramos un pago con esa referencia."));

        multa.confirmarPago(aprobado);
        return multas.save(multa);
    }

    /** Resultado de iniciar un pago: lo mínimo que el vecino necesita para pasar al simulador (ADR 0018 §1). */
    record IniciarPagoResultado(String referenciaExterna, String urlDePago) {
    }
}
