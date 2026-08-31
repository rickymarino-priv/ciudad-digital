package ar.com.ciudaddigital.educacion.internal;

/**
 * Ciclo de vida fijo de una institución educativa municipal (ADR 0028
 * §4): enum de tres valores con la tabla de transiciones codificada en
 * {@code GestionDeEducacion}, sin entidad de historial ni motor genérico
 * de workflow.
 *
 * <pre>
 * ACTIVA                 → CERRADA_TEMPORALMENTE
 * CERRADA_TEMPORALMENTE  → ACTIVA
 * CERRADA_TEMPORALMENTE  → CERRADA_DEFINITIVAMENTE
 * </pre>
 *
 * <p>{@code CERRADA_DEFINITIVAMENTE} es terminal. Una institución
 * {@code ACTIVA} no pasa directo a {@code CERRADA_DEFINITIVAMENTE}: tiene
 * que pasar primero por {@code CERRADA_TEMPORALMENTE}, para que quede un
 * estado intermedio que documenta que hubo un cierre transitorio antes de
 * la baja definitiva.
 */
enum EstadoDeInstitucion {
    ACTIVA,
    CERRADA_TEMPORALMENTE,
    CERRADA_DEFINITIVAMENTE
}
