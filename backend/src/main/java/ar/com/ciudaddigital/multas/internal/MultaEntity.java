package ar.com.ciudaddigital.multas.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un acta de infracción de tránsito labrada por un agente municipal contra
 * una patente, con su ciclo de vida propio (ADR 0021 §2).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, mismo criterio
 * que {@code TasaEntity}. {@code labradaPorNombre}/{@code labradaPorEmail}
 * y {@code resueltoPorNombre}/{@code resueltoPorEmail} son copias del
 * actor al momento de cada acto, no una relación JPA (ADR 0013/0018 §2).
 *
 * <p>A diferencia de {@code TasaEntity}, el monto que efectivamente se
 * cobra no es fijo: {@link #montoAPagar(Instant)} aplica el descuento por
 * pago voluntario temprano (ADR 0021 §8) en el momento de pagar, nunca
 * antes.
 */
@Entity
@Table(name = "multa")
class MultaEntity {

    /** Descuento por pago voluntario temprano (ADR 0021 §8). */
    static final BigDecimal PORCENTAJE_DESCUENTO = new BigDecimal("0.20");

    /** Plazo, en días corridos desde {@code notificadaEn}, para acceder al descuento (ADR 0021 §8). */
    static final int DIAS_PLAZO_DESCUENTO = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String patente;

    @Column(length = 20)
    private String dni;

    @Column(name = "descripcion_infraccion", nullable = false, length = 500)
    private String descripcionInfraccion;

    @Column(name = "monto_original", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoOriginal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDeMulta estado;

    @Column(name = "notificada_en", nullable = false)
    private Instant notificadaEn;

    @Column(name = "labrada_por_nombre", nullable = false, length = 150)
    private String labradaPorNombre;

    @Column(name = "labrada_por_email", nullable = false, length = 200)
    private String labradaPorEmail;

    @Column(name = "descargo_texto", length = 2000)
    private String descargoTexto;

    @Column(name = "descargo_contacto", length = 200)
    private String descargoContacto;

    @Column(name = "descargo_presentado_en")
    private Instant descargoPresentadoEn;

    @Column(name = "resolucion_comentario", length = 2000)
    private String resolucionComentario;

    @Column(name = "resuelto_por_nombre", length = 150)
    private String resueltoPorNombre;

    @Column(name = "resuelto_por_email", length = 200)
    private String resueltoPorEmail;

    @Column(name = "resuelto_en")
    private Instant resueltoEn;

    @Column(name = "fecha_pago")
    private Instant fechaPago;

    @Column(name = "referencia_externa_pago", length = 100)
    private String referenciaExternaPago;

    protected MultaEntity() {
    }

    static MultaEntity labrar(String patente, String dni, String descripcionInfraccion, BigDecimal montoOriginal,
            String labradaPorNombre, String labradaPorEmail) {

        MultaEntity multa = new MultaEntity();
        multa.patente = patente;
        multa.dni = dni;
        multa.descripcionInfraccion = descripcionInfraccion;
        multa.montoOriginal = montoOriginal;
        multa.estado = EstadoDeMulta.NOTIFICADA;
        multa.notificadaEn = Instant.now();
        multa.labradaPorNombre = labradaPorNombre;
        multa.labradaPorEmail = labradaPorEmail;
        return multa;
    }

    Long getId() {
        return id;
    }

    String getPatente() {
        return patente;
    }

    String getDni() {
        return dni;
    }

    String getDescripcionInfraccion() {
        return descripcionInfraccion;
    }

    BigDecimal getMontoOriginal() {
        return montoOriginal;
    }

    EstadoDeMulta getEstado() {
        return estado;
    }

    Instant getNotificadaEn() {
        return notificadaEn;
    }

    String getLabradaPorNombre() {
        return labradaPorNombre;
    }

    String getLabradaPorEmail() {
        return labradaPorEmail;
    }

    String getDescargoTexto() {
        return descargoTexto;
    }

    String getDescargoContacto() {
        return descargoContacto;
    }

    Instant getDescargoPresentadoEn() {
        return descargoPresentadoEn;
    }

    String getResolucionComentario() {
        return resolucionComentario;
    }

    String getResueltoPorNombre() {
        return resueltoPorNombre;
    }

    String getResueltoPorEmail() {
        return resueltoPorEmail;
    }

    Instant getResueltoEn() {
        return resueltoEn;
    }

    Instant getFechaPago() {
        return fechaPago;
    }

    String getReferenciaExternaPago() {
        return referenciaExternaPago;
    }

    /**
     * Monto vigente a cobrar en {@code ahora}: el 80% del monto original si
     * la multa sigue {@code NOTIFICADA}, nunca pasó por un descargo, y
     * {@code ahora} cae dentro de los {@value #DIAS_PLAZO_DESCUENTO} días
     * corridos desde {@code notificadaEn}; el monto original en cualquier
     * otro caso (ADR 0021 §8). Una vez impugnada, la multa pierde el
     * derecho al descuento para siempre, aunque el descargo se resuelva a
     * su favor.
     */
    BigDecimal montoAPagar(Instant ahora) {
        boolean dentroDePlazo = estado == EstadoDeMulta.NOTIFICADA
                && descargoPresentadoEn == null
                && ahora.isBefore(notificadaEn.plus(DIAS_PLAZO_DESCUENTO, ChronoUnit.DAYS));

        if (!dentroDePlazo) {
            return montoOriginal;
        }
        return montoOriginal
                .multiply(BigDecimal.ONE.subtract(PORCENTAJE_DESCUENTO))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Presenta un descargo de texto libre contra la multa: solo posible
     * mientras está {@code NOTIFICADA} (ADR 0021 §5) — no hay un segundo
     * ciclo de descargo sobre la misma multa en esta rebanada.
     */
    void presentarDescargo(String texto, String contacto) {
        if (estado != EstadoDeMulta.NOTIFICADA) {
            throw new SolicitudInvalida("No se puede presentar un descargo sobre esta multa en su estado actual.");
        }
        this.descargoTexto = texto;
        this.descargoContacto = contacto;
        this.descargoPresentadoEn = Instant.now();
        this.estado = EstadoDeMulta.EN_DESCARGO;
    }

    /**
     * Resuelve el descargo en curso: confirma la multa (rechaza el
     * descargo) o la anula (hace lugar al descargo). Solo posible mientras
     * está {@code EN_DESCARGO} (ADR 0021 §2).
     */
    void resolverDescargo(String comentario, boolean confirmar, String resueltoPorNombre, String resueltoPorEmail) {
        if (estado != EstadoDeMulta.EN_DESCARGO) {
            throw new SolicitudInvalida("Esta multa no tiene un descargo pendiente de resolución.");
        }
        this.resolucionComentario = comentario;
        this.resueltoPorNombre = resueltoPorNombre;
        this.resueltoPorEmail = resueltoPorEmail;
        this.resueltoEn = Instant.now();
        this.estado = confirmar ? EstadoDeMulta.CONFIRMADA : EstadoDeMulta.ANULADA;
    }

    /**
     * Inicia un intento de pago: exige que la multa esté {@code NOTIFICADA}
     * o {@code CONFIRMADA} (ADR 0021 §2) — segunda barrera, dentro de la
     * entidad, del mismo chequeo que ya hace {@code GestionDeMultas}.
     */
    void iniciarPago(String referenciaExterna) {
        switch (estado) {
            case EN_DESCARGO ->
                throw new SolicitudInvalida("No se puede pagar una multa con un descargo en trámite.");
            case PAGADA -> throw new SolicitudInvalida("Esta multa ya está pagada.");
            case ANULADA -> throw new SolicitudInvalida("Esta multa fue anulada.");
            case NOTIFICADA, CONFIRMADA -> this.referenciaExternaPago = referenciaExterna;
        }
    }

    /**
     * Aplica el resultado de la pasarela sobre el intento de pago en
     * curso. Si se aprueba, pasa a {@code PAGADA}; si se rechaza, limpia
     * {@code referenciaExternaPago} para permitir reintentar, dejando el
     * estado previo ({@code NOTIFICADA} o {@code CONFIRMADA}) intacto
     * (mismo patrón que {@code TasaEntity#confirmarPago}).
     */
    void confirmarPago(boolean aprobado) {
        if (referenciaExternaPago == null) {
            // No debería pasar: GestionDeMultas siempre llega hasta acá
            // habiendo encontrado esta fila por su referenciaExternaPago,
            // así que si está null es un problema de invariante interno,
            // no algo que el llamador haya hecho mal.
            throw new IllegalStateException("No hay un pago en curso para confirmar.");
        }
        if (aprobado) {
            this.estado = EstadoDeMulta.PAGADA;
            this.fechaPago = Instant.now();
        } else {
            this.referenciaExternaPago = null;
        }
    }
}
