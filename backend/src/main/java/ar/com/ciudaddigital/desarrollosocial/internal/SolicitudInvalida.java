package ar.com.ciudaddigital.desarrollosocial.internal;

/** El programa a publicar, la búsqueda pedida, la inscripción o el cambio de estado, no es válido. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
