package ar.com.ciudaddigital.desarrollosocial.internal;

/**
 * No existe ningún programa social con el id indicado. Se usa al cambiar
 * el estado de un id inventado o de otro municipio; mapea a {@code 404},
 * nunca a {@link SolicitudInvalida} (400): no es un problema de la forma
 * de la solicitud, es que ese recurso no está (mismo patrón que
 * {@code ObraNoEncontrada}).
 */
class ProgramaNoEncontrado extends RuntimeException {

    ProgramaNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
