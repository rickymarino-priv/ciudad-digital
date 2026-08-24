package ar.com.ciudaddigital.tenants.internal;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Relee al usuario de plataforma en cada request (ADR 0010): si lo
 * desactivaron, la sesión deja de ser válida en la próxima request, sin
 * esperar a que expire nada.
 *
 * <p>No escribe una respuesta de error acá: limpia el contexto y deja que
 * cada endpoint decida —{@code GET /api/admin/sesion} informa "no
 * autenticado" con 200, el resto cae en el 401 estándar de Spring
 * Security—, igual que {@code acceso.internal.SesionDelMunicipioFilter}
 * hace para los usuarios de municipio.
 *
 * <p>También descarta cualquier autenticación que no sea de un usuario de
 * plataforma. Esta cadena y la del portal de municipio son cadenas de
 * Spring Security distintas, pero ambas guardan el contexto de seguridad
 * en el mismo tipo de sesión HTTP: sin este chequeo, una sesión de
 * municipio presentada acá pasaría {@code anyRequest().authenticated()}
 * igual, porque ese chequeo no distingue de qué tipo es el principal.
 */
class SesionDePlataformaFilter extends OncePerRequestFilter {

    private final AutenticacionDePlataforma autenticacion;

    SesionDePlataformaFilter(AutenticacionDePlataforma autenticacion) {
        this.autenticacion = autenticacion;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        Authentication autenticado = SecurityContextHolder.getContext().getAuthentication();
        if (autenticado == null) {
            chain.doFilter(request, response);
            return;
        }

        if (!(autenticado.getPrincipal() instanceof UsuarioPlataformaAutenticado usuario)) {
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
            return;
        }

        Optional<UsuarioPlataformaAutenticado> alDia = autenticacion.refrescar(usuario.id());
        if (alDia.isEmpty()) {
            SecurityContextHolder.clearContext();
            HttpSession sesion = request.getSession(false);
            if (sesion != null) {
                sesion.invalidate();
            }
        } else {
            SecurityContextHolder.getContext()
                    .setAuthentication(AutenticacionesDePlataforma.de(alDia.get()));
        }

        chain.doFilter(request, response);
    }
}
