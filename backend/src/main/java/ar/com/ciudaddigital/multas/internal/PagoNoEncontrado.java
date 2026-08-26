package ar.com.ciudaddigital.multas.internal;

/**
 * No existe ninguna multa con un pago en curso para la referencia externa
 * consultada (ADR 0018 §4, ADR 0021 §7): mismo criterio de mensaje
 * genérico que {@code PagoNoEncontrado} de {@code tasas} — no distingue si
 * la referencia no matchea ninguna fila o directamente no tiene forma de
 * referencia real, para no darle a quien prueba referencias al azar
 * ninguna señal sobre por qué falló.
 */
class PagoNoEncontrado extends RuntimeException {

    PagoNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
