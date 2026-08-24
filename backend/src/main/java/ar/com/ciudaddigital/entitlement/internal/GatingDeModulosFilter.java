package ar.com.ciudaddigital.entitlement.internal;

import java.io.IOException;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ar.com.ciudaddigital.entitlement.CatalogoDeModulos;
import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rechaza los requests dirigidos a un módulo que el municipio no tiene
 * contratado (ADR 0012 §3, §5).
 *
 * <p>Corre después de que el tenant ya está resuelto —{@code
 * tenants.internal.TenantResolutionFilter}, con {@code @Order(Integer.MIN_VALUE
 * + 100)}— y antes de la cadena de Spring Security, cuyo {@code @Order} por
 * defecto es {@code -100}. Ese orden es lo que materializa "entitlement
 * primero, permiso después" (ADR 0011): el rechazo por módulo no contratado
 * nunca depende de quién sea el usuario, porque todavía no corrió ninguna
 * verificación de autenticación ni de permisos.
 */
@Component
@Order(GatingDeModulosFilter.ORDEN)
class GatingDeModulosFilter extends OncePerRequestFilter {

    /*
     * Cualquier valor estrictamente entre Integer.MIN_VALUE + 100 (el orden
     * de TenantResolutionFilter) y -100 (el de la cadena de Spring
     * Security) cumple el contrato de arriba; el rango es enorme, así que
     * el valor puntual no importa más que para dejar constancia de dónde
     * cae.
     */
    static final int ORDEN = Integer.MIN_VALUE + 150;

    private static final String PREFIJO_API = "/api/";
    private static final String PREFIJO_ADMIN = "/api/admin/";

    private final CatalogoDeModulos catalogo;

    GatingDeModulosFilter(CatalogoDeModulos catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        // Misma exclusión que TenantResolutionFilter: la API de
        // administración es cross-tenant, no hay tenant resuelto contra el
        // que preguntar si un módulo está contratado.
        return !ruta.startsWith(PREFIJO_API) || ruta.startsWith(PREFIJO_ADMIN);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        Optional<DescriptorDeModulo> modulo = moduloDe(request.getRequestURI());

        if (modulo.isEmpty()) {
            // No pertenece a ningún módulo declarado: es canon base y no se
            // gatea (ADR 0012 §4).
            chain.doFilter(request, response);
            return;
        }

        if (!catalogo.habilitado(modulo.get().codigo())) {
            responderNoContratado(response, modulo.get().codigo());
            return;
        }

        chain.doFilter(request, response);
    }

    private Optional<DescriptorDeModulo> moduloDe(String ruta) {
        return catalogo.catalogo().stream()
                .filter(descriptor -> descriptor.prefijosDeApi().stream()
                        .anyMatch(prefijo -> coincide(ruta, prefijo)))
                .findFirst();
    }

    /** Coincidencia por segmento: {@code /api/ejemplote} no matchea {@code /api/ejemplo}. */
    private boolean coincide(String ruta, String prefijo) {
        return ruta.equals(prefijo) || ruta.startsWith(prefijo + "/");
    }

    private void responderNoContratado(HttpServletResponse response, String codigoModulo)
            throws IOException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"error":"%s","codigo":"MODULO_NO_CONTRATADO","modulo":"%s"}"""
                .formatted(
                        "El municipio no tiene contratado el módulo " + codigoModulo + ".",
                        codigoModulo));
    }
}
