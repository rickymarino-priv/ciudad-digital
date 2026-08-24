package ar.com.ciudaddigital.acceso.internal;

import java.io.Serializable;
import java.security.Principal;
import java.util.Set;

/**
 * Quién está usando la sesión y qué puede hacer.
 *
 * <p>Viaja como principal de la sesión, así que es {@link Serializable} y
 * lleva solo lo necesario: nunca el hash de la contraseña.
 *
 * <p>Implementa {@link Principal} para que {@code Authentication#getName()}
 * devuelva el email: es la forma en la que un módulo funcional (p. ej.
 * {@code ejemplo}) identifica al usuario autenticado sin depender de este
 * tipo, que es interno de {@code acceso}. Sin esto, {@code getName()} caería
 * en {@code Object#toString()} del record.
 */
record UsuarioAutenticado(Long id, String nombre, String email, Set<String> permisos)
        implements Serializable, Principal {

    UsuarioAutenticado {
        permisos = Set.copyOf(permisos);
    }

    static UsuarioAutenticado de(UsuarioEntity usuario) {
        return new UsuarioAutenticado(
                usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.permisos());
    }

    @Override
    public String getName() {
        return email;
    }
}
