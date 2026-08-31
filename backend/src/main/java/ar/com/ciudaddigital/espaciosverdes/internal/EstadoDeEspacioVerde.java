package ar.com.ciudaddigital.espaciosverdes.internal;

/**
 * Ciclo de vida fijo del estado de un espacio verde (ADR 0029 §5): enum de
 * tres valores con la tabla de transiciones codificada en
 * {@code GestionDeEspaciosVerdes}, sin entidad de historial ni motor
 * genérico de workflow.
 *
 * <pre>
 * DISPONIBLE       → EN_MANTENIMIENTO
 * EN_MANTENIMIENTO → DISPONIBLE
 * EN_MANTENIMIENTO → CERRADO
 * </pre>
 *
 * <p>{@code CERRADO} es terminal. Un espacio {@code DISPONIBLE} no pasa
 * directo a {@code CERRADO}: tiene que pasar primero por
 * {@code EN_MANTENIMIENTO}, para que quede un estado intermedio que
 * documenta que hubo un motivo antes del cierre.
 */
enum EstadoDeEspacioVerde {
    DISPONIBLE,
    EN_MANTENIMIENTO,
    CERRADO
}
