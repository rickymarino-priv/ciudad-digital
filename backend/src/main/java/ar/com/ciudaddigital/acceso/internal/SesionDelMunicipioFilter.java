package ar.com.ciudaddigital.acceso.internal;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import ar.com.ciudaddigital.tenants.TenantContext;
import ar.com.ciudaddigital.tenants.TenantInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Ata cada sesión al municipio en el que se abrió y mantiene sus permisos
 * al día (ADR 0010).
 *
 * <p>Hace dos cosas que no se pueden delegar en el browser:
 *
 * <ol>
 * <li><strong>Verifica el municipio.</strong> La cookie de sesión se emite
 * sin atributo {@code Domain}, así que un browser jamás la manda a otro
 * subdominio. Pero eso vale para un browser: cualquier cliente puede
 * presentar la cookie donde quiera, y sin esta verificación una sesión de
 * un municipio abriría datos de otro.</li>
 * <li><strong>Relee al usuario en cada request.</strong> Si los permisos
 * quedaran congelados en la sesión, desactivar a alguien o sacarle un rol
 * no tendría efecto hasta que cerrara sesión — que es justo lo que no se
 * puede esperar cuando hay que cortarle el acceso a alguien.</li>
 * </ol>
 */
class SesionDelMunicipioFilter extends OncePerRequestFilter {

    /** Municipio en el que se inició la sesión, guardado al hacer login. */
    static final String ATRIBUTO_MUNICIPIO = "ciudad.municipio";

    private static final String PREFIJO_ADMIN = "/api/admin/";

    private final AutenticacionDeMunicipio autenticacion;

    SesionDelMunicipioFilter(AutenticacionDeMunicipio autenticacion) {
        this.autenticacion = autenticacion;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // La API de administración es cross-tenant: no tiene municipio
        // resuelto contra el cual comparar.
        return request.getRequestURI().startsWith(PREFIJO_ADMIN);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        HttpSession sesion = request.getSession(false);
        Object municipioDeLaSesion =
                sesion == null ? null : sesion.getAttribute(ATRIBUTO_MUNICIPIO);

        if (municipioDeLaSesion == null) {
            chain.doFilter(request, response);
            return;
        }

        String municipioDelRequest =
                TenantContext.actual().map(TenantInfo::slug).orElse(null);

        if (!municipioDeLaSesion.equals(municipioDelRequest)) {
            cerrar(sesion);
            RespuestasJson.error(response, HttpStatus.UNAUTHORIZED,
                    "La sesión no corresponde a este municipio.");
            return;
        }

        Authentication autenticado = SecurityContextHolder.getContext().getAuthentication();
        if (autenticado != null
                && autenticado.getPrincipal() instanceof UsuarioAutenticado usuario) {

            Optional<UsuarioAutenticado> alDia = autenticacion.refrescar(usuario.id());
            if (alDia.isEmpty()) {
                cerrar(sesion);
                RespuestasJson.error(response, HttpStatus.UNAUTHORIZED,
                        "La sesión ya no es válida.");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(Autenticaciones.de(alDia.get()));
        }

        chain.doFilter(request, response);
    }

    private void cerrar(HttpSession sesion) {
        SecurityContextHolder.clearContext();
        sesion.invalidate();
    }
}
