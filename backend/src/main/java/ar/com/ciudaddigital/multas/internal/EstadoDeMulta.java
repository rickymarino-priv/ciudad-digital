package ar.com.ciudaddigital.multas.internal;

/**
 * Ciclo de vida de una multa (ADR 0021 §2): enum fijo con la tabla de
 * transiciones codificada en {@code MultaEntity}/{@code GestionDeMultas},
 * sin motor genérico ni tabla de movimientos separada — un único "tipo" de
 * multa y, como mucho, un descargo y su resolución.
 *
 * <pre>
 * NOTIFICADA  → PAGADA        (el vecino paga, con o sin descuento)
 * NOTIFICADA  → EN_DESCARGO   (el vecino presenta un descargo)
 * EN_DESCARGO → CONFIRMADA    (el municipio rechaza el descargo)
 * EN_DESCARGO → ANULADA       (el municipio hace lugar al descargo)
 * CONFIRMADA  → PAGADA        (el vecino paga la multa ya confirmada, sin descuento)
 * </pre>
 *
 * <p>{@code PAGADA} y {@code ANULADA} son terminales.
 */
enum EstadoDeMulta {
    NOTIFICADA,
    EN_DESCARGO,
    CONFIRMADA,
    ANULADA,
    PAGADA
}
