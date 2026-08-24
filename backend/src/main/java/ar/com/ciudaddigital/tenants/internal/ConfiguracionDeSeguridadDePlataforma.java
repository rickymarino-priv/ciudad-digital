package ar.com.ciudaddigital.tenants.internal;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Cadena de seguridad de la API de administración (ADR 0010).
 *
 * <p>Es una cadena propia, separada de la del portal de municipio
 * ({@code acceso.internal.ConfiguracionDeSeguridad}), con {@code @Order}
 * explícito para que Spring Security la evalúe primero: {@code
 * /api/admin/**} también matchea el patrón {@code /api/**} de la cadena
 * general, así que sin este orden una tapa a la otra según cuál registre
 * el contenedor primero, no según cuál tiene sentido.
 *
 * <p>Dos motivos para que sea una cadena separada y no una rama más
 * adentro de la otra: los usuarios de plataforma son una identidad
 * distinta de los usuarios de municipio —acá no hay tenant resuelto, y no
 * tiene sentido que la sesión de administración comparta nada con ningún
 * portal—, y los límites de módulo de Spring Modulith no dejan que este
 * módulo ({@code tenants}) alcance los internals del módulo {@code
 * acceso} de todas formas.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class ConfiguracionDeSeguridadDePlataforma {

    private static final String API_ADMIN = "/api/admin/**";
    private static final String SESION_ADMIN = "/api/admin/sesion";

    /**
     * Dónde vive el contexto de seguridad entre requests. Nombrado
     * explícitamente para que el controller de login lo pida por nombre:
     * el módulo {@code acceso} define otro bean del mismo tipo para su
     * propia cadena, y sin el nombre Spring no sabría cuál inyectar acá.
     */
    @Bean("repositorioDeContextoDePlataforma")
    SecurityContextRepository repositorioDeContextoDePlataforma() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @Order(1)
    SecurityFilterChain cadenaDeApiDePlataforma(HttpSecurity http,
            @Qualifier("repositorioDeContextoDePlataforma") SecurityContextRepository contexto,
            AutenticacionDePlataforma autenticacion) throws Exception {

        http
                .securityMatcher(API_ADMIN)
                .securityContext(sc -> sc.securityContextRepository(contexto))
                .sessionManagement(sesiones -> sesiones
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilterDePlataforma(), CsrfFilter.class)
                .addFilterBefore(new SesionDePlataformaFilter(autenticacion),
                        AuthorizationFilter.class)
                .authorizeHttpRequests(reglas -> reglas
                        // Entrar y saber si hay sesión tiene que poder
                        // hacerse sin sesión.
                        .requestMatchers(SESION_ADMIN).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((request, response, e) ->
                                RespuestasJsonDePlataforma.error(response, HttpStatus.UNAUTHORIZED,
                                        "Hay que iniciar sesión para operar la API de administración."))
                        .accessDeniedHandler((request, response, e) ->
                                RespuestasJsonDePlataforma.error(response, HttpStatus.FORBIDDEN,
                                        "No tenés permiso para hacer esto.")));

        return http.build();
    }
}
