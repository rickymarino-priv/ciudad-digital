package ar.com.ciudaddigital.desarrollosocial.internal;

/**
 * Ciclo de vida fijo de una inscripción a un programa social (ADR 0025
 * §8): {@code RECIBIDA → EN_EVALUACION → APROBADA | RECHAZADA}, con
 * {@code APROBADA}/{@code RECHAZADA} terminales. A diferencia de Multas,
 * no hay una vía de resolver directo desde el estado inicial: siempre se
 * pasa primero por {@code EN_EVALUACION}, para dejar rastro de que hubo
 * una revisión deliberada antes de decidir sobre una ayuda social.
 */
enum EstadoDeInscripcion {
    RECIBIDA,
    EN_EVALUACION,
    APROBADA,
    RECHAZADA
}
