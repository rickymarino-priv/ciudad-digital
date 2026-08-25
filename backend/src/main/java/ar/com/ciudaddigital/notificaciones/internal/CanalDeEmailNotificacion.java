package ar.com.ciudaddigital.notificaciones.internal;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Único {@link CanalDeNotificacion} implementado en R5, sobre
 * {@code JavaMailSender} sin autenticación (Mailpit en desarrollo, ver
 * {@code docker-compose.yml}).
 */
@Component
class CanalDeEmailNotificacion implements CanalDeNotificacion {

    private final JavaMailSender mailSender;

    CanalDeEmailNotificacion(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void enviar(Notificacion notificacion) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(notificacion.destinatario());
        mensaje.setSubject(notificacion.asunto());
        mensaje.setText(notificacion.cuerpo());
        mailSender.send(mensaje);
    }
}
