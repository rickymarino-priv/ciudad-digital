package ar.com.ciudaddigital.mesaentradas.internal;

/**
 * Estados posibles de un expediente, comunes a todos los tipos de trámite
 * (ADR 0015 §1): cada {@link TipoDeTramite} decide, a través de su propio
 * {@link CircuitoDeTramite}, cuáles de estos estados usa y en qué orden.
 */
enum EstadoDeExpediente {
    INICIADO,
    EN_REVISION,
    APROBADO,
    RECHAZADO
}
