package ar.com.ciudaddigital.transparencia.internal;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una partida presupuestaria publicada por este municipio en Transparencia
 * Activa (backlog R11).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, igual que
 * {@code norma} (V7) y {@code sepultura} (V8). {@code publicadoPorNombre} y
 * {@code publicadoPorEmail} son una copia del actor al momento de publicar,
 * no una relación JPA —mismo criterio que {@code NormaEntity}, ADR 0013—:
 * es la firma pública del acto de publicar, no un dato que tenga que
 * seguir vivo si ese usuario cambia de nombre o se desactiva después.
 *
 * <p>Sin métodos de mutación a propósito: una vez publicada, esta rebanada
 * no edita ni borra una partida. Se corrige publicando una nueva, mismo
 * criterio que {@code norma}.
 */
@Entity
@Table(name = "partida_presupuestaria")
class PartidaPresupuestariaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false, length = 150)
    private String area;

    @Column(name = "numero_partida", nullable = false, length = 50)
    private String numeroPartida;

    @Column(nullable = false, length = 300)
    private String concepto;

    @Column(name = "monto_asignado", nullable = false)
    private BigDecimal montoAsignado;

    @Column(name = "monto_ejecutado")
    private BigDecimal montoEjecutado;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected PartidaPresupuestariaEntity() {
    }

    static PartidaPresupuestariaEntity nueva(Integer anio, String area, String numeroPartida, String concepto,
            BigDecimal montoAsignado, BigDecimal montoEjecutado, String publicadoPorNombre,
            String publicadoPorEmail) {

        PartidaPresupuestariaEntity partida = new PartidaPresupuestariaEntity();
        partida.anio = anio;
        partida.area = area;
        partida.numeroPartida = numeroPartida;
        partida.concepto = concepto;
        partida.montoAsignado = montoAsignado;
        partida.montoEjecutado = montoEjecutado;
        partida.publicadoPorNombre = publicadoPorNombre;
        partida.publicadoPorEmail = publicadoPorEmail;
        partida.creadoEn = Instant.now();
        return partida;
    }

    Long getId() {
        return id;
    }

    Integer getAnio() {
        return anio;
    }

    String getArea() {
        return area;
    }

    String getNumeroPartida() {
        return numeroPartida;
    }

    String getConcepto() {
        return concepto;
    }

    BigDecimal getMontoAsignado() {
        return montoAsignado;
    }

    BigDecimal getMontoEjecutado() {
        return montoEjecutado;
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
}
