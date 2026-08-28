package ar.com.ciudaddigital.obras.internal;

/**
 * Ciclo de vida fijo de una obra pública (ADR 0023 §3): enum de cuatro
 * valores con la tabla de transiciones codificada en {@code GestionDeObras},
 * sin entidad de historial ni motor genérico de workflow.
 *
 * <pre>
 * PLANIFICADA  → EN_EJECUCION
 * EN_EJECUCION → PARALIZADA
 * EN_EJECUCION → FINALIZADA
 * PARALIZADA   → EN_EJECUCION
 * </pre>
 *
 * <p>{@code FINALIZADA} es terminal. {@code PARALIZADA} solo vuelve a
 * {@code EN_EJECUCION}: una obra paralizada no se da por finalizada
 * directamente en esta rebanada, se reanuda primero.
 */
enum EstadoDeObra {
    PLANIFICADA,
    EN_EJECUCION,
    PARALIZADA,
    FINALIZADA
}
