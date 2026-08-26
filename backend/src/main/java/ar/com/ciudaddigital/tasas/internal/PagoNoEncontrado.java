package ar.com.ciudaddigital.tasas.internal;

/**
 * No existe ninguna tasa con un pago en curso para la referencia externa
 * consultada (ADR 0018 §4): mismo criterio de mensaje genérico que
 * {@code TokenNoEncontrado} de {@code reclamos}/{@code mesaentradas} (ADR
 * 0017) — no distingue si la referencia no matchea ninguna fila o
 * directamente no tiene forma de referencia real, para no darle a quien
 * prueba referencias al azar ninguna señal sobre por qué falló.
 */
class PagoNoEncontrado extends RuntimeException {

    PagoNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
