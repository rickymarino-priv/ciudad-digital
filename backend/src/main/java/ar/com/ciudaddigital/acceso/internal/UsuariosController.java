package ar.com.ciudaddigital.acceso.internal;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Usuarios del municipio del request en curso.
 *
 * <p>El permiso se verifica acá, en el backend: que el frontend esconda la
 * pantalla es comodidad para el usuario, no protección (ADR 0011).
 */
@RestController
@RequestMapping("/api/usuarios")
class UsuariosController {

    private final UsuarioRepository usuarios;

    UsuariosController(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('usuarios.ver')")
    List<UsuarioResponse> listar() {
        return usuarios.findAll(Sort.by("nombre")).stream()
                .map(UsuariosController::describir)
                .toList();
    }

    private static UsuarioResponse describir(UsuarioEntity usuario) {
        List<String> roles = usuario.getRoles().stream()
                .map(RolEntity::getNombre)
                .sorted(Comparator.naturalOrder())
                .toList();

        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.isActivo(),
                usuario.getUltimoAcceso(),
                roles);
    }

    record UsuarioResponse(
            Long id,
            String nombre,
            String email,
            boolean activo,
            Instant ultimoAcceso,
            List<String> roles) {
    }
}
