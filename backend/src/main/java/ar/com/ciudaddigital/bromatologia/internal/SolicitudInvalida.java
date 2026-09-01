package ar.com.ciudaddigital.bromatologia.internal;

/** El comercio o la inspección a registrar, o la búsqueda pedida, no es válida. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
