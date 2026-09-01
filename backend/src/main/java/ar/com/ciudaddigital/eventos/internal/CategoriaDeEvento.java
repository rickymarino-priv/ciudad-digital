package ar.com.ciudaddigital.eventos.internal;

/**
 * Clasificación de un evento de la agenda cultural, turística o deportiva
 * (ADR 0030 §2): conjunto chico y estable, más una salida genérica para no
 * bloquear un caso real que no encaje, mismo criterio que
 * {@code TipoDeEspacioVerde}/{@code TipoDeInstitucionEducativa}.
 *
 * <p>No es el mismo enum que {@code TipoDeActividad} de {@code turnos}
 * (que coincide en tres de sus valores pero no tiene {@code OTRA}): cada
 * módulo define su propia clasificación desde cero, la coincidencia de
 * dominio no es motivo para compartir código (ADR 0030 §2).
 */
enum CategoriaDeEvento {
    CULTURA,
    TURISMO,
    DEPORTE,
    OTRA
}
