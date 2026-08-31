package ar.com.ciudaddigital.turnos.internal;

/**
 * La franja no tiene cupo disponible al momento de reservar (ADR 0026
 * §4/§7): mapea a {@code 409 Conflict}, primer uso de ese código en el
 * proyecto. La franja existe y su actividad está {@code ACTIVA} — el
 * problema es exclusivamente que {@code cupoDisponible} llegó a cero antes
 * de que esta solicitud lograra decrementarlo con el {@code UPDATE}
 * condicional atómico de {@code FranjaHorariaRepository#reservarUnLugar}.
 */
class CupoAgotado extends RuntimeException {

    CupoAgotado(String mensaje) {
        super(mensaje);
    }
}
