package ar.com.ciudaddigital.arbolado.internal;

/**
 * Ciclo de vida fijo del estado sanitario de un árbol urbano (ADR 0024
 * §4): enum de cuatro valores con la tabla de transiciones codificada en
 * {@code GestionDeArbolado}, sin entidad de historial ni motor genérico de
 * workflow.
 *
 * <pre>
 * PLANTADO              → SANO
 * SANO                  → REQUIERE_INTERVENCION
 * REQUIERE_INTERVENCION → SANO
 * REQUIERE_INTERVENCION → RETIRADO
 * </pre>
 *
 * <p>{@code RETIRADO} es terminal. Un árbol {@code SANO} no pasa directo a
 * {@code RETIRADO}: tiene que pasar primero por
 * {@code REQUIERE_INTERVENCION}, para que quede un estado intermedio que
 * documenta que hubo un motivo antes del retiro.
 */
enum EstadoDeArbol {
    PLANTADO,
    SANO,
    REQUIERE_INTERVENCION,
    RETIRADO
}
