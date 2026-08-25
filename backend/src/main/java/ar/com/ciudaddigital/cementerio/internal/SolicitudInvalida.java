package ar.com.ciudaddigital.cementerio.internal;

/** El registro de sepultura a dar de alta, o la búsqueda pedida, no es válido. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
