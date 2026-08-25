package ar.com.ciudaddigital.acceso.internal;

import java.io.Serializable;
import java.security.Principal;
import java.util.Set;

import ar.com.ciudaddigital.acceso.ActorAutenticado;

/**
 * Quién está usando la sesión y qué puede hacer.
 *
 * <p>Viaja como principal de la sesión, así que es {@link Serializable} y
 * lleva solo lo necesario: nunca el hash de la contraseña.
 *
 * <p>Implementa {@link Principal} para que {@code Authentication#getName()}
 * devuelva el email: es la forma en la que un módulo funcional identifica al
 * usuario autenticado sin depender de este tipo, que es interno de {@code
 * acceso}. Sin esto, {@code getName()} caería en {@code Object#toString()}
 * del record.
 *
 * <p>También implementa {@link ActorAutenticado}, la vista pública mínima
 * (id, nombre, email) que otros módulos pueden castear desde
 * {@code Authentication#getPrincipal()} cuando necesitan algo más que el
 * email de {@code getName()} —por ejemplo, la firma de quien publicó una
 * norma en {@code boletin}— sin poder referenciar este tipo, que sigue
 * siendo interno de {@code acceso}. Los accesores que el record genera
 * automáticamente para {@code id}, {@code nombre} y {@code email} ya
 * coinciden con los de la interfaz, así que no hace falta escribir nada
 * más para satisfacerla.
 */
record UsuarioAutenticado(Long id, String nombre, String email, Set<String> permisos)
        implements Serializable, Principal, ActorAutenticado {

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
