package ar.com.ciudaddigital.espaciosverdes.internal;

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
 * Un espacio verde (plaza, parque o paseo) registrado por este municipio
 * (ADR 0029).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, mismo criterio
 * que {@code ObraPublicaEntity}/{@code ArbolUrbanoEntity}.
 * {@code publicadoPorNombre} y {@code publicadoPorEmail} son una copia del
 * actor que registra el espacio verde, no una relación JPA (ADR 0013),
 * mismo criterio que {@code publicadoPorNombre}/{@code publicadoPorEmail}
 * en {@code ObraPublicaEntity}/{@code ArbolUrbanoEntity}.
 *
 * <p>Esta entidad sí muta después de creada, pero solo su {@code estado}
 * (ADR 0029 §5): {@code nombre}, {@code tipo}, {@code ubicacion},
 * {@code descripcion} y {@code superficie} no tienen ningún método de
 * edición a propósito. La validación de qué transición de estado es
 * válida vive en {@code GestionDeEspaciosVerdes}, no acá: esta entidad no
 * sabe qué transiciones son válidas, solo aplica la que ya fue validada
 * (mismo criterio que {@code ObraPublicaEntity#actualizarEstado}).
 */
@Entity
@Table(name = "espacio_verde")
class EspacioVerdeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDeEspacioVerde tipo;

    @Column(nullable = false, length = 300)
    private String ubicacion;

    @Column
    private String descripcion;

    @Column
    private BigDecimal superficie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoDeEspacioVerde estado;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected EspacioVerdeEntity() {
    }

    /** El estado inicial no es un parámetro: siempre nace {@code DISPONIBLE} (ADR 0029 §5). */
    static EspacioVerdeEntity registrar(String nombre, TipoDeEspacioVerde tipo, String ubicacion,
            String descripcion, BigDecimal superficie, String publicadoPorNombre, String publicadoPorEmail) {

        EspacioVerdeEntity espacioVerde = new EspacioVerdeEntity();
        espacioVerde.nombre = nombre;
        espacioVerde.tipo = tipo;
        espacioVerde.ubicacion = ubicacion;
        espacioVerde.descripcion = descripcion;
        espacioVerde.superficie = superficie;
        espacioVerde.estado = EstadoDeEspacioVerde.DISPONIBLE;
        espacioVerde.publicadoPorNombre = publicadoPorNombre;
        espacioVerde.publicadoPorEmail = publicadoPorEmail;
        espacioVerde.creadoEn = Instant.now();
        espacioVerde.actualizadoEn = espacioVerde.creadoEn;
        return espacioVerde;
    }

    Long getId() {
        return id;
    }

    String getNombre() {
        return nombre;
    }

    TipoDeEspacioVerde getTipo() {
        return tipo;
    }

    String getUbicacion() {
        return ubicacion;
    }

    String getDescripcion() {
        return descripcion;
    }

    BigDecimal getSuperficie() {
        return superficie;
    }

    EstadoDeEspacioVerde getEstado() {
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
     * Aplica el estado nuevo, ya validado por {@code GestionDeEspaciosVerdes}
     * contra la tabla de transiciones (ADR 0029 §5), y actualiza
     * {@code actualizadoEn}.
     */
    void actualizarEstado(EstadoDeEspacioVerde estadoNuevo) {
        this.estado = estadoNuevo;
        this.actualizadoEn = Instant.now();
    }
}
