package ar.com.ciudaddigital.acceso.internal;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Usuarios del municipio del request en curso (ADR 0011).
 *
 * <p>El permiso se verifica acá, en el backend: que el frontend esconda la
 * pantalla es comodidad para el usuario, no protección.
 */
@RestController
@RequestMapping("/api/usuarios")
class UsuariosController {

    private final UsuarioRepository usuarios;
    private final AdministracionDeUsuarios administracion;

    UsuariosController(UsuarioRepository usuarios, AdministracionDeUsuarios administracion) {
        this.usuarios = usuarios;
        this.administracion = administracion;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('usuarios.ver')")
    List<UsuarioResponse> listar() {
        return usuarios.findAll(Sort.by("nombre")).stream()
                .map(UsuariosController::describir)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('usuarios.administrar')")
    ResponseEntity<UsuarioResponse> crear(@RequestBody CrearUsuarioRequest request) {
        UsuarioEntity usuario = administracion.crear(
                request.nombre(), request.email(), request.password(), request.roles());
        return ResponseEntity.status(HttpStatus.CREATED).body(describir(usuario));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('usuarios.administrar')")
    UsuarioResponse editar(@PathVariable Long id, @RequestBody EditarUsuarioRequest request) {
        UsuarioEntity usuario =
                administracion.editar(id, request.nombre(), request.activo(), request.roles());
        return describir(usuario);
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    private static UsuarioResponse describir(UsuarioEntity usuario) {
        List<RolResumen> roles = usuario.getRoles().stream()
                .map(rol -> new RolResumen(rol.getId(), rol.getNombre()))
                .sorted(Comparator.comparing(RolResumen::nombre))
                .toList();

        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.isActivo(),
                usuario.getUltimoAcceso(),
                roles);
    }

    record CrearUsuarioRequest(String nombre, String email, String password, Set<Long> roles) {
    }

    record EditarUsuarioRequest(String nombre, boolean activo, Set<Long> roles) {
    }

    record RolResumen(Long id, String nombre) {
    }

    record UsuarioResponse(
            Long id,
            String nombre,
            String email,
            boolean activo,
            Instant ultimoAcceso,
            List<RolResumen> roles) {
    }

    record ErrorResponse(String error) {
    }
}
