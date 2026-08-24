package ar.com.ciudaddigital.acceso.internal;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Traduce un usuario del municipio a la forma que entiende Spring Security.
 *
 * <p>Los permisos del usuario son directamente las authorities: así el
 * chequeo de cada endpoint se escribe con el mismo código de permiso que
 * usa el catálogo (ADR 0011), sin prefijos ni traducciones intermedias.
 */
final class Autenticaciones {

    private Autenticaciones() {
    }

    static Authentication de(UsuarioAutenticado usuario) {
        List<GrantedAuthority> permisos = usuario.permisos().stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();

        // Sin credenciales: la contraseña ya se verificó y no tiene por qué
        // seguir viva en memoria durante toda la sesión.
        return UsernamePasswordAuthenticationToken.authenticated(usuario, null, permisos);
    }
}
