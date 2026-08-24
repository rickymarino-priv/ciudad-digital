package ar.com.ciudaddigital.ejemplo.internal;

/** El mensaje del eco no es válido. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
