package ar.com.ciudaddigital.bromatologia.internal;

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
 * Una inspección registrada sobre un comercio de este municipio (ADR 0032
 * §3): un registro de auditoría de lo que un inspector constató en una
 * fecha, no un acto administrativo con efectos jurídicos propios (sin acta
 * ni infracción, ver el package-info del módulo).
 *
 * <p>{@code comercioId} es una clave foránea de base de datos hacia
 * {@code comercio_bromatologico} (ver la migración), pero acá vive como un
 * {@code Long} simple, no como una relación JPA: no hace falta navegar
 * desde la inspección hacia el comercio en memoria, y así se evita el
 * costo de mantener un lado bidireccional de la relación. La validación de
 * que el comercio exista en este tenant la hace
 * {@code GestionDeBromatologia} antes de construir esta entidad.
 * {@code inspeccionadoPorNombre}/{@code inspeccionadoPorEmail} son una
 * copia del actor que la registra, no una relación JPA (ADR 0013).
 *
 * <p>Sin {@code actualizadoEn} ni ningún método de edición: es append-only,
 * el primer registro del proyecto con esta forma, no se edita ni se borra
 * después de creada (ADR 0032 §3).
 */
@Entity
@Table(name = "inspeccion_bromatologica")
class InspeccionBromatologicaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comercio_id", nullable = false)
    private Long comercioId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoBromatologico resultado;

    @Column
    private String observaciones;

    @Column(name = "inspeccionado_por_nombre", nullable = false, length = 150)
    private String inspeccionadoPorNombre;

    @Column(name = "inspeccionado_por_email", nullable = false, length = 200)
    private String inspeccionadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected InspeccionBromatologicaEntity() {
    }

    static InspeccionBromatologicaEntity registrar(Long comercioId, LocalDate fecha, EstadoBromatologico resultado,
            String observaciones, String inspeccionadoPorNombre, String inspeccionadoPorEmail) {

        InspeccionBromatologicaEntity inspeccion = new InspeccionBromatologicaEntity();
        inspeccion.comercioId = comercioId;
        inspeccion.fecha = fecha;
        inspeccion.resultado = resultado;
        inspeccion.observaciones = observaciones;
        inspeccion.inspeccionadoPorNombre = inspeccionadoPorNombre;
        inspeccion.inspeccionadoPorEmail = inspeccionadoPorEmail;
        inspeccion.creadoEn = Instant.now();
        return inspeccion;
    }

    Long getId() {
        return id;
    }

    Long getComercioId() {
        return comercioId;
    }

    LocalDate getFecha() {
        return fecha;
    }

    EstadoBromatologico getResultado() {
        return resultado;
    }

    String getObservaciones() {
        return observaciones;
    }

    String getInspeccionadoPorNombre() {
        return inspeccionadoPorNombre;
    }

    String getInspeccionadoPorEmail() {
        return inspeccionadoPorEmail;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }
}
