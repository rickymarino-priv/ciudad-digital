package ar.com.ciudaddigital.notificaciones.internal;

/**
 * Un mensaje a mandar por algún {@link CanalDeNotificacion}, independiente
 * del medio que lo entrega (ADR 0013 §3, Pendiente de definir).
 *
 * <p>Deliberadamente sin nada específico de email (sin remitente, sin
 * cuerpo HTML): agregar eso ahora sería diseñar el contrato multicanal a
 * ciegas, sobre una forma que hoy solo un canal real (email) obliga a
 * tener.
 */
record Notificacion(String destinatario, String asunto, String cuerpo) {
}
