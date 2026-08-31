package ar.com.ciudaddigital.prensa.internal;

/** La gacetilla a publicar, o la búsqueda pedida, no es válida. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
