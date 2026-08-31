package ar.com.ciudaddigital.desarrollosocial.internal;

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
 * Un programa social que este municipio ofrece (ADR 0025 §3): catálogo
 * institucional, sin ningún dato de una persona identificable, mismo
 * perfil de riesgo que {@code ObraPublicaEntity}/{@code ArbolUrbanoEntity}.
 *
 * <p>Vive en la base del municipio, sin columna de tenant. {@code
 * publicadoPorNombre} y {@code publicadoPorEmail} son una copia del actor
 * que publica el programa, no una relación JPA (ADR 0013), mismo
 * criterio que {@code ObraPublicaEntity}.
 *
 * <p>{@code estado} es el único campo que muta después de creado: un
 * municipio abre y cierra una convocatoria libremente en ambos sentidos
 * (ADR 0025 §3), a diferencia de Obras/Arbolado no hace falta una tabla
 * de transiciones — cualquiera de los dos valores es válido desde el
 * otro.
 */
@Entity
@Table(name = "programa_social")
class ProgramaSocialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column
    private String descripcion;

    @Column(name = "criterios_de_elegibilidad")
    private String criteriosDeElegibilidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoDePrograma estado;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected ProgramaSocialEntity() {
    }

    /** El estado inicial no es un parámetro: siempre nace {@code ABIERTO} (ADR 0025 §3). */
    static ProgramaSocialEntity publicar(String nombre, String descripcion, String criteriosDeElegibilidad,
            String publicadoPorNombre, String publicadoPorEmail) {

        ProgramaSocialEntity programa = new ProgramaSocialEntity();
        programa.nombre = nombre;
        programa.descripcion = descripcion;
        programa.criteriosDeElegibilidad = criteriosDeElegibilidad;
        programa.estado = EstadoDePrograma.ABIERTO;
        programa.publicadoPorNombre = publicadoPorNombre;
        programa.publicadoPorEmail = publicadoPorEmail;
        programa.creadoEn = Instant.now();
        programa.actualizadoEn = programa.creadoEn;
        return programa;
    }

    Long getId() {
        return id;
    }

    String getNombre() {
        return nombre;
    }

    String getDescripcion() {
        return descripcion;
    }

    String getCriteriosDeElegibilidad() {
        return criteriosDeElegibilidad;
    }

    EstadoDePrograma getEstado() {
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
     * Aplica el estado nuevo, ya validado por {@code GestionDeProgramasSociales}
     * (ambos sentidos son válidos, ADR 0025 §3), y actualiza {@code
     * actualizadoEn}.
     */
    void actualizarEstado(EstadoDePrograma estadoNuevo) {
        this.estado = estadoNuevo;
        this.actualizadoEn = Instant.now();
    }
}
