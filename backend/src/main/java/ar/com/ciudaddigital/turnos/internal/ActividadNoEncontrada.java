package ar.com.ciudaddigital.turnos.internal;

/**
 * No existe ninguna actividad con el id indicado. Se usa al crear una
 * franja o cambiar el estado de un id inventado o de otro municipio;
 * mapea a {@code 404}, nunca a {@link SolicitudInvalida} (400): no es un
 * problema de la forma de la solicitud, es que ese recurso no está (mismo
 * patrón que {@code ObraNoEncontrada}/{@code ArbolNoEncontrado}).
 */
class ActividadNoEncontrada extends RuntimeException {

    ActividadNoEncontrada(String mensaje) {
        super(mensaje);
    }
}
