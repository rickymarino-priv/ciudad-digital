package ar.com.ciudaddigital.multas.internal;

/**
 * La multa a labrar, la búsqueda pedida, el descargo/resolución, o el pago
 * a iniciar/confirmar, no es válido.
 */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
