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
 * Un comercio del padrón bromatológico de este municipio (ADR 0032 §2):
 * público, sin campo de titular, razón social o CUIT.
 *
 * <p>Vive en la base del municipio, sin columna de tenant.
 * {@code publicadoPorNombre}/{@code publicadoPorEmail} son una copia del
 * actor que registra el comercio, no una relación JPA (ADR 0013).
 *
 * <p>{@code nombre}, {@code rubro}, {@code direccion},
 * {@code fechaHabilitacion} y {@code fechaVencimientoHabilitacion} no
 * tienen ningún método de edición a propósito: no existe edición del alta
 * después de creado en esta rebanada (ADR 0032, Pendiente de definir). El
 * único campo que muta después del alta es {@code estado}, y no con un
 * {@code PATCH} directo sino como efecto de registrar una inspección
 * ({@link #actualizarEstado}, ver {@code GestionDeBromatologia}): a
 * diferencia de todo el resto del proyecto con estado propio (Obras,
 * Arbolado, Espacios Verdes, Recursos de Defensa Civil), acá el cambio de
 * estado siempre queda acompañado de quién lo constató, cuándo y por qué
 * (ADR 0032 §2).
 */
@Entity
@Table(name = "comercio_bromatologico")
class ComercioBromatologicoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RubroBromatologico rubro;

    @Column(nullable = false, length = 300)
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoBromatologico estado;

    @Column(name = "fecha_habilitacion", nullable = false)
    private LocalDate fechaHabilitacion;

    @Column(name = "fecha_vencimiento_habilitacion", nullable = false)
    private LocalDate fechaVencimientoHabilitacion;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected ComercioBromatologicoEntity() {
    }

    /** El estado inicial no es un parámetro: siempre nace {@code HABILITADO} (ADR 0032 §2). */
    static ComercioBromatologicoEntity registrar(String nombre, RubroBromatologico rubro, String direccion,
            LocalDate fechaHabilitacion, LocalDate fechaVencimientoHabilitacion,
            String publicadoPorNombre, String publicadoPorEmail) {

        ComercioBromatologicoEntity comercio = new ComercioBromatologicoEntity();
        comercio.nombre = nombre;
        comercio.rubro = rubro;
        comercio.direccion = direccion;
        comercio.estado = EstadoBromatologico.HABILITADO;
        comercio.fechaHabilitacion = fechaHabilitacion;
        comercio.fechaVencimientoHabilitacion = fechaVencimientoHabilitacion;
        comercio.publicadoPorNombre = publicadoPorNombre;
        comercio.publicadoPorEmail = publicadoPorEmail;
        comercio.creadoEn = Instant.now();
        comercio.actualizadoEn = comercio.creadoEn;
        return comercio;
    }

    Long getId() {
        return id;
    }

    String getNombre() {
        return nombre;
    }

    RubroBromatologico getRubro() {
        return rubro;
    }

    String getDireccion() {
        return direccion;
    }

    EstadoBromatologico getEstado() {
        return estado;
    }

    LocalDate getFechaHabilitacion() {
        return fechaHabilitacion;
    }

    LocalDate getFechaVencimientoHabilitacion() {
        return fechaVencimientoHabilitacion;
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
     * Aplica el estado nuevo como efecto de registrar una inspección
     * ({@code GestionDeBromatologia#registrarInspeccion}), y actualiza
     * {@code actualizadoEn}. Sin validación acá de que sea distinto del
     * actual: a diferencia de {@code RecursoDeDefensaCivilEntity}, una
     * reinspección de rutina con el mismo resultado es válida (ADR 0032
     * §3).
     */
    void actualizarEstado(EstadoBromatologico estadoNuevo) {
        this.estado = estadoNuevo;
        this.actualizadoEn = Instant.now();
    }
}
