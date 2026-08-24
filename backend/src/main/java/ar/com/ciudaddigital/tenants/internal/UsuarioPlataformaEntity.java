package ar.com.ciudaddigital.tenants.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Quien puede operar la API cross-tenant de administración de municipios
 * (ADR 0010).
 *
 * <p>Vive en la base de control, separado de los usuarios de cada
 * municipio ({@code acceso.internal.UsuarioEntity}): son conceptos
 * distintos — este opera la plataforma, aquellos entran al portal de un
 * municipio — y mezclarlos volvería ambiguo a quién pertenece cada fila.
 */
@Entity
@Table(name = "usuario_plataforma")
class UsuarioPlataformaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(name = "hash_password", nullable = false, length = 100)
    private String hashPassword;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "ultimo_acceso")
    private Instant ultimoAcceso;

    protected UsuarioPlataformaEntity() {
    }

    static UsuarioPlataformaEntity nuevo(String nombre, String email, String hashPassword) {
        UsuarioPlataformaEntity usuario = new UsuarioPlataformaEntity();
        usuario.nombre = nombre;
        usuario.email = email;
        usuario.hashPassword = hashPassword;
        usuario.activo = true;
        usuario.creadoEn = Instant.now();
        return usuario;
    }

    Long getId() {
        return id;
    }

    String getNombre() {
        return nombre;
    }

    String getEmail() {
        return email;
    }

    String getHashPassword() {
        return hashPassword;
    }

    boolean isActivo() {
        return activo;
    }

    void registrarAcceso(Instant momento) {
        this.ultimoAcceso = momento;
    }
}
