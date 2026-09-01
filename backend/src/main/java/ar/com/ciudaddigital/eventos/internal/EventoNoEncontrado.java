package ar.com.ciudaddigital.eventos.internal;

/**
 * No existe ningún evento con el id indicado. Se usa al cancelar un id
 * inventado o de otro municipio; mapea a {@code 404}, nunca a
 * {@link SolicitudInvalida} (400): no es un problema de la forma de la
 * solicitud, es que ese recurso no está (mismo patrón que
 * {@code EspacioVerdeNoEncontrado}/{@code ObraNoEncontrada}).
 */
class EventoNoEncontrado extends RuntimeException {

    EventoNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
