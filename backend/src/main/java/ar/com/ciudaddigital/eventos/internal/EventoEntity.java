package ar.com.ciudaddigital.eventos.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un evento de la agenda cultural, turística o deportiva publicado por
 * este municipio (ADR 0030).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, mismo criterio
 * que {@code ObraPublicaEntity}/{@code EspacioVerdeEntity}.
 * {@code publicadoPorNombre} y {@code publicadoPorEmail} son una copia del
 * actor que publica el evento, no una relación JPA (ADR 0013), mismo
 * criterio que {@code publicadoPorNombre}/{@code publicadoPorEmail} en
 * {@code ObraPublicaEntity}/{@code EspacioVerdeEntity}.
 *
 * <p>Esta entidad sí muta después de creada, pero solo su {@code estado},
 * con un único salto sin retorno ({@code PROGRAMADO → CANCELADO}, ADR 0030
 * §3): {@code nombre}, {@code categoria}, {@code ubicacion},
 * {@code descripcion}, las fechas y {@code horaInicio} no tienen ningún
 * método de edición a propósito. La validación de que la transición
 * pedida es la única válida vive en {@code GestionDeEventos}, no acá: esta
 * entidad no decide, solo aplica el cambio ya validado (mismo criterio que
 * {@code EspacioVerdeEntity#actualizarEstado}).
 */
@Entity
@Table(name = "evento")
class EventoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaDeEvento categoria;

    @Column(nullable = false, length = 300)
    private String ubicacion;

    @Column
    private String descripcion;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDeEvento estado;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected EventoEntity() {
    }

    /** El estado inicial no es un parámetro: siempre nace {@code PROGRAMADO} (ADR 0030 §3). */
    static EventoEntity publicar(String nombre, CategoriaDeEvento categoria, String ubicacion, String descripcion,
            LocalDate fechaInicio, LocalDate fechaFin, LocalTime horaInicio,
            String publicadoPorNombre, String publicadoPorEmail) {

        EventoEntity evento = new EventoEntity();
        evento.nombre = nombre;
        evento.categoria = categoria;
        evento.ubicacion = ubicacion;
        evento.descripcion = descripcion;
        evento.fechaInicio = fechaInicio;
        evento.fechaFin = fechaFin;
        evento.horaInicio = horaInicio;
        evento.estado = EstadoDeEvento.PROGRAMADO;
        evento.publicadoPorNombre = publicadoPorNombre;
        evento.publicadoPorEmail = publicadoPorEmail;
        evento.creadoEn = Instant.now();
        evento.actualizadoEn = evento.creadoEn;
        return evento;
    }

    Long getId() {
        return id;
    }

    String getNombre() {
        return nombre;
    }

    CategoriaDeEvento getCategoria() {
        return categoria;
    }

    String getUbicacion() {
        return ubicacion;
    }

    String getDescripcion() {
        return descripcion;
    }

    LocalDate getFechaInicio() {
        return fechaInicio;
    }

    LocalDate getFechaFin() {
        return fechaFin;
    }

    LocalTime getHoraInicio() {
        return horaInicio;
    }

    EstadoDeEvento getEstado() {
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
     * Cancela el evento, ya validado por {@code GestionDeEventos} como la
     * única transición posible desde {@code PROGRAMADO} (ADR 0030 §3), y
     * actualiza {@code actualizadoEn}. Sin parámetro de estado nuevo: no
     * hace falta, {@code CANCELADO} es el único destino posible.
     */
    void cancelar() {
        this.estado = EstadoDeEvento.CANCELADO;
        this.actualizadoEn = Instant.now();
    }
}
