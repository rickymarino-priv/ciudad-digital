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
 * Un recurso de Defensa Civil (refugio, punto de encuentro, centro de
 * acopio) registrado por este municipio (ADR 0031 §5).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, mismo criterio
 * que {@code AlertaDeDefensaCivilEntity}. Sin clave foránea hacia
 * {@code AlertaDeDefensaCivilEntity}: son dos catálogos independientes
 * que comparten pantalla por afinidad de dominio, no por relación de
 * datos (ADR 0031 §1). {@code publicadoPorNombre} y
 * {@code publicadoPorEmail} son una copia del actor que registra el
 * recurso, no una relación JPA (ADR 0013).
 *
 * <p>Esta entidad sí muta después de creada, pero solo su {@code estado}
 * operativo, con transición libre en ambos sentidos ({@code ACTIVO} ↔
 * {@code INACTIVO}, ADR 0031 §5): {@code tipo}, {@code nombre},
 * {@code direccion}, {@code capacidad}, {@code telefonoContacto} y
 * {@code descripcion} no tienen ningún método de edición a propósito. La
 * validación de que el estado nuevo sea distinto del actual vive en
 * {@code GestionDeRecursos}, no acá: esta entidad no decide, solo aplica
 * el cambio ya validado (mismo criterio que {@code ProgramaSocialEntity#actualizarEstado}).
 */
@Entity
@Table(name = "recurso_defensa_civil")
class RecursoDeDefensaCivilEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDeRecurso tipo;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false, length = 300)
    private String direccion;

    @Column
    private Integer capacidad;

    @Column(name = "telefono_contacto", length = 50)
    private String telefonoContacto;

    @Column
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoDeRecurso estado;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected RecursoDeDefensaCivilEntity() {
    }

    /** El estado inicial no es un parámetro: siempre nace {@code ACTIVO} (ADR 0031 §5). */
    static RecursoDeDefensaCivilEntity registrar(TipoDeRecurso tipo, String nombre, String direccion,
            Integer capacidad, String telefonoContacto, String descripcion,
            String publicadoPorNombre, String publicadoPorEmail) {

        RecursoDeDefensaCivilEntity recurso = new RecursoDeDefensaCivilEntity();
        recurso.tipo = tipo;
        recurso.nombre = nombre;
        recurso.direccion = direccion;
        recurso.capacidad = capacidad;
        recurso.telefonoContacto = telefonoContacto;
        recurso.descripcion = descripcion;
        recurso.estado = EstadoDeRecurso.ACTIVO;
        recurso.publicadoPorNombre = publicadoPorNombre;
        recurso.publicadoPorEmail = publicadoPorEmail;
        recurso.creadoEn = Instant.now();
        recurso.actualizadoEn = recurso.creadoEn;
        return recurso;
    }

    Long getId() {
        return id;
    }

    TipoDeRecurso getTipo() {
        return tipo;
    }

    String getNombre() {
        return nombre;
    }

    String getDireccion() {
        return direccion;
    }

    Integer getCapacidad() {
        return capacidad;
    }

    String getTelefonoContacto() {
        return telefonoContacto;
    }

    String getDescripcion() {
        return descripcion;
    }

    EstadoDeRecurso getEstado() {
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
     * Aplica el estado nuevo, ya validado por {@code GestionDeRecursos}
     * como distinto del actual (ADR 0031 §5), y actualiza
     * {@code actualizadoEn}.
     */
    void actualizarEstado(EstadoDeRecurso estadoNuevo) {
        this.estado = estadoNuevo;
        this.actualizadoEn = Instant.now();
    }
}
