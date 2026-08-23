package ar.com.ciudaddigital.tenants.internal;

import java.io.IOException;

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
 * Resuelve el municipio al principio de cada request de API y lo deja
 * disponible para el resto del procesamiento (ADR 0004).
 *
 * <p>Corre antes que cualquier otra cosa: ningún handler debería ejecutarse
 * sin saber a qué municipio pertenece el request.
 */
@Component
@Order(TenantResolutionFilter.ORDEN)
class TenantResolutionFilter extends OncePerRequestFilter {

    static final int ORDEN = Integer.MIN_VALUE + 100;

    private static final String PREFIJO_API = "/api/";

    private final TenantResolver resolver;

    TenantResolutionFilter(TenantResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        // La API de administración es cross-tenant por definición: opera
        // sobre todos los municipios, así que no se resuelve ninguno.
        return !ruta.startsWith(PREFIJO_API) || ruta.startsWith(AdminTokenFilter.PREFIJO_ADMIN);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        var tenant = resolver.resolver(request.getServerName());

        if (tenant.isEmpty()) {
            responder(response, HttpStatus.NOT_FOUND,
                    "No hay ningún municipio publicado en este dominio.");
            return;
        }

        TenantEntity encontrado = tenant.get();
        if (!encontrado.getEstado().puedeAtender()) {
            responder(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "El portal de este municipio no está disponible en este momento.");
            return;
        }

        TenantHolder.establecer(encontrado.aTenantInfo());
        try {
            chain.doFilter(request, response);
        } finally {
            // Sin esto, el hilo reutilizado por el próximo request llegaría
            // con el municipio anterior cargado.
            TenantHolder.limpiar();
        }
    }

    private void responder(HttpServletResponse response, HttpStatus estado, String mensaje)
            throws IOException {
        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"error":"%s"}""".formatted(mensaje));
    }
}
