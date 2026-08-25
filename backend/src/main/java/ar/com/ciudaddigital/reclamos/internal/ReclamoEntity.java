package ar.com.ciudaddigital.reclamos.internal;

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
 * Un reclamo cargado por un vecino de este municipio (ADR 0014).
 *
 * <p>Vive en la base del municipio: no hay columna de tenant porque no
 * hace falta, la base ya es la del municipio. {@code nombreContacto} y
 * {@code contacto} son texto libre, opcional y sin verificar —no hay
 * cuenta detrás que los respalde—, se guardan solo para que el municipio
 * pueda volver a contactar al vecino (ADR 0014 §4). {@code tokenHash} es el
 * hash SHA-256 del token de seguimiento anónimo (ADR 0017): el token en
 * claro nunca llega a esta entidad, ni tiene getter que lo exponga.
 */
@Entity
@Table(name = "reclamo")
class ReclamoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaReclamo categoria;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false, length = 300)
    private String direccion;

    @Column(name = "nombre_contacto", length = 150)
    private String nombreContacto;

    @Column(length = 200)
    private String contacto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReclamo estado;

    @Column(name = "comentario_gestion")
    private String comentarioGestion;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    protected ReclamoEntity() {
    }

    /**
     * {@code tokenHash} llega ya calculado: esta entidad no depende de
     * {@code seguimientoanonimo}, es {@code GestionDeReclamos} quien genera
     * el token y calcula su hash (ADR 0017 §4). El token en claro nunca
     * llega hasta acá.
     */
    static ReclamoEntity nuevo(CategoriaReclamo categoria, String descripcion, String direccion,
            String nombreContacto, String contacto, String tokenHash) {

        ReclamoEntity reclamo = new ReclamoEntity();
        reclamo.categoria = categoria;
        reclamo.descripcion = descripcion;
        reclamo.direccion = direccion;
        reclamo.nombreContacto = nombreContacto;
        reclamo.contacto = contacto;
        reclamo.estado = EstadoReclamo.NUEVO;
        reclamo.creadoEn = Instant.now();
        reclamo.actualizadoEn = reclamo.creadoEn;
        reclamo.tokenHash = tokenHash;
        return reclamo;
    }

    Long getId() {
        return id;
    }

    CategoriaReclamo getCategoria() {
        return categoria;
    }

    String getDescripcion() {
        return descripcion;
    }

    String getDireccion() {
        return direccion;
    }

    String getNombreContacto() {
        return nombreContacto;
    }

    String getContacto() {
        return contacto;
    }

    EstadoReclamo getEstado() {
        return estado;
    }

    String getComentarioGestion() {
        return comentarioGestion;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }

    Instant getActualizadoEn() {
        return actualizadoEn;
    }

    /**
     * Fija el estado nuevo y, si viene, el comentario de gestión. No valida
     * la transición: esa tabla vive en {@link GestionDeReclamos}, no acá
     * (ADR 0014 §3) — esta entidad no sabe qué transiciones son válidas, solo
     * aplica la que ya fue validada.
     */
    void cambiarEstado(EstadoReclamo nuevoEstado, String comentario) {
        this.estado = nuevoEstado;
        this.actualizadoEn = Instant.now();
        if (comentario != null && !comentario.isBlank()) {
            this.comentarioGestion = comentario;
        }
    }
}
