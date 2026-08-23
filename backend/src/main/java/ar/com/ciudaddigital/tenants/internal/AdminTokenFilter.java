package ar.com.ciudaddigital.tenants.internal;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Protección provisoria de la API de administración.
 *
 * <p>La API de administración es la única superficie del sistema que cruza
 * municipios: da de alta, lista y toca todos. Dejarla abierta no sería
 * "endurecimiento diferido" sino un agujero, así que hasta que R3 traiga
 * autenticación de verdad se exige un token de configuración.
 *
 * <p>Esto <strong>se reemplaza en R3</strong>. Un token compartido en
 * configuración no identifica a nadie: no sirve para auditar quién dio de
 * alta un municipio, que es justamente lo que ADR 0009 pide registrar.
 */
@Component
@Order(AdminTokenFilter.ORDEN)
class AdminTokenFilter extends OncePerRequestFilter {

    static final int ORDEN = TenantResolutionFilter.ORDEN - 10;

    static final String PREFIJO_ADMIN = "/api/admin/";

    private static final String CABECERA = "X-Admin-Token";

    private final byte[] tokenEsperado;

    AdminTokenFilter(@Value("${ciudad.admin.token}") String token) {
        this.tokenEsperado = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIJO_ADMIN);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String recibido = request.getHeader(CABECERA);

        // Comparación en tiempo constante: una comparación común filtra,
        // por el tiempo que tarda, cuántos caracteres del token acertó
        // quien lo está probando.
        boolean autorizado = recibido != null && MessageDigest.isEqual(
                recibido.getBytes(StandardCharsets.UTF_8), tokenEsperado);

        if (!autorizado) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"error":"Falta el token de administración o no es válido."}""");
            return;
        }

        chain.doFilter(request, response);
    }
}
