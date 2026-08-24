package ar.com.ciudaddigital.tenants.internal;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Errores en JSON desde los filtros de la cadena de seguridad de
 * administración, fuera del alcance de los {@code @ExceptionHandler} de
 * los controllers.
 *
 * <p>Duplica {@code acceso.internal.RespuestasJson}: son diez líneas, y
 * los límites de módulo de Spring Modulith no dejan que un módulo alcance
 * los internals de otro, así que compartir esto costaría más que
 * repetirlo.
 */
final class RespuestasJsonDePlataforma {

    private RespuestasJsonDePlataforma() {
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
