package ar.com.ciudaddigital.reclamos.internal;

/**
 * No existe ningún reclamo con el token de seguimiento consultado (ADR 0017
 * §4): ni cuando el token no matchea ninguna fila, ni cuando el valor
 * recibido ni siquiera tiene forma de token. Deliberadamente no distingue
 * esos dos casos —ambos se mapean a la misma respuesta 404 genérica— para
 * no darle a quien prueba tokens al azar ninguna señal sobre por qué falló.
 */
class TokenNoEncontrado extends RuntimeException {

    TokenNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
