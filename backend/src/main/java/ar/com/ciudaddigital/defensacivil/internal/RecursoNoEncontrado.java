package ar.com.ciudaddigital.defensacivil.internal;

/**
 * No existe ningún recurso con el id indicado. Se usa al actualizar el
 * estado de un id inventado o de otro municipio; mapea a {@code 404},
 * nunca a {@link SolicitudInvalida} (400): no es un problema de la forma
 * de la solicitud, es que ese recurso no está (mismo patrón que
 * {@code ArbolNoEncontrado}).
 */
class RecursoNoEncontrado extends RuntimeException {

    RecursoNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
