package ar.com.ciudaddigital.acceso.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.tenants.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Entrar y salir del portal del municipio (ADR 0010).
 *
 * <p>La sesión se abre siempre en el municipio del {@code Host} del
 * request: no hay forma de pedir sesión en otro, porque el municipio no es
 * un parámetro que el cliente mande.
 */
@RestController
@RequestMapping("/api/sesion")
class SesionController {

    private final AutenticacionDeMunicipio autenticacion;
    private final SecurityContextRepository repositorioDeContexto;

    SesionController(AutenticacionDeMunicipio autenticacion,
            SecurityContextRepository repositorioDeContexto) {
        this.autenticacion = autenticacion;
        this.repositorioDeContexto = repositorioDeContexto;
    }

    @PostMapping
    SesionResponse iniciar(@RequestBody CredencialesRequest credenciales,
            HttpServletRequest request, HttpServletResponse response) {

        UsuarioAutenticado usuario =
                autenticacion.autenticar(credenciales.email(), credenciales.password());

        // Id de sesión nuevo después de autenticar: si alguien logró fijar
        // el id antes del login, a partir de acá el que tiene ya no sirve.
        HttpSession sesion = request.getSession(true);
        request.changeSessionId();
        sesion.setAttribute(SesionDelMunicipioFilter.ATRIBUTO_MUNICIPIO,
                TenantContext.requerido().slug());

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(Autenticaciones.de(usuario));
        SecurityContextHolder.setContext(contexto);
        repositorioDeContexto.saveContext(contexto, request, response);

        return SesionResponse.de(usuario);
    }

    /**
     * Con quién está abierta la sesión, si hay alguna.
     *
     * <p>Responde 200 aunque no haya sesión: el frontend llama a esto al
     * arrancar para saber qué pantalla mostrar, y "no hay nadie" no es un
     * error.
     */
    @GetMapping
    SesionResponse actual(Authentication autenticado) {
        if (autenticado == null
                || !(autenticado.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return SesionResponse.anonima();
        }
        return SesionResponse.de(usuario);
    }

    @DeleteMapping
    ResponseEntity<Void> cerrar(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(CredencialesInvalidas.class)
    ResponseEntity<ErrorResponse> credencialesInvalidas(CredencialesInvalidas e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(e.getMessage()));
    }

    record CredencialesRequest(String email, String password) {
    }

    record SesionResponse(boolean autenticado, UsuarioResponse usuario) {

        static SesionResponse anonima() {
            return new SesionResponse(false, null);
        }

        static SesionResponse de(UsuarioAutenticado usuario) {
            return new SesionResponse(true, new UsuarioResponse(
                    usuario.id(),
                    usuario.nombre(),
                    usuario.email(),
                    List.copyOf(usuario.permisos())));
        }
    }

    record UsuarioResponse(Long id, String nombre, String email, List<String> permisos) {
    }

    record ErrorResponse(String error) {
    }
}
