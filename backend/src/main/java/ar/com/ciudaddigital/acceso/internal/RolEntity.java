package ar.com.ciudaddigital.acceso.internal;

import java.util.LinkedHashSet;
import java.util.Set;

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
 * Conjunto de permisos con nombre, definido por cada municipio (ADR 0011).
 *
 * <p>Los roles marcados como del sistema vienen sembrados por el alta para
 * que un municipio recién creado sea usable, y no se borran.
 */
@Entity
@Table(name = "rol")
class RolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 300)
    private String descripcion;

    @Column(name = "del_sistema", nullable = false)
    private boolean delSistema;

    /*
     * EAGER a propósito: los permisos del rol se necesitan en todos los
     * usos —autenticar, autorizar cada request, listar roles—, así que
     * diferirlos solo agregaría consultas y el riesgo de leerlos fuera de
     * la sesión de persistencia. Es un Set y no un List: con dos
     * colecciones EAGER, Hibernate solo puede combinarlas en una consulta
     * si no son bags.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "rol_permiso",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_codigo"))
    private Set<PermisoEntity> permisos = new LinkedHashSet<>();

    protected RolEntity() {
    }

    Long getId() {
        return id;
    }

    String getCodigo() {
        return codigo;
    }

    String getNombre() {
        return nombre;
    }

    String getDescripcion() {
        return descripcion;
    }

    boolean isDelSistema() {
        return delSistema;
    }

    Set<PermisoEntity> getPermisos() {
        return permisos;
    }
}
