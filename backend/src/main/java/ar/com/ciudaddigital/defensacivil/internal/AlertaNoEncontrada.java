package ar.com.ciudaddigital.defensacivil.internal;

/**
 * No existe ninguna alerta con el id indicado. Se usa al finalizar un id
 * inventado o de otro municipio; mapea a {@code 404}, nunca a
 * {@link SolicitudInvalida} (400): no es un problema de la forma de la
 * solicitud, es que ese recurso no está (mismo patrón que
 * {@code EventoNoEncontrado}).
 */
class AlertaNoEncontrada extends RuntimeException {

    AlertaNoEncontrada(String mensaje) {
        super(mensaje);
    }
}
