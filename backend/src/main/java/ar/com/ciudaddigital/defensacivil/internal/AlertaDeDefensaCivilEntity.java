package ar.com.ciudaddigital.defensacivil.internal;

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
 * Una alerta de Defensa Civil publicada por este municipio (ADR 0031 §4).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, mismo criterio
 * que {@code ObraPublicaEntity}/{@code EventoEntity}. {@code publicadoPorNombre}
 * y {@code publicadoPorEmail} son una copia del actor que publica la
 * alerta, no una relación JPA (ADR 0013), mismo criterio que
 * {@code publicadoPorNombre}/{@code publicadoPorEmail} en
 * {@code ObraPublicaEntity}/{@code EventoEntity}.
 *
 * <p>Esta entidad sí muta después de creada, pero solo su {@code estado}
 * de vigencia, con un único salto sin retorno ({@code VIGENTE →
 * FINALIZADA}, ADR 0031 §4): {@code tipo}, {@code nivel}, {@code titulo},
 * {@code descripcion}, {@code recomendaciones} y {@code zonaAfectada} no
 * tienen ningún método de edición a propósito. La validación de que la
 * transición pedida es la única válida vive en {@code GestionDeAlertas},
 * no acá: esta entidad no decide, solo aplica el cambio ya validado
 * (mismo criterio que {@code EventoEntity#cancelar}).
 */
@Entity
@Table(name = "alerta_defensa_civil")
class AlertaDeDefensaCivilEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDeAlerta tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NivelDeAlerta nivel;

    @Column(nullable = false, length = 300)
    private String titulo;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private String recomendaciones;

    @Column(name = "zona_afectada", length = 300)
    private String zonaAfectada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoDeAlerta estado;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected AlertaDeDefensaCivilEntity() {
    }

    /** El estado inicial no es un parámetro: siempre nace {@code VIGENTE} (ADR 0031 §4). */
    static AlertaDeDefensaCivilEntity publicar(TipoDeAlerta tipo, NivelDeAlerta nivel, String titulo,
            String descripcion, String recomendaciones, String zonaAfectada,
            String publicadoPorNombre, String publicadoPorEmail) {

        AlertaDeDefensaCivilEntity alerta = new AlertaDeDefensaCivilEntity();
        alerta.tipo = tipo;
        alerta.nivel = nivel;
        alerta.titulo = titulo;
        alerta.descripcion = descripcion;
        alerta.recomendaciones = recomendaciones;
        alerta.zonaAfectada = zonaAfectada;
        alerta.estado = EstadoDeAlerta.VIGENTE;
        alerta.publicadoPorNombre = publicadoPorNombre;
        alerta.publicadoPorEmail = publicadoPorEmail;
        alerta.creadoEn = Instant.now();
        alerta.actualizadoEn = alerta.creadoEn;
        return alerta;
    }

    Long getId() {
        return id;
    }

    TipoDeAlerta getTipo() {
        return tipo;
    }

    NivelDeAlerta getNivel() {
        return nivel;
    }

    String getTitulo() {
        return titulo;
    }

    String getDescripcion() {
        return descripcion;
    }

    String getRecomendaciones() {
        return recomendaciones;
    }

    String getZonaAfectada() {
        return zonaAfectada;
    }

    EstadoDeAlerta getEstado() {
        return estado;
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

    Instant getActualizadoEn() {
        return actualizadoEn;
    }

    /**
     * Finaliza la alerta, ya validado por {@code GestionDeAlertas} como la
     * única transición posible desde {@code VIGENTE} (ADR 0031 §4), y
     * actualiza {@code actualizadoEn}. Sin parámetro de estado nuevo: no
     * hace falta, {@code FINALIZADA} es el único destino posible.
     */
    void finalizar() {
        this.estado = EstadoDeAlerta.FINALIZADA;
        this.actualizadoEn = Instant.now();
    }
}
