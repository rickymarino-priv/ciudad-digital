package ar.com.ciudaddigital.auditoria.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una fila del registro de auditoría del municipio: quién hizo qué y
 * cuándo (ADR 0013 §3).
 *
 * <p>{@code actorNombre} y {@code actorEmail} son una copia del dato al
 * momento del hecho, no un join contra el usuario: si el actor cambia de
 * nombre o se desactiva después, esta fila no cambia con él. Por el mismo
 * motivo {@code actorId} no es una relación JPA — es informativo, no
 * referencial.
 */
@Entity
@Table(name = "registro_auditoria")
class RegistroAuditoriaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_nombre", nullable = false, length = 150)
    private String actorNombre;

    @Column(name = "actor_email", nullable = false, length = 200)
    private String actorEmail;

    @Column(nullable = false, length = 100)
    private String accion;

    @Column(name = "entidad_tipo", nullable = false, length = 60)
    private String entidadTipo;

    @Column(name = "entidad_id", nullable = false, length = 100)
    private String entidadId;

    @Column(nullable = false)
    private String detalle;

    protected RegistroAuditoriaEntity() {
    }

    static RegistroAuditoriaEntity nueva(Long actorId, String actorNombre, String actorEmail,
            String accion, String entidadTipo, String entidadId, String detalle) {

        RegistroAuditoriaEntity registro = new RegistroAuditoriaEntity();
        registro.ocurridoEn = Instant.now();
        registro.actorId = actorId;
        registro.actorNombre = actorNombre;
        registro.actorEmail = actorEmail;
        registro.accion = accion;
        registro.entidadTipo = entidadTipo;
        registro.entidadId = entidadId;
        registro.detalle = detalle;
        return registro;
    }

    Long getId() {
        return id;
    }

    Instant getOcurridoEn() {
        return ocurridoEn;
    }

    Long getActorId() {
        return actorId;
    }

    String getActorNombre() {
        return actorNombre;
    }

    String getActorEmail() {
        return actorEmail;
    }

    String getAccion() {
        return accion;
    }

    String getEntidadTipo() {
        return entidadTipo;
    }

    String getEntidadId() {
        return entidadId;
    }

    String getDetalle() {
        return detalle;
    }
}
