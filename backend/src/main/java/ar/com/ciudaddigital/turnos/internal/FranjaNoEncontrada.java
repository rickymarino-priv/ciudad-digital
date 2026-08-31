package ar.com.ciudaddigital.turnos.internal;

/**
 * No existe ninguna franja horaria con el id indicado. Se usa al reservar
 * un turno o listar franjas de un id inventado o de otro municipio; mapea
 * a {@code 404}, nunca a {@link SolicitudInvalida} (400): no es un
 * problema de la forma de la solicitud, es que ese recurso no está (mismo
 * patrón que {@code ActividadNoEncontrada}).
 */
class FranjaNoEncontrada extends RuntimeException {

    FranjaNoEncontrada(String mensaje) {
        super(mensaje);
    }
}
