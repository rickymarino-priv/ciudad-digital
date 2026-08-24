package ar.com.ciudaddigital.acceso.internal;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fuerza la emisión de la cookie con el token CSRF.
 *
 * <p>Spring Security genera el token de forma diferida: si nadie lo lee, la
 * cookie nunca se escribe y el frontend no tiene qué mandar en el primer
 * POST. Pedir el valor acá, en cada request, garantiza que el token viaje
 * antes de que haga falta.
 */
class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            // El valor no se usa: alcanza con pedirlo para que se resuelva
            // y el repositorio escriba la cookie.
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
