package ar.com.ciudaddigital.tenants.internal;

import java.io.IOException;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;

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
    private static final String PREFIJO_ADMIN = "/api/admin/";

    private final TenantResolver resolver;

    TenantResolutionFilter(TenantResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = rutaDespachada(request);
        // La API de administración es cross-tenant por definición: opera
        // sobre todos los municipios, así que no se resuelve ninguno.
        return !ruta.startsWith(PREFIJO_API) || ruta.startsWith(PREFIJO_ADMIN);
    }

    /**
     * Ruta decodificada por segmento y sin parámetros de matriz ({@code ;}),
     * en la misma forma que usa Spring MVC para elegir el handler
     * ({@link org.springframework.web.util.pattern.PathPattern}, sobre el
     * {@code RequestPath} parseado). Decidir esta exclusión contra {@code
     * request.getRequestURI()} —la URI cruda— deja pasar variantes
     * percent-encoded equivalentes como {@code /%61pi/...}: sería la misma
     * ruta para el que despacha y otra distinta para el que decide si hay
     * que resolver tenant, salteando también el gating de módulos que corre
     * después (ADR 0012 §3; mismo problema que {@code
     * entitlement.internal.GatingDeModulosFilter}, duplicado acá a
     * propósito porque son módulos distintos).
     *
     * <p>Cachear acá el {@code RequestPath} parseado no rompe el despacho
     * posterior: el {@code DispatcherServlet} vuelve a parsearlo por su
     * cuenta antes de despachar y restaura el valor previo del atributo al
     * terminar.
     */
    private static String rutaDespachada(HttpServletRequest request) {
        PathContainer pathWithinApplication =
                ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();

        StringBuilder ruta = new StringBuilder();
        for (PathContainer.Element elemento : pathWithinApplication.elements()) {
            ruta.append(elemento instanceof PathContainer.PathSegment segmento
                    ? segmento.valueToMatch()
                    : elemento.value());
        }
        return ruta.toString();
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
        TenantModulosHolder.establecer(modulosHabilitados(encontrado));
        try {
            chain.doFilter(request, response);
        } finally {
            // Sin esto, el hilo reutilizado por el próximo request llegaría
            // con el municipio anterior cargado.
            TenantHolder.limpiar();
            TenantModulosHolder.limpiar();
        }
    }

    /**
     * Lista de módulos contratados del tenant resuelto, para que {@code
     * entitlement.internal.GatingDeModulosFilter} pueda preguntar por ella
     * más adelante en la cadena (ADR 0012 §2).
     */
    private List<String> modulosHabilitados(TenantEntity tenant) {
        TenantConfig config = tenant.getConfig();
        return config == null ? List.of() : config.modulosHabilitados();
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
