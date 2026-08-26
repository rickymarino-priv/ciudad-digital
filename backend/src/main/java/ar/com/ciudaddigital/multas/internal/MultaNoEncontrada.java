package ar.com.ciudaddigital.multas.internal;

/**
 * No existe ninguna multa con el id indicado. Se usa para iniciar un pago,
 * presentar un descargo o resolverlo sobre un id inventado; mapea a
 * {@code 404}, nunca a {@link SolicitudInvalida} (400): no es un problema
 * de la forma de la solicitud, es que ese recurso no está.
 */
class MultaNoEncontrada extends RuntimeException {

    MultaNoEncontrada(String mensaje) {
        super(mensaje);
    }
}
