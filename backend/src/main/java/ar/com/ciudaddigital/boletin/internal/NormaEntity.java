package ar.com.ciudaddigital.boletin.internal;

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
 * Una norma publicada en el Boletín Oficial de este municipio (backlog R7).
 *
 * <p>Vive en la base del municipio, sin columna de tenant, igual que
 * {@code reclamo} y {@code registro_auditoria}. {@code publicadoPorNombre}
 * y {@code publicadoPorEmail} son una copia del actor al momento de
 * publicar, no una relación JPA —mismo criterio que
 * {@code RegistroAuditoriaEntity}, ADR 0013—: es la firma pública de la
 * norma, no un dato que tenga que seguir vivo si ese usuario cambia de
 * nombre o se desactiva después.
 *
 * <p>Sin métodos de mutación a propósito: una vez publicada, esta rebanada
 * no edita ni borra una norma. Es un registro público que se corrige
 * publicando una norma nueva, no mutando la vieja.
 */
@Entity
@Table(name = "norma")
class NormaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDeNorma tipo;

    @Column(nullable = false, length = 50)
    private String numero;

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

    protected NormaEntity() {
    }

    static NormaEntity nueva(TipoDeNorma tipo, String numero, String titulo, String texto,
            LocalDate fechaPublicacion, String publicadoPorNombre, String publicadoPorEmail) {

        NormaEntity norma = new NormaEntity();
        norma.tipo = tipo;
        norma.numero = numero;
        norma.titulo = titulo;
        norma.texto = texto;
        norma.fechaPublicacion = fechaPublicacion;
        norma.publicadoPorNombre = publicadoPorNombre;
        norma.publicadoPorEmail = publicadoPorEmail;
        norma.creadoEn = Instant.now();
        return norma;
    }

    Long getId() {
        return id;
    }

    TipoDeNorma getTipo() {
        return tipo;
    }

    String getNumero() {
        return numero;
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
