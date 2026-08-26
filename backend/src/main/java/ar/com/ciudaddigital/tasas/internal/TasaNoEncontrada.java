package ar.com.ciudaddigital.tasas.internal;

/**
 * No existe ninguna tasa con el id indicado. Se usa para iniciar un pago
 * sobre un id inventado o vencido; mapea a {@code 404}, nunca a un
 * {@code SolicitudInvalida} (400): no es un problema de la forma de la
 * solicitud, es que ese recurso no está.
 */
class TasaNoEncontrada extends RuntimeException {

    TasaNoEncontrada(String mensaje) {
        super(mensaje);
    }
}
