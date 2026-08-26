package ar.com.ciudaddigital.tasas.internal;

/**
 * Ciclo de vida de una tasa (backlog R13): sin estados intermedios como
 * "vencida" o "en gestión", solo si ya se pagó o no. Un pago rechazado no
 * cambia el estado, solo libera el intento de pago para reintentar (ver
 * {@code TasaEntity#confirmarPago}).
 */
enum EstadoDeTasa {
    PENDIENTE,
    PAGADA
}
