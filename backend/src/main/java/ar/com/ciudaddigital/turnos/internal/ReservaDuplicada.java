package ar.com.ciudaddigital.turnos.internal;

/**
 * Ya existe una reserva con ese DNI para esa franja (ADR 0026 §4/§7):
 * mapea a {@code 409 Conflict}. Se lanza tanto desde el chequeo temprano de
 * {@code GestionDeReservas#reservar} (caso común, sin carrera) como desde
 * la traducción de la {@code DataIntegrityViolationException} que dispara
 * la restricción {@code unique (franja_id, dni_solicitante)} de la base
 * (barrera real bajo concurrencia). En ambos casos tiene que llegar sin
 * atrapar hasta el proxy transaccional: si el segundo caso se atrapara y
 * se tragara acá, el decremento de cupo ya aplicado en la misma
 * transacción no haría rollback, y quedaría un cupo "fantasma" consumido
 * sin ningún turno guardado.
 */
class ReservaDuplicada extends RuntimeException {

    ReservaDuplicada(String mensaje) {
        super(mensaje);
    }
}
