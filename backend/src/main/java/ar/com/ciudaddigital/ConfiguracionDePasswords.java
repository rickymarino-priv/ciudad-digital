package ar.com.ciudaddigital;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cómo se hashean las contraseñas en toda la aplicación (ADR 0010).
 *
 * <p>Vive en el paquete raíz, junto a la configuración de bases, porque no
 * es de un módulo en particular: lo usa el módulo de acceso para verificar
 * contraseñas y el de tenants para sembrar el usuario administrador durante
 * el alta, cuando el módulo de acceso todavía no puede intervenir porque no
 * hay ningún municipio resuelto.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionDePasswords {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // bcrypt con el costo por defecto (10). Subirlo es una decisión de
        // capacidad, no de corrección: el hash guarda su propio costo, así
        // que cambiarlo no invalida las contraseñas ya existentes.
        return new BCryptPasswordEncoder();
    }
}
