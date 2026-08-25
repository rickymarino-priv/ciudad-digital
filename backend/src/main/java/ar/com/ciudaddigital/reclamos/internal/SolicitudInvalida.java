package ar.com.ciudaddigital.reclamos.internal;

/** El reclamo, o el cambio de estado pedido, no es válido. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
