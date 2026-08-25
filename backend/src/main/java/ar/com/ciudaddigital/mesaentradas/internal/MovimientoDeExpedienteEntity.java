package ar.com.ciudaddigital.mesaentradas.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Una fila del historial de un expediente: un cambio de estado, con quién
 * lo hizo y cuándo (ADR 0015 §2). El primer movimiento (el alta) tiene
 * {@code estadoAnterior}/{@code actorNombre}/{@code actorEmail} en
 * {@code null}: no hay estado previo, y el alta es pública y anónima
 * (ADR 0014 §1, reutilizado acá), sin actor autenticado que la firme.
 *
 * <p>{@code actorNombre}/{@code actorEmail} son una copia del actor al
 * momento del movimiento, no una relación JPA — mismo criterio "copia, no
 * referencia" que {@code publicado_por_*} (Boletín) y
 * {@code registrado_por_*} (Cementerio), ADR 0013.
 */
@Entity
@Table(name = "movimiento_de_expediente")
class MovimientoDeExpedienteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "expediente_id", nullable = false)
    private ExpedienteEntity expediente;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior")
    private EstadoDeExpediente estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false)
    private EstadoDeExpediente estadoNuevo;

    @Column(name = "actor_nombre", length = 150)
    private String actorNombre;

    @Column(name = "actor_email", length = 200)
    private String actorEmail;

    @Column
    private String comentario;

    @Column(nullable = false)
    private Instant fecha;

    protected MovimientoDeExpedienteEntity() {
    }

    /** Movimiento de alta: sin estado previo ni actor, el alta es pública y anónima (ADR 0014 §1). */
    static MovimientoDeExpedienteEntity deAlta(EstadoDeExpediente estadoInicial) {
        MovimientoDeExpedienteEntity movimiento = new MovimientoDeExpedienteEntity();
        movimiento.estadoAnterior = null;
        movimiento.estadoNuevo = estadoInicial;
        movimiento.actorNombre = null;
        movimiento.actorEmail = null;
        movimiento.comentario = null;
        movimiento.fecha = Instant.now();
        return movimiento;
    }

    static MovimientoDeExpedienteEntity deAvance(EstadoDeExpediente estadoAnterior, EstadoDeExpediente estadoNuevo,
            String actorNombre, String actorEmail, String comentario) {

        MovimientoDeExpedienteEntity movimiento = new MovimientoDeExpedienteEntity();
        movimiento.estadoAnterior = estadoAnterior;
        movimiento.estadoNuevo = estadoNuevo;
        movimiento.actorNombre = actorNombre;
        movimiento.actorEmail = actorEmail;
        movimiento.comentario = comentario;
        movimiento.fecha = Instant.now();
        return movimiento;
    }

    Long getId() {
        return id;
    }

    EstadoDeExpediente getEstadoAnterior() {
        return estadoAnterior;
    }

    EstadoDeExpediente getEstadoNuevo() {
        return estadoNuevo;
    }

    String getActorNombre() {
        return actorNombre;
    }

    String getActorEmail() {
        return actorEmail;
    }

    String getComentario() {
        return comentario;
    }

    Instant getFecha() {
        return fecha;
    }

    /**
     * Sin setter público: el lado dueño de la relación es
     * {@link ExpedienteEntity}, que fija este campo al agregar el
     * movimiento a su colección (patrón bidireccional estándar de JPA).
     */
    void fijarExpediente(ExpedienteEntity expediente) {
        this.expediente = expediente;
    }
}
