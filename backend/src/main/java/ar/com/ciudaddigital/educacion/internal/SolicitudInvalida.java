package ar.com.ciudaddigital.educacion.internal;

/** La institución a registrar, la búsqueda pedida, o el cambio de estado, no es válido. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
