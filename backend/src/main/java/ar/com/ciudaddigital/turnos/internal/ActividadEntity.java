package ar.com.ciudaddigital.turnos.internal;

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
 * Una actividad municipal de deporte, cultura o turismo (ADR 0026 §1/§2):
 * catálogo público institucional, sin ningún dato de una persona
 * identificable, mismo perfil de riesgo que {@code ObraPublicaEntity}/
 * {@code ArbolUrbanoEntity}/{@code ProgramaSocialEntity}.
 *
 * <p>Vive en la base del municipio, sin columna de tenant. {@code
 * publicadoPorNombre} y {@code publicadoPorEmail} son una copia del actor
 * que publica la actividad, no una relación JPA (ADR 0013), mismo criterio
 * que {@code ObraPublicaEntity}.
 *
 * <p>{@code estado} es el único campo que muta después de creada: un
 * municipio activa e inactiva libremente en ambos sentidos (ADR 0026 §2,
 * mismo criterio que {@code EstadoDePrograma} en Desarrollo Social, ADR
 * 0025 §3) — no hace falta una tabla de transiciones.
 */
@Entity
@Table(name = "actividad")
class ActividadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDeActividad tipo;

    @Column
    private String descripcion;

    @Column(nullable = false, length = 300)
    private String ubicacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoDeActividad estado;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected ActividadEntity() {
    }

    /** El estado inicial no es un parámetro: siempre nace {@code ACTIVA} (ADR 0026 §2). */
    static ActividadEntity publicar(String nombre, TipoDeActividad tipo, String descripcion, String ubicacion,
            String publicadoPorNombre, String publicadoPorEmail) {

        ActividadEntity actividad = new ActividadEntity();
        actividad.nombre = nombre;
        actividad.tipo = tipo;
        actividad.descripcion = descripcion;
        actividad.ubicacion = ubicacion;
        actividad.estado = EstadoDeActividad.ACTIVA;
        actividad.publicadoPorNombre = publicadoPorNombre;
        actividad.publicadoPorEmail = publicadoPorEmail;
        actividad.creadoEn = Instant.now();
        actividad.actualizadoEn = actividad.creadoEn;
        return actividad;
    }

    Long getId() {
        return id;
    }

    String getNombre() {
        return nombre;
    }

    TipoDeActividad getTipo() {
        return tipo;
    }

    String getDescripcion() {
        return descripcion;
    }

    String getUbicacion() {
        return ubicacion;
    }

    EstadoDeActividad getEstado() {
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
     * Aplica el estado nuevo, ya validado por {@code GestionDeAgenda}
     * (ambos sentidos son válidos, ADR 0026 §2), y actualiza {@code
     * actualizadoEn}.
     */
    void actualizarEstado(EstadoDeActividad estadoNuevo) {
        this.estado = estadoNuevo;
        this.actualizadoEn = Instant.now();
    }
}
