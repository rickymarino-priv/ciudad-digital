package ar.com.ciudaddigital.tenants.internal;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fuerza la emisión de la cookie con el token CSRF de la API de
 * administración. Ver {@code acceso.internal.RespuestasJsonDePlataforma}
 * sobre por qué esto está duplicado y no compartido.
 */
class CsrfCookieFilterDePlataforma extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
