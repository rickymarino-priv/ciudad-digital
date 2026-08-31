package ar.com.ciudaddigital.turnos.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una reserva pública anónima de un vecino sobre una franja horaria de
 * este municipio (ADR 0026 §4). Vive en la base del municipio, sin
 * columna de tenant.
 *
 * <p>{@code contacto} es obligatorio, mismo criterio que {@code contacto}
 * en {@code InscripcionSocialEntity} (ADR 0025 §4): el municipio necesita
 * poder avisar si la actividad se reprograma o cancela.
 *
 * <p>La restricción {@code unique (franja_id, dni_solicitante)} de la
 * base (V22) es la barrera real contra la reserva duplicada bajo
 * concurrencia; el chequeo temprano en {@code GestionDeReservas#reservar}
 * es solo una salida rápida para el caso común, sin carrera (ADR 0026
 * §4).
 *
 * <p>Sin estado ni cancelación (ADR 0026 §8): esta entidad no tiene
 * ningún método de mutación más allá del alta.
 */
@Entity
@Table(name = "turno")
class TurnoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "franja_id", nullable = false)
    private Long franjaId;

    @Column(name = "nombre_solicitante", nullable = false, length = 150)
    private String nombreSolicitante;

    @Column(name = "dni_solicitante", nullable = false, length = 20)
    private String dniSolicitante;

    @Column(nullable = false, length = 200)
    private String contacto;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected TurnoEntity() {
    }

    static TurnoEntity reservar(Long franjaId, String nombreSolicitante, String dniSolicitante, String contacto) {
        TurnoEntity turno = new TurnoEntity();
        turno.franjaId = franjaId;
        turno.nombreSolicitante = nombreSolicitante;
        turno.dniSolicitante = dniSolicitante;
        turno.contacto = contacto;
        turno.creadoEn = Instant.now();
        return turno;
    }

    Long getId() {
        return id;
    }

    Long getFranjaId() {
        return franjaId;
    }

    String getNombreSolicitante() {
        return nombreSolicitante;
    }

    String getDniSolicitante() {
        return dniSolicitante;
    }

    String getContacto() {
        return contacto;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }
}
