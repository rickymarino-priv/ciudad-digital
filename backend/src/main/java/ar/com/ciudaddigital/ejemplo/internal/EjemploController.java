package ar.com.ciudaddigital.ejemplo.internal;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.tenants.TenantContext;

/**
 * Ping y eco del módulo de ejemplo (ADR 0012 §10).
 *
 * <p>El ping no requiere sesión —lo tiene que poder ver un vecino anónimo
 * en el portal—, así que lo único que lo protege es el gating por
 * entitlement. El eco sí requiere el permiso {@code ejemplo.usar}: entre
 * los dos demuestran que entitlement y permiso conviven en el mismo módulo
 * sin que uno tape al otro (ADR 0011, ADR 0012 §3).
 */
@RestController
@RequestMapping("/api/ejemplo")
class EjemploController {

    /** Recorte defensivo: un eco no es el lugar para mensajes largos. */
    private static final int LARGO_MAXIMO_DEL_MENSAJE = 200;

    @GetMapping("/ping")
    PingResponse ping() {
        return new PingResponse(
                DescriptorDelModuloEjemplo.CODIGO,
                TenantContext.requerido().nombreMunicipio(),
                OffsetDateTime.now().toString());
    }

    /**
     * {@code Authentication#getName()} resuelve al email del usuario: es la
     * forma en la que un módulo funcional identifica a quien hace el
     * request sin depender del tipo de principal, que es interno del
     * módulo {@code acceso}.
     */
    @PostMapping("/eco")
    @PreAuthorize("hasAuthority('ejemplo.usar')")
    EcoResponse eco(@RequestBody(required = false) EcoRequest request, Authentication autenticacion) {
        String mensaje = request == null ? null : request.mensaje();
        if (mensaje == null || mensaje.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar un mensaje.");
        }

        String recortado = mensaje.length() > LARGO_MAXIMO_DEL_MENSAJE
                ? mensaje.substring(0, LARGO_MAXIMO_DEL_MENSAJE)
                : mensaje;

        return new EcoResponse(recortado, TenantContext.requerido().nombreMunicipio(),
                autenticacion.getName());
    }

    @ExceptionHandler(SolicitudInvalida.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalida e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    record PingResponse(String modulo, String municipio, String momento) {
    }

    record EcoRequest(String mensaje) {
    }

    record EcoResponse(String mensaje, String municipio, String usuario) {
    }

    record ErrorResponse(String error) {
    }
}
