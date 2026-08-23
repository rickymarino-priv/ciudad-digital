package ar.com.ciudaddigital.tenants.internal;

/** El alta de un municipio no se pudo completar. */
class AprovisionamientoFallido extends RuntimeException {

    AprovisionamientoFallido(String mensaje) {
        super(mensaje);
    }

    AprovisionamientoFallido(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
