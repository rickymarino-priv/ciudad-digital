package ar.com.ciudaddigital.arbolado.internal;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un árbol urbano registrado por este municipio (ADR 0024).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, mismo criterio
 * que {@code ObraPublicaEntity}/{@code ReclamoEntity}. {@code publicadoPorNombre}
 * y {@code publicadoPorEmail} son una copia del actor que registra el
 * árbol, no una relación JPA (ADR 0013), mismo criterio que
 * {@code publicadoPorNombre}/{@code publicadoPorEmail} en
 * {@code ObraPublicaEntity}.
 *
 * <p>Esta entidad sí muta después de creada, pero solo su {@code estado}
 * sanitario (ADR 0024 §4): {@code especie}, {@code ubicacion},
 * {@code descripcion} y {@code fechaDePlantacion} no tienen ningún método
 * de edición a propósito. La validación de qué transición de estado es
 * válida vive en {@code GestionDeArbolado}, no acá: esta entidad no sabe
 * qué transiciones son válidas, solo aplica la que ya fue validada (mismo
 * criterio que {@code ObraPublicaEntity#actualizarEstado}).
 */
@Entity
@Table(name = "arbol_urbano")
class ArbolUrbanoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String especie;

    @Column(nullable = false, length = 300)
    private String ubicacion;

    @Column
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoDeArbol estado;

    @Column(name = "fecha_de_plantacion")
    private LocalDate fechaDePlantacion;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected ArbolUrbanoEntity() {
    }

    /** El estado inicial no es un parámetro: siempre nace {@code PLANTADO} (ADR 0024 §4). */
    static ArbolUrbanoEntity registrar(String especie, String ubicacion, String descripcion,
            LocalDate fechaDePlantacion, String publicadoPorNombre, String publicadoPorEmail) {

        ArbolUrbanoEntity arbol = new ArbolUrbanoEntity();
        arbol.especie = especie;
        arbol.ubicacion = ubicacion;
        arbol.descripcion = descripcion;
        arbol.estado = EstadoDeArbol.PLANTADO;
        arbol.fechaDePlantacion = fechaDePlantacion;
        arbol.publicadoPorNombre = publicadoPorNombre;
        arbol.publicadoPorEmail = publicadoPorEmail;
        arbol.creadoEn = Instant.now();
        arbol.actualizadoEn = arbol.creadoEn;
        return arbol;
    }

    Long getId() {
        return id;
    }

    String getEspecie() {
        return especie;
    }

    String getUbicacion() {
        return ubicacion;
    }

    String getDescripcion() {
        return descripcion;
    }

    EstadoDeArbol getEstado() {
        return estado;
    }

    LocalDate getFechaDePlantacion() {
        return fechaDePlantacion;
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
     * Aplica el estado nuevo, ya validado por {@code GestionDeArbolado}
     * contra la tabla de transiciones (ADR 0024 §4), y actualiza
     * {@code actualizadoEn}.
     */
    void actualizarEstado(EstadoDeArbol estadoNuevo) {
        this.estado = estadoNuevo;
        this.actualizadoEn = Instant.now();
    }
}
