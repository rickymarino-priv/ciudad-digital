package ar.com.ciudaddigital.acceso.internal;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Errores en JSON desde los filtros, que están fuera del alcance de los
 * {@code @ExceptionHandler} de los controllers.
 */
final class RespuestasJson {

    private RespuestasJson() {
    }

    static void error(HttpServletResponse response, HttpStatus estado, String mensaje)
            throws IOException {

        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"error":"%s"}""".formatted(mensaje));
    }
}
