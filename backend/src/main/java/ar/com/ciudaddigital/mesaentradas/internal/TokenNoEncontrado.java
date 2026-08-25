package ar.com.ciudaddigital.mesaentradas.internal;

/**
 * No existe ningún expediente con el token de seguimiento consultado (ADR
 * 0017 §4): ni cuando el token no matchea ninguna fila, ni cuando el valor
 * recibido ni siquiera tiene forma de token. Deliberadamente no distingue
 * esos dos casos —ambos se mapean a la misma respuesta 404 genérica— para
 * no darle a quien prueba tokens al azar ninguna señal sobre por qué falló.
 *
 * <p>Clase propia de este paquete, no compartida con
 * {@code reclamos.internal.TokenNoEncontrado}: mismo criterio que las dos
 * clases homónimas {@code SolicitudInvalida}, cada módulo es dueño de su
 * propia excepción de dominio.
 */
class TokenNoEncontrado extends RuntimeException {

    TokenNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
