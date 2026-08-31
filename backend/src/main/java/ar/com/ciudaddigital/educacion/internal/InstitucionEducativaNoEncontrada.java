package ar.com.ciudaddigital.educacion.internal;

/**
 * No existe ninguna institución educativa con el id indicado. Se usa al
 * actualizar el estado de un id inventado o de otro municipio; mapea a
 * {@code 404}, nunca a {@link SolicitudInvalida} (400): no es un problema
 * de la forma de la solicitud, es que ese recurso no está (mismo patrón
 * que {@code ObraNoEncontrada}/{@code ArbolNoEncontrado}).
 */
class InstitucionEducativaNoEncontrada extends RuntimeException {

    InstitucionEducativaNoEncontrada(String mensaje) {
        super(mensaje);
    }
}
