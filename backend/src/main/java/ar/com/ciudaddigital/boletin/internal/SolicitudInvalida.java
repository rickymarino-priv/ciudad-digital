package ar.com.ciudaddigital.boletin.internal;

/** La norma a publicar, o la búsqueda pedida, no es válida. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
