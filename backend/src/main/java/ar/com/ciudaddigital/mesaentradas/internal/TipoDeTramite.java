package ar.com.ciudaddigital.mesaentradas.internal;

/**
 * Catálogo de trámites que Mesa de Entradas sabe tramitar (ADR 0015 §1).
 *
 * <p>Agregar un tipo de trámite nuevo es agregar un valor acá y su
 * {@link CircuitoDeTramite} propio registrado en {@link CircuitosDeTramite}.
 * El <b>avance de estado</b> es agnóstico al tipo de trámite y no toca el
 * motor ({@code ExpedienteEntity}, {@code MovimientoDeExpedienteEntity},
 * {@link GestionDeExpedientes#avanzar}). El <b>alta</b> no goza hoy de ese
 * desacople: {@link GestionDeExpedientes#iniciar} y el controller exponen
 * los campos propios del único tipo actual ({@code domicilioACertificar})
 * como parámetros explícitos, así que un segundo tipo con campos distintos
 * sí va a requerir tocar esa firma y el controller, hasta que se resuelva
 * el pendiente de ADR 0015 §3 sobre la forma de los datos variables por
 * tipo.
 */
enum TipoDeTramite {
    CERTIFICADO_DOMICILIO
}
