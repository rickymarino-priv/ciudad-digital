package ar.com.ciudaddigital.acceso.internal;

import java.io.Serializable;
import java.util.Set;

/**
 * Quién está usando la sesión y qué puede hacer.
 *
 * <p>Viaja como principal de la sesión, así que es {@link Serializable} y
 * lleva solo lo necesario: nunca el hash de la contraseña.
 */
record UsuarioAutenticado(Long id, String nombre, String email, Set<String> permisos)
        implements Serializable {

    UsuarioAutenticado {
        permisos = Set.copyOf(permisos);
    }

    static UsuarioAutenticado de(UsuarioEntity usuario) {
        return new UsuarioAutenticado(
                usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.permisos());
    }
}
