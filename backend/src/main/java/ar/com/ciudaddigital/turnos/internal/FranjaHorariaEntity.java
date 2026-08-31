package ar.com.ciudaddigital.turnos.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una franja horaria puntual de una actividad municipal, con cupo (ADR
 * 0026 §3). Vive en la base del municipio, sin columna de tenant.
 *
 * <p>{@code cupoDisponible} se inicializa igual a {@code cupoTotal} al
 * crearse (ver {@link #crear}) — la única vez que se escribe directo. De
 * ahí en más, solo lo modifica el {@code UPDATE} condicional atómico de
 * {@code FranjaHorariaRepository#reservarUnLugar} (ADR 0026 §4): esta
 * clase no tiene ningún método que lo mute, a propósito, para que no haya
 * ningún camino en el código de aplicación que lo escriba salteando ese
 * mecanismo.
 *
 * <p>Sin {@code actualizadoEn}: esta rebanada no edita una franja ya
 * creada (ADR 0026 §3).
 */
@Entity
@Table(name = "franja_horaria")
class FranjaHorariaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actividad_id", nullable = false)
    private Long actividadId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "cupo_total", nullable = false)
    private Integer cupoTotal;

    @Column(name = "cupo_disponible", nullable = false)
    private Integer cupoDisponible;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected FranjaHorariaEntity() {
    }

    /** {@code cupoDisponible} nace igual a {@code cupoTotal}: no es un parámetro (ADR 0026 §3). */
    static FranjaHorariaEntity crear(
            Long actividadId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, Integer cupoTotal) {

        FranjaHorariaEntity franja = new FranjaHorariaEntity();
        franja.actividadId = actividadId;
        franja.fecha = fecha;
        franja.horaInicio = horaInicio;
        franja.horaFin = horaFin;
        franja.cupoTotal = cupoTotal;
        franja.cupoDisponible = cupoTotal;
        franja.creadoEn = Instant.now();
        return franja;
    }

    Long getId() {
        return id;
    }

    Long getActividadId() {
        return actividadId;
    }

    LocalDate getFecha() {
        return fecha;
    }

    LocalTime getHoraInicio() {
        return horaInicio;
    }

    LocalTime getHoraFin() {
        return horaFin;
    }

    Integer getCupoTotal() {
        return cupoTotal;
    }

    Integer getCupoDisponible() {
        return cupoDisponible;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }
}
