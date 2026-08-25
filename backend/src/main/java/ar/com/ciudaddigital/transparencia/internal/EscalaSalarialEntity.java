package ar.com.ciudaddigital.transparencia.internal;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una entrada de escala salarial publicada por este municipio en
 * Transparencia Activa: cargo/función y monto, NUNCA una persona (backlog
 * R11).
 *
 * <p>A diferencia de {@code SepulturaEntity} (V8), donde el dato privado se
 * guarda y se oculta en la búsqueda pública, acá el dato de persona
 * directamente no existe como columna: es una decisión de modelo, no de
 * presentación (ver la spec de R11 para el razonamiento completo).
 * {@code publicadoPorNombre}/{@code publicadoPorEmail} sí identifican a
 * quien publicó el registro —copia del actor al momento de publicar, mismo
 * criterio que {@code NormaEntity}, ADR 0013—: es la firma pública del acto
 * administrativo de publicar, no el dato salarial de un tercero.
 *
 * <p>Sin métodos de mutación a propósito, mismo criterio que
 * {@code PartidaPresupuestariaEntity}.
 */
@Entity
@Table(name = "escala_salarial")
class EscalaSalarialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false, length = 150)
    private String area;

    @Column(nullable = false, length = 200)
    private String cargo;

    @Column(name = "cantidad_cargos", nullable = false)
    private Integer cantidadCargos;

    @Column(name = "monto_bruto_mensual", nullable = false)
    private BigDecimal montoBrutoMensual;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected EscalaSalarialEntity() {
    }

    static EscalaSalarialEntity nueva(Integer anio, String area, String cargo, Integer cantidadCargos,
            BigDecimal montoBrutoMensual, String publicadoPorNombre, String publicadoPorEmail) {

        EscalaSalarialEntity escala = new EscalaSalarialEntity();
        escala.anio = anio;
        escala.area = area;
        escala.cargo = cargo;
        escala.cantidadCargos = cantidadCargos;
        escala.montoBrutoMensual = montoBrutoMensual;
        escala.publicadoPorNombre = publicadoPorNombre;
        escala.publicadoPorEmail = publicadoPorEmail;
        escala.creadoEn = Instant.now();
        return escala;
    }

    Long getId() {
        return id;
    }

    Integer getAnio() {
        return anio;
    }

    String getArea() {
        return area;
    }

    String getCargo() {
        return cargo;
    }

    Integer getCantidadCargos() {
        return cantidadCargos;
    }

    BigDecimal getMontoBrutoMensual() {
        return montoBrutoMensual;
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
}
