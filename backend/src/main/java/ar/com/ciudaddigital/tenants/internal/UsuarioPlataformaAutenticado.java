package ar.com.ciudaddigital.tenants.internal;

import java.io.Serializable;

/** Quién está operando la API de administración. */
record UsuarioPlataformaAutenticado(Long id, String nombre, String email) implements Serializable {

    static UsuarioPlataformaAutenticado de(UsuarioPlataformaEntity usuario) {
        return new UsuarioPlataformaAutenticado(usuario.getId(), usuario.getNombre(), usuario.getEmail());
    }
}
