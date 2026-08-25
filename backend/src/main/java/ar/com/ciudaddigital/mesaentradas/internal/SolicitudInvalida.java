package ar.com.ciudaddigital.mesaentradas.internal;

/** El trámite, o el cambio de estado pedido, no es válido. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
