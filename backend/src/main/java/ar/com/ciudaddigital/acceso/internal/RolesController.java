package ar.com.ciudaddigital.acceso.internal;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Roles del municipio del request en curso y catálogo de permisos
 * disponibles para armarlos (ADR 0011).
 */
@RestController
@RequestMapping("/api")
class RolesController {

    private final AdministracionDeRoles administracion;

    RolesController(AdministracionDeRoles administracion) {
        this.administracion = administracion;
    }

    /**
     * Quien administra usuarios necesita ver qué roles existen para
     * asignarlos, aunque no tenga el permiso de administrar roles en sí
     * (ADR 0011): por eso alcanza cualquiera de los dos permisos, no solo
     * {@code roles.ver}.
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('roles.ver', 'usuarios.administrar')")
    List<RolResponse> listar() {
        return administracion.listar().stream().map(RolesController::describir).toList();
    }

    /**
     * Catálogo de permisos, agrupado por área: una lista plana de cientos
     * de permisos no es administrable desde una pantalla.
     */
    @GetMapping("/permisos")
    @PreAuthorize("hasAuthority('roles.ver')")
    List<AreaDePermisosResponse> catalogoDePermisos() {
        return administracion.catalogoDePermisos().stream()
                .collect(Collectors.groupingBy(
                        PermisoEntity::getArea, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entrada -> new AreaDePermisosResponse(
                        entrada.getKey(),
                        entrada.getValue().stream().map(RolesController::describir).toList()))
                .toList();
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('roles.administrar')")
    ResponseEntity<RolResponse> crear(@RequestBody CrearRolRequest request) {
        RolEntity rol = administracion.crear(
                request.codigo(), request.nombre(), request.descripcion(), request.permisos());
        return ResponseEntity.status(HttpStatus.CREATED).body(describir(rol));
    }

    @PatchMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('roles.administrar')")
    RolResponse editar(@PathVariable Long id, @RequestBody EditarRolRequest request) {
        RolEntity rol = administracion.editar(
                id, request.nombre(), request.descripcion(), request.permisos());
        return describir(rol);
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('roles.administrar')")
    ResponseEntity<Void> eliminar(@PathVariable Long id) {
        administracion.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    private static RolResponse describir(RolEntity rol) {
        List<PermisoResponse> permisos = rol.getPermisos().stream()
                .map(RolesController::describir)
                .sorted(Comparator.comparing(PermisoResponse::codigo))
                .toList();

        return new RolResponse(
                rol.getId(), rol.getCodigo(), rol.getNombre(), rol.getDescripcion(),
                rol.isDelSistema(), permisos);
    }

    private static PermisoResponse describir(PermisoEntity permiso) {
        return new PermisoResponse(
                permiso.getCodigo(), permiso.getModulo(), permiso.getAccion(),
                permiso.getDescripcion());
    }

    record CrearRolRequest(String codigo, String nombre, String descripcion, Set<String> permisos) {
    }

    record EditarRolRequest(String nombre, String descripcion, Set<String> permisos) {
    }

    record PermisoResponse(String codigo, String modulo, String accion, String descripcion) {
    }

    record AreaDePermisosResponse(String area, List<PermisoResponse> permisos) {
    }

    record RolResponse(
            Long id,
            String codigo,
            String nombre,
            String descripcion,
            boolean delSistema,
            List<PermisoResponse> permisos) {
    }

    record ErrorResponse(String error) {
    }
}
