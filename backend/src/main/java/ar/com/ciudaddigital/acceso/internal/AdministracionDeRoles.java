package ar.com.ciudaddigital.acceso.internal;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, edición y baja de los roles del municipio del request en curso
 * (ADR 0011).
 *
 * <p>Los roles de sistema —sembrados en el alta del municipio— tienen dos
 * invariantes que este servicio hace cumplir en todos los casos, no solo
 * en la pantalla: no se pueden borrar, y el rol de administrador no puede
 * quedarse sin el permiso que administra usuarios. Sin esa segunda regla,
 * un municipio podría editar su propio rol de administrador hasta dejarse
 * afuera de su administración.
 */
@Service
class AdministracionDeRoles {

    private static final Pattern CODIGO_VALIDO = Pattern.compile("^[a-z][a-z0-9-]{1,59}$");

    /** Código del rol de sistema que administra usuarios y roles. */
    private static final String CODIGO_ADMINISTRADOR = "administrador";

    /** Permiso que el rol de administrador no puede perder (ADR 0011). */
    private static final String PERMISO_INDISPENSABLE = "usuarios.administrar";

    private final RolRepository roles;
    private final PermisoRepository permisos;

    AdministracionDeRoles(RolRepository roles, PermisoRepository permisos) {
        this.roles = roles;
        this.permisos = permisos;
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    List<RolEntity> listar() {
        return roles.findAllByOrderByNombreAsc();
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    List<PermisoEntity> catalogoDePermisos() {
        return permisos.findAllByOrderByAreaAscModuloAscAccionAsc();
    }

    @Transactional("tenantTransactionManager")
    RolEntity crear(String codigo, String nombre, String descripcion, Set<String> codigosDePermiso) {
        String codigoNormalizado = validarCodigo(codigo);
        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre del rol.");
        }
        if (roles.existsByCodigoIgnoreCase(codigoNormalizado)) {
            throw new SolicitudInvalida("Ya existe un rol con el código " + codigoNormalizado + ".");
        }

        RolEntity rol = RolEntity.nuevo(codigoNormalizado, nombre.trim(), descripcion);
        rol.asignarPermisos(Set.copyOf(resolverPermisos(codigosDePermiso)));
        return roles.save(rol);
    }

    @Transactional("tenantTransactionManager")
    RolEntity editar(Long id, String nombre, String descripcion, Set<String> codigosDePermiso) {
        RolEntity rol = roles.findById(id)
                .orElseThrow(() -> new SolicitudInvalida("No existe el rol " + id + "."));

        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre del rol.");
        }

        List<PermisoEntity> nuevosPermisos = resolverPermisos(codigosDePermiso);
        validarPermisoIndispensable(rol, nuevosPermisos);

        rol.actualizar(nombre.trim(), descripcion);
        rol.asignarPermisos(Set.copyOf(nuevosPermisos));
        return roles.save(rol);
    }

    @Transactional("tenantTransactionManager")
    void eliminar(Long id) {
        RolEntity rol = roles.findById(id)
                .orElseThrow(() -> new SolicitudInvalida("No existe el rol " + id + "."));

        if (rol.isDelSistema()) {
            throw new SolicitudInvalida(
                    "El rol " + rol.getNombre() + " es un rol de sistema y no se puede borrar.");
        }
        roles.delete(rol);
    }

    private void validarPermisoIndispensable(RolEntity rol, List<PermisoEntity> nuevosPermisos) {
        if (!CODIGO_ADMINISTRADOR.equalsIgnoreCase(rol.getCodigo())) {
            return;
        }
        boolean conservaElPermiso = nuevosPermisos.stream()
                .anyMatch(permiso -> PERMISO_INDISPENSABLE.equals(permiso.getCodigo()));
        if (!conservaElPermiso) {
            throw new SolicitudInvalida(
                    "El rol de administrador no puede quedarse sin el permiso para "
                            + "administrar usuarios: un municipio no puede dejarse afuera de su "
                            + "propia administración.");
        }
    }

    private String validarCodigo(String codigo) {
        String normalizado = codigo == null ? "" : codigo.trim().toLowerCase(Locale.ROOT);
        if (!CODIGO_VALIDO.matcher(normalizado).matches()) {
            throw new SolicitudInvalida(
                    "El código del rol tiene que empezar con una letra y contener solo "
                            + "minúsculas, números y guiones.");
        }
        return normalizado;
    }

    private List<PermisoEntity> resolverPermisos(Set<String> codigosDePermiso) {
        Set<String> codigos = codigosDePermiso == null ? Set.of() : codigosDePermiso;
        List<PermisoEntity> encontrados = permisos.findByCodigoIn(codigos);
        if (encontrados.size() != codigos.size()) {
            throw new SolicitudInvalida("Alguno de los permisos indicados no existe.");
        }
        return encontrados;
    }
}
