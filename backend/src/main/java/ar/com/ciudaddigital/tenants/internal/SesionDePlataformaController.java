package ar.com.ciudaddigital.tenants.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Entrar y salir de la API de administración (ADR 0010).
 *
 * <p>Reemplaza el token compartido {@code ciudad.admin.token} de R2: un
 * token no identifica a nadie, un usuario de plataforma sí.
 */
@RestController
@RequestMapping("/api/admin/sesion")
class SesionDePlataformaController {

    private final AutenticacionDePlataforma autenticacion;
    private final SecurityContextRepository repositorioDeContexto;

    SesionDePlataformaController(
            AutenticacionDePlataforma autenticacion,
            @Qualifier("repositorioDeContextoDePlataforma") SecurityContextRepository repositorioDeContexto) {
        this.autenticacion = autenticacion;
        this.repositorioDeContexto = repositorioDeContexto;
    }

    @PostMapping
    SesionResponse iniciar(@RequestBody CredencialesRequest credenciales,
            HttpServletRequest request, HttpServletResponse response) {

        UsuarioPlataformaAutenticado usuario =
                autenticacion.autenticar(credenciales.email(), credenciales.password());

        HttpSession sesion = request.getSession(true);
        request.changeSessionId();

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(AutenticacionesDePlataforma.de(usuario));
        SecurityContextHolder.setContext(contexto);
        repositorioDeContexto.saveContext(contexto, request, response);

        return SesionResponse.de(usuario);
    }

    @GetMapping
    SesionResponse actual(Authentication autenticado) {
        if (autenticado == null
                || !(autenticado.getPrincipal() instanceof UsuarioPlataformaAutenticado usuario)) {
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

    @ExceptionHandler(CredencialesDePlataformaInvalidas.class)
    ResponseEntity<ErrorResponse> credencialesInvalidas(CredencialesDePlataformaInvalidas e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage()));
    }

    record CredencialesRequest(String email, String password) {
    }

    record SesionResponse(boolean autenticado, UsuarioResponse usuario) {

        static SesionResponse anonima() {
            return new SesionResponse(false, null);
        }

        static SesionResponse de(UsuarioPlataformaAutenticado usuario) {
            return new SesionResponse(true, new UsuarioResponse(
                    usuario.id(), usuario.nombre(), usuario.email()));
        }
    }

    record UsuarioResponse(Long id, String nombre, String email) {
    }

    record ErrorResponse(String error) {
    }
}
