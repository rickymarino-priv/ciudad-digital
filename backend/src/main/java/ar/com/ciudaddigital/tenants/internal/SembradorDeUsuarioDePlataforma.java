package ar.com.ciudaddigital.tenants.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deja sembrado el primer usuario de plataforma al arrancar, si todavía no
 * hay ninguno (ADR 0010).
 *
 * <p>No hay alta de usuarios de plataforma por API: son pocos, los crea
 * quien tiene acceso a la configuración del servidor, y agregar una API
 * para esto sería construir un ABM para un caso que no lo necesita. Este
 * sembrador resuelve únicamente el arranque en frío — el primer usuario,
 * sin el cual nadie podría operar la administración para crear a los
 * demás a mano en la base.
 */
@Component
class SembradorDeUsuarioDePlataforma {

    private static final Logger log = LoggerFactory.getLogger(SembradorDeUsuarioDePlataforma.class);

    private final UsuarioPlataformaRepository usuarios;
    private final PasswordEncoder encoder;
    private final String nombre;
    private final String email;
    private final String password;

    SembradorDeUsuarioDePlataforma(
            UsuarioPlataformaRepository usuarios,
            PasswordEncoder encoder,
            @Value("${ciudad.plataforma.admin-inicial.nombre}") String nombre,
            @Value("${ciudad.plataforma.admin-inicial.email}") String email,
            @Value("${ciudad.plataforma.admin-inicial.password}") String password) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.nombre = nombre;
        this.email = email;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional("controlTransactionManager")
    void sembrarSiHaceFalta() {
        if (usuarios.count() > 0) {
            return;
        }
        usuarios.save(UsuarioPlataformaEntity.nuevo(nombre, email, encoder.encode(password)));
        log.info("Sembrado el primer usuario de plataforma ({}). "
                + "Cambiá la contraseña de arranque antes de ir a producción.", email);
    }
}
