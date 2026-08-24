package ar.com.ciudaddigital.acceso.internal;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * Una persona que puede entrar al portal de este municipio (ADR 0010).
 *
 * <p>Vive en la base del municipio: no hay columna de tenant porque no hace
 * falta, la base ya es la del municipio. Un usuario de otro municipio no es
 * invisible por un filtro, sencillamente no está en esta tabla.
 */
@Entity
@Table(name = "usuario")
class UsuarioEntity {

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

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "usuario_rol",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<RolEntity> roles = new LinkedHashSet<>();

    protected UsuarioEntity() {
    }

    /**
     * Usuario nuevo, activo y sin roles. Los roles se asignan aparte: un
     * usuario recién creado que ya pudiera hacer algo sería un default
     * peligroso.
     */
    static UsuarioEntity nuevo(String nombre, String email, String hashPassword) {
        UsuarioEntity usuario = new UsuarioEntity();
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

    Instant getUltimoAcceso() {
        return ultimoAcceso;
    }

    Set<RolEntity> getRoles() {
        return roles;
    }

    void registrarAcceso(Instant momento) {
        this.ultimoAcceso = momento;
    }

    void renombrar(String nombre) {
        this.nombre = nombre;
    }

    void activar() {
        this.activo = true;
    }

    void desactivar() {
        this.activo = false;
    }

    /** Reemplaza el conjunto de roles por completo, no lo completa. */
    void asignarRoles(Set<RolEntity> roles) {
        this.roles.clear();
        this.roles.addAll(roles);
    }

    /**
     * Permisos efectivos: la unión de los permisos de sus roles. Un usuario
     * sin roles no puede hacer nada, que es el default correcto.
     */
    Set<String> permisos() {
        Set<String> codigos = new TreeSet<>();
        for (RolEntity rol : roles) {
            for (PermisoEntity permiso : rol.getPermisos()) {
                codigos.add(permiso.getCodigo());
            }
        }
        return codigos;
    }
}
