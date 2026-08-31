package ar.com.ciudaddigital.prensa.internal;

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
 * Una gacetilla de prensa publicada por este municipio (ADR 0027).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, igual que
 * {@code norma} y {@code registro_auditoria}. {@code publicadoPorNombre}
 * y {@code publicadoPorEmail} son una copia del actor al momento de
 * publicar, no una relación JPA —mismo criterio que
 * {@code NormaEntity}/{@code RegistroAuditoriaEntity}, ADR 0013—: es la
 * firma pública de la gacetilla, no un dato que tenga que seguir vivo si
 * ese usuario cambia de nombre o se desactiva después.
 *
 * <p>Sin {@code numero}, a diferencia de {@code NormaEntity}: una
 * gacetilla no es un acto legal con numeración correlativa (ADR 0027 §1).
 *
 * <p>Sin métodos de mutación a propósito: una vez publicada, esta
 * rebanada no edita ni borra una gacetilla. Es un registro público que se
 * corrige publicando una gacetilla nueva, no mutando la vieja.
 */
@Entity
@Table(name = "gacetilla")
class GacetillaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoriaDeGacetilla categoria;

    @Column(nullable = false, length = 300)
    private String titulo;

    @Column(nullable = false)
    private String texto;

    @Column(name = "fecha_publicacion", nullable = false)
    private LocalDate fechaPublicacion;

    @Column(name = "publicado_por_nombre", nullable = false, length = 150)
    private String publicadoPorNombre;

    @Column(name = "publicado_por_email", nullable = false, length = 200)
    private String publicadoPorEmail;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected GacetillaEntity() {
    }

    static GacetillaEntity nueva(CategoriaDeGacetilla categoria, String titulo, String texto,
            LocalDate fechaPublicacion, String publicadoPorNombre, String publicadoPorEmail) {

        GacetillaEntity gacetilla = new GacetillaEntity();
        gacetilla.categoria = categoria;
        gacetilla.titulo = titulo;
        gacetilla.texto = texto;
        gacetilla.fechaPublicacion = fechaPublicacion;
        gacetilla.publicadoPorNombre = publicadoPorNombre;
        gacetilla.publicadoPorEmail = publicadoPorEmail;
        gacetilla.creadoEn = Instant.now();
        return gacetilla;
    }

    Long getId() {
        return id;
    }

    CategoriaDeGacetilla getCategoria() {
        return categoria;
    }

    String getTitulo() {
        return titulo;
    }

    String getTexto() {
        return texto;
    }

    LocalDate getFechaPublicacion() {
        return fechaPublicacion;
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
