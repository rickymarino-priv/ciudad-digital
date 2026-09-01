package ar.com.ciudaddigital.defensacivil.internal;

/** La alerta o el recurso a registrar, la búsqueda pedida, o el cambio de estado, no es válido. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
