package ar.com.ciudaddigital.mesaentradas.internal;

/**
 * Estados posibles de un expediente, comunes a todos los tipos de trámite
 * (ADR 0015 §1): cada {@link TipoDeTramite} decide, a través de su propio
 * {@link CircuitoDeTramite}, cuáles de estos estados usa y en qué orden.
 */
enum EstadoDeExpediente {
    INICIADO,
    EN_REVISION,
    // Paso propio del circuito de HABILITACION_COMERCIAL_SIMPLE (backlog
    // R10, ADR 0016): agregar un estado al enum compartido no toca el
    // motor (GestionDeExpedientes.avanzar sigue siendo agnóstico), es
    // exactamente el tipo de extensión que ADR 0015 anticipaba.
    INSPECCION,
    APROBADO,
    RECHAZADO
}
