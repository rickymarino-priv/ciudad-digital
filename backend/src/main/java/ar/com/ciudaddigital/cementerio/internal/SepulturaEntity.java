package ar.com.ciudaddigital.cementerio.internal;

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
 * Un registro de inhumación en el cementerio municipal de este municipio
 * (backlog R8).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, igual que
 * {@code reclamo} (V6) y {@code norma} (V7). {@code registradoPorNombre} y
 * {@code registradoPorEmail} son una copia del actor al momento de
 * registrar, no una relación JPA —mismo criterio que
 * {@code publicadoPorNombre}/{@code publicadoPorEmail} en
 * {@code NormaEntity}, ADR 0013—.
 *
 * <p>{@code nombreTitular}, {@code contactoTitular} y
 * {@code observaciones} son privados: no se exponen en la búsqueda
 * pública (minimización de datos de terceros), solo en la respuesta del
 * alta a quien lo acaba de cargar.
 *
 * <p>Sin métodos de mutación a propósito: registrar una sepultura no
 * tiene estados ni transiciones, es un alta y listo, más simple todavía
 * que {@code reclamo}. Editar o borrar un registro ya cargado no entra en
 * esta rebanada.
 */
@Entity
@Table(name = "sepultura")
class SepulturaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_parcela", nullable = false)
    private TipoDeParcela tipoParcela;

    @Column(nullable = false, length = 100)
    private String sector;

    @Column(length = 50)
    private String fila;

    @Column(nullable = false, length = 50)
    private String numero;

    @Column(name = "nombre_difunto", nullable = false, length = 200)
    private String nombreDifunto;

    @Column(name = "fecha_fallecimiento", nullable = false)
    private LocalDate fechaFallecimiento;

    @Column(name = "fecha_inhumacion", nullable = false)
    private LocalDate fechaInhumacion;

    @Column(name = "nombre_titular", length = 200)
    private String nombreTitular;

    @Column(name = "contacto_titular", length = 200)
    private String contactoTitular;

    @Column
    private String observaciones;

    @Column(name = "registrado_por_nombre", nullable = false, length = 150)
    private String registradoPorNombre;

    @Column(name = "registrado_por_email", nullable = false, length = 200)
    private String registradoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected SepulturaEntity() {
    }

    static SepulturaEntity nueva(TipoDeParcela tipoParcela, String sector, String fila, String numero,
            String nombreDifunto, LocalDate fechaFallecimiento, LocalDate fechaInhumacion,
            String nombreTitular, String contactoTitular, String observaciones,
            String registradoPorNombre, String registradoPorEmail) {

        SepulturaEntity sepultura = new SepulturaEntity();
        sepultura.tipoParcela = tipoParcela;
        sepultura.sector = sector;
        sepultura.fila = fila;
        sepultura.numero = numero;
        sepultura.nombreDifunto = nombreDifunto;
        sepultura.fechaFallecimiento = fechaFallecimiento;
        sepultura.fechaInhumacion = fechaInhumacion;
        sepultura.nombreTitular = nombreTitular;
        sepultura.contactoTitular = contactoTitular;
        sepultura.observaciones = observaciones;
        sepultura.registradoPorNombre = registradoPorNombre;
        sepultura.registradoPorEmail = registradoPorEmail;
        sepultura.creadoEn = Instant.now();
        return sepultura;
    }

    Long getId() {
        return id;
    }

    TipoDeParcela getTipoParcela() {
        return tipoParcela;
    }

    String getSector() {
        return sector;
    }

    String getFila() {
        return fila;
    }

    String getNumero() {
        return numero;
    }

    String getNombreDifunto() {
        return nombreDifunto;
    }

    LocalDate getFechaFallecimiento() {
        return fechaFallecimiento;
    }

    LocalDate getFechaInhumacion() {
        return fechaInhumacion;
    }

    String getNombreTitular() {
        return nombreTitular;
    }

    String getContactoTitular() {
        return contactoTitular;
    }

    String getObservaciones() {
        return observaciones;
    }

    String getRegistradoPorNombre() {
        return registradoPorNombre;
    }

    String getRegistradoPorEmail() {
        return registradoPorEmail;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }
}
