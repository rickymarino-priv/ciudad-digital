package ar.com.ciudaddigital.turnos.internal;

/** La actividad, franja o reserva a dar de alta, buscar o actualizar no es válida. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
