package ar.com.ciudaddigital.tasas.internal;

/** La tasa a publicar, la búsqueda pedida, o el pago a iniciar/confirmar, no es válido. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
