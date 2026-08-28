package ar.com.ciudaddigital.obras.internal;

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
 * Una obra pública en curso registrada por este municipio (ADR 0023).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, mismo criterio
 * que {@code NormaEntity}/{@code ReclamoEntity}. {@code publicadoPorNombre}
 * y {@code publicadoPorEmail} son una copia del actor que registra la
 * obra, no una relación JPA (ADR 0013), mismo criterio que
 * {@code publicadoPorNombre}/{@code publicadoPorEmail} en
 * {@code NormaEntity}.
 *
 * <p>A diferencia de {@code NormaEntity}, esta entidad sí muta después de
 * creada, pero solo su {@code estado} (ADR 0023 §4): {@code nombre},
 * {@code tipo}, {@code ubicacion}, {@code descripcion} y las fechas
 * estimadas no tienen ningún método de edición a propósito. La validación
 * de qué transición de estado es válida vive en {@code GestionDeObras}, no
 * acá: esta entidad no sabe qué transiciones son válidas, solo aplica la
 * que ya fue validada (mismo criterio que {@code ReclamoEntity#cambiarEstado}).
 */
@Entity
@Table(name = "obra_publica")
class ObraPublicaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDeObra tipo;

    @Column(nullable = false, length = 300)
    private String ubicacion;

    @Column
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDeObra estado;

    @Column(name = "fecha_inicio_estimada")
    private LocalDate fechaInicioEstimada;

    @Column(name = "fecha_fin_estimada")
    private LocalDate fechaFinEstimada;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected ObraPublicaEntity() {
    }

    /** El estado inicial no es un parámetro: siempre nace {@code PLANIFICADA} (ADR 0023 §3). */
    static ObraPublicaEntity registrar(String nombre, TipoDeObra tipo, String ubicacion, String descripcion,
            LocalDate fechaInicioEstimada, LocalDate fechaFinEstimada,
            String publicadoPorNombre, String publicadoPorEmail) {

        ObraPublicaEntity obra = new ObraPublicaEntity();
        obra.nombre = nombre;
        obra.tipo = tipo;
        obra.ubicacion = ubicacion;
        obra.descripcion = descripcion;
        obra.estado = EstadoDeObra.PLANIFICADA;
        obra.fechaInicioEstimada = fechaInicioEstimada;
        obra.fechaFinEstimada = fechaFinEstimada;
        obra.publicadoPorNombre = publicadoPorNombre;
        obra.publicadoPorEmail = publicadoPorEmail;
        obra.creadoEn = Instant.now();
        obra.actualizadoEn = obra.creadoEn;
        return obra;
    }

    Long getId() {
        return id;
    }

    String getNombre() {
        return nombre;
    }

    TipoDeObra getTipo() {
        return tipo;
    }

    String getUbicacion() {
        return ubicacion;
    }

    String getDescripcion() {
        return descripcion;
    }

    EstadoDeObra getEstado() {
        return estado;
    }

    LocalDate getFechaInicioEstimada() {
        return fechaInicioEstimada;
    }

    LocalDate getFechaFinEstimada() {
        return fechaFinEstimada;
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
     * Aplica el estado nuevo, ya validado por {@code GestionDeObras} contra
     * la tabla de transiciones (ADR 0023 §3), y actualiza
     * {@code actualizadoEn}.
     */
    void actualizarEstado(EstadoDeObra estadoNuevo) {
        this.estado = estadoNuevo;
        this.actualizadoEn = Instant.now();
    }
}
