package ar.com.ciudaddigital.tasas.internal;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.ciudaddigital.pagos.PasarelaDePago;
import ar.com.ciudaddigital.pagos.ResultadoDeInicioDePago;
import ar.com.ciudaddigital.pagos.SolicitudDePago;

/**
 * Alta, búsqueda por número de cuenta, e inicio/confirmación de pago de las
 * tasas del municipio del request en curso (backlog R13, ADR 0018).
 */
@Service
class GestionDeTasas {

    private static final int LARGO_MAXIMO_NUMERO_CUENTA = 50;
    private static final int LARGO_MAXIMO_CONCEPTO = 200;
    private static final int LARGO_MAXIMO_PERIODO = 50;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_NOMBRE = 150;
    private static final int LARGO_MAXIMO_PUBLICADO_POR_EMAIL = 200;

    private final TasaRepository tasas;
    private final PasarelaDePago pasarelaDePago;

    GestionDeTasas(TasaRepository tasas, PasarelaDePago pasarelaDePago) {
        this.tasas = tasas;
        this.pasarelaDePago = pasarelaDePago;
    }

    @Transactional("tenantTransactionManager")
    TasaEntity publicar(String numeroCuenta, String concepto, String periodo, BigDecimal monto,
            String publicadoPorNombre, String publicadoPorEmail) {

        if (numeroCuenta == null || numeroCuenta.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el número de cuenta.");
        }
        if (numeroCuenta.length() > LARGO_MAXIMO_NUMERO_CUENTA) {
            throw new SolicitudInvalida(
                    "El número de cuenta no puede superar los " + LARGO_MAXIMO_NUMERO_CUENTA + " caracteres.");
        }
        if (concepto == null || concepto.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el concepto.");
        }
        if (concepto.length() > LARGO_MAXIMO_CONCEPTO) {
            throw new SolicitudInvalida("El concepto no puede superar los " + LARGO_MAXIMO_CONCEPTO + " caracteres.");
        }
        if (periodo == null || periodo.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el período.");
        }
        if (periodo.length() > LARGO_MAXIMO_PERIODO) {
            throw new SolicitudInvalida("El período no puede superar los " + LARGO_MAXIMO_PERIODO + " caracteres.");
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SolicitudInvalida("El monto tiene que ser mayor a cero.");
        }
        // publicadoPorNombre/publicadoPorEmail salen del actor autenticado, no de la
        // solicitud: si alguno faltara sería un problema del mecanismo de
        // autenticación, no una solicitud inválida del agente (mismo criterio que
        // GestionDelCementerio#registrar).
        if (publicadoPorNombre != null && publicadoPorNombre.length() > LARGO_MAXIMO_PUBLICADO_POR_NOMBRE) {
            throw new SolicitudInvalida(
                    "El nombre de quien publica no puede superar los "
                            + LARGO_MAXIMO_PUBLICADO_POR_NOMBRE + " caracteres.");
        }
        if (publicadoPorEmail != null && publicadoPorEmail.length() > LARGO_MAXIMO_PUBLICADO_POR_EMAIL) {
            throw new SolicitudInvalida(
                    "El correo de quien publica no puede superar los "
                            + LARGO_MAXIMO_PUBLICADO_POR_EMAIL + " caracteres.");
        }

        TasaEntity tasa =
                TasaEntity.nueva(numeroCuenta, concepto, periodo, monto, publicadoPorNombre, publicadoPorEmail);
        return tasas.save(tasa);
    }

    /**
     * {@code numeroCuenta} es obligatorio a propósito, no un filtro
     * opcional: a diferencia de la búsqueda de {@code cementerio} (que
     * permite listar todo sin filtro), acá listar sin número de cuenta
     * expondría montos y conceptos de todos los contribuyentes del
     * municipio de una, más de lo que hace falta para que un vecino
     * encuentre su propia tasa. No hay ninguna otra forma de listar tasas
     * en esta rebanada.
     */
    List<TasaEntity> buscarPorCuenta(String numeroCuenta) {
        if (numeroCuenta == null || numeroCuenta.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el número de cuenta.");
        }
        return tasas.findByNumeroCuentaOrderByCreadoEnDesc(numeroCuenta);
    }

    @Transactional("tenantTransactionManager")
    IniciarPagoResultado iniciarPago(Long tasaId) {
        TasaEntity tasa = tasas.findById(tasaId)
                .orElseThrow(() -> new TasaNoEncontrada("No existe la tasa " + tasaId + "."));

        if (tasa.getEstado() != EstadoDeTasa.PENDIENTE) {
            throw new SolicitudInvalida("Esta tasa ya está pagada.");
        }

        ResultadoDeInicioDePago resultado = pasarelaDePago.iniciarPago(new SolicitudDePago(
                tasaId.toString(), tasa.getMonto(), tasa.getConcepto() + " - " + tasa.getPeriodo()));

        tasa.iniciarPago(resultado.referenciaExterna());
        tasas.save(tasa);
        return new IniciarPagoResultado(resultado.referenciaExterna(), resultado.urlDePago());
    }

    /**
     * Confirma (o rechaza) el pago en curso identificado por su
     * {@code referenciaExterna} (ADR 0018 §4). Una referencia vacía se
     * trata igual que "no encontrada", nunca como {@link SolicitudInvalida}
     * — mismo criterio que {@code GestionDeReclamos#consultarPorToken}
     * (ADR 0017 §4) — para no distinguirle a quien manda datos inventados
     * un formato inválido de una referencia que no existe.
     */
    @Transactional("tenantTransactionManager")
    TasaEntity confirmarPago(String referenciaExterna, boolean aprobado) {
        if (referenciaExterna == null || referenciaExterna.isBlank()) {
            throw new PagoNoEncontrado("No encontramos un pago con esa referencia.");
        }

        TasaEntity tasa = tasas.findByReferenciaExternaPago(referenciaExterna)
                .orElseThrow(() -> new PagoNoEncontrado("No encontramos un pago con esa referencia."));

        tasa.confirmarPago(aprobado);
        return tasas.save(tasa);
    }

    /** Resultado de iniciar un pago: lo mínimo que el vecino necesita para pasar al simulador (ADR 0018 §1). */
    record IniciarPagoResultado(String referenciaExterna, String urlDePago) {
    }
}
