package ar.com.ciudaddigital.educacion.internal;

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
 * Una institución educativa municipal registrada por este municipio (ADR
 * 0028).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, mismo criterio
 * que {@code ObraPublicaEntity}/{@code ArbolUrbanoEntity}.
 * {@code publicadoPorNombre} y {@code publicadoPorEmail} son una copia del
 * actor que registra la institución, no una relación JPA (ADR 0013),
 * mismo criterio que {@code publicadoPorNombre}/{@code publicadoPorEmail}
 * en {@code ObraPublicaEntity}.
 *
 * <p>A diferencia de {@code ObraPublicaEntity}/{@code ArbolUrbanoEntity},
 * no tiene ningún campo de fecha propio. Esta entidad sí muta después de
 * creada, pero solo su {@code estado} (ADR 0028 §4): {@code nombre},
 * {@code tipo}, {@code ubicacion} y {@code descripcion} no tienen ningún
 * método de edición a propósito. La validación de qué transición de
 * estado es válida vive en {@code GestionDeEducacion}, no acá: esta
 * entidad no sabe qué transiciones son válidas, solo aplica la que ya fue
 * validada (mismo criterio que {@code ObraPublicaEntity#actualizarEstado}).
 */
@Entity
@Table(name = "institucion_educativa")
class InstitucionEducativaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 35)
    private TipoDeInstitucionEducativa tipo;

    @Column(nullable = false, length = 300)
    private String ubicacion;

    @Column
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoDeInstitucion estado;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected InstitucionEducativaEntity() {
    }

    /** El estado inicial no es un parámetro: siempre nace {@code ACTIVA} (ADR 0028 §4). */
    static InstitucionEducativaEntity registrar(String nombre, TipoDeInstitucionEducativa tipo, String ubicacion,
            String descripcion, String publicadoPorNombre, String publicadoPorEmail) {

        InstitucionEducativaEntity institucion = new InstitucionEducativaEntity();
        institucion.nombre = nombre;
        institucion.tipo = tipo;
        institucion.ubicacion = ubicacion;
        institucion.descripcion = descripcion;
        institucion.estado = EstadoDeInstitucion.ACTIVA;
        institucion.publicadoPorNombre = publicadoPorNombre;
        institucion.publicadoPorEmail = publicadoPorEmail;
        institucion.creadoEn = Instant.now();
        institucion.actualizadoEn = institucion.creadoEn;
        return institucion;
    }

    Long getId() {
        return id;
    }

    String getNombre() {
        return nombre;
    }

    TipoDeInstitucionEducativa getTipo() {
        return tipo;
    }

    String getUbicacion() {
        return ubicacion;
    }

    String getDescripcion() {
        return descripcion;
    }

    EstadoDeInstitucion getEstado() {
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
     * Aplica el estado nuevo, ya validado por {@code GestionDeEducacion}
     * contra la tabla de transiciones (ADR 0028 §4), y actualiza
     * {@code actualizadoEn}.
     */
    void actualizarEstado(EstadoDeInstitucion estadoNuevo) {
        this.estado = estadoNuevo;
        this.actualizadoEn = Instant.now();
    }
}
