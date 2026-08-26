package ar.com.ciudaddigital.tasas.internal;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una tasa municipal sembrada por el municipio para un número de cuenta, y
 * su estado de pago online (backlog R13, ADR 0018).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, mismo criterio
 * que el resto de las entidades de módulo (p. ej. {@code SepulturaEntity}).
 * {@code publicadoPorNombre}/{@code publicadoPorEmail} son una copia del
 * actor al momento de publicar, no una relación JPA (ADR 0013).
 *
 * <p>{@code referenciaExternaPago} es el puente con el pago en curso
 * contra {@code pagos.PasarelaDePago}: nulo mientras no hay ningún pago
 * iniciado, se limpia si el pago se rechaza (para permitir reintentar,
 * ADR 0018 §4) y se conserva cuando se aprueba, aunque en ese punto ya no
 * hace falta para nada más que trazabilidad.
 */
@Entity
@Table(name = "tasa")
class TasaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_cuenta", nullable = false, length = 50)
    private String numeroCuenta;

    @Column(nullable = false, length = 200)
    private String concepto;

    @Column(nullable = false, length = 50)
    private String periodo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDeTasa estado;

    @Column(name = "fecha_pago")
    private Instant fechaPago;

    @Column(name = "referencia_externa_pago", length = 100)
    private String referenciaExternaPago;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected TasaEntity() {
    }

    static TasaEntity nueva(String numeroCuenta, String concepto, String periodo, BigDecimal monto,
            String publicadoPorNombre, String publicadoPorEmail) {

        TasaEntity tasa = new TasaEntity();
        tasa.numeroCuenta = numeroCuenta;
        tasa.concepto = concepto;
        tasa.periodo = periodo;
        tasa.monto = monto;
        tasa.estado = EstadoDeTasa.PENDIENTE;
        tasa.publicadoPorNombre = publicadoPorNombre;
        tasa.publicadoPorEmail = publicadoPorEmail;
        tasa.creadoEn = Instant.now();
        return tasa;
    }

    Long getId() {
        return id;
    }

    String getNumeroCuenta() {
        return numeroCuenta;
    }

    String getConcepto() {
        return concepto;
    }

    String getPeriodo() {
        return periodo;
    }

    BigDecimal getMonto() {
        return monto;
    }

    EstadoDeTasa getEstado() {
        return estado;
    }

    Instant getFechaPago() {
        return fechaPago;
    }

    String getReferenciaExternaPago() {
        return referenciaExternaPago;
    }

    String getPublicadoPorNombre() {
        return publicadoPorNombre;
    }

    String getPublicadoPorEmail() {
        return publicadoPorEmail;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }

    /**
     * Inicia un intento de pago: exige que la tasa siga {@code PENDIENTE}
     * (ADR 0018 §4) — una tasa ya pagada no puede reabrir un cobro.
     */
    void iniciarPago(String referenciaExterna) {
        if (estado != EstadoDeTasa.PENDIENTE) {
            throw new SolicitudInvalida("Esta tasa ya está pagada.");
        }
        this.referenciaExternaPago = referenciaExterna;
    }

    /**
     * Aplica el resultado de la pasarela sobre el intento de pago en
     * curso. Si se aprueba, pasa a {@code PAGADA}; si se rechaza, limpia
     * {@code referenciaExternaPago} para permitir reintentar (ADR 0018
     * §4, spec CD-21).
     */
    void confirmarPago(boolean aprobado) {
        if (referenciaExternaPago == null) {
            // No debería pasar: GestionDeTasas siempre llega hasta acá
            // habiendo encontrado esta fila por su referenciaExternaPago,
            // así que si está null es un problema de invariante interno,
            // no algo que el llamador haya hecho mal.
            throw new IllegalStateException("No hay un pago en curso para confirmar.");
        }
        if (aprobado) {
            this.estado = EstadoDeTasa.PAGADA;
            this.fechaPago = Instant.now();
        } else {
            this.referenciaExternaPago = null;
        }
    }
}
