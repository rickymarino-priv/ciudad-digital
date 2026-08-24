package ar.com.ciudaddigital.acceso.internal;

/** La solicitud de administración no se puede cumplir tal como llegó. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
