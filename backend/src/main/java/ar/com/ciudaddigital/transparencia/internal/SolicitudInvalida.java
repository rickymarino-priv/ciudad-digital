package ar.com.ciudaddigital.transparencia.internal;

/** La partida presupuestaria o la entrada de escala salarial a publicar, o la búsqueda pedida, no es válida. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
