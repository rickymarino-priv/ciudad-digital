package ar.com.ciudaddigital.notificaciones.internal;

/**
 * Forma mínima del motor multicanal previsto para notificaciones (ADR 0013
 * §3, catálogo funcional "Plataforma transversal"). R5 solo implementa
 * {@link CanalDeEmailNotificacion}; SMS, WhatsApp Business API y push
 * quedan como implementaciones futuras de esta misma interfaz, no como
 * código de esta rebanada.
 */
interface CanalDeNotificacion {

    void enviar(Notificacion notificacion);
}
