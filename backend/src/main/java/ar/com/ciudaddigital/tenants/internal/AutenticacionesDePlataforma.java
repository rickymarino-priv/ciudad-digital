package ar.com.ciudaddigital.tenants.internal;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Traduce un usuario de plataforma autenticado a la forma de Spring Security. */
final class AutenticacionesDePlataforma {

    private AutenticacionesDePlataforma() {
    }

    static Authentication de(UsuarioPlataformaAutenticado usuario) {
        // Sin authorities: hoy no hay roles distintos entre usuarios de
        // plataforma (ADR 0010), y sin credenciales porque la contraseña
        // ya se verificó y no tiene por qué seguir viva en memoria.
        return UsernamePasswordAuthenticationToken.authenticated(usuario, null, List.of());
    }
}
