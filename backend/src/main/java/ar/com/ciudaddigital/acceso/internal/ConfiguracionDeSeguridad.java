package ar.com.ciudaddigital.acceso.internal;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
 * Cadena de seguridad del portal de municipio (ADR 0010, ADR 0011).
 *
 * <p>La sesión es server-side y viaja en una cookie {@code HttpOnly}
 * host-only. Nada de esto elige municipio: para cuando la cadena corre, el
 * tenant ya fue resuelto por el {@code Host}, y de atar la sesión a ese
 * municipio se encarga {@link SesionDelMunicipioFilter}.
 *
 * <p>{@code /api/admin/**} no pasa por acá: tiene su propia cadena en
 * {@code tenants.internal.ConfiguracionDeSeguridadDePlataforma}, con
 * {@code @Order} menor para que se evalúe primero.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
class ConfiguracionDeSeguridad {

    /**
     * Dónde vive el contexto de seguridad entre requests.
     *
     * <p>Es un bean para que el login pueda guardar el contexto en el mismo
     * lugar del que después lo lee la cadena: si fueran dos instancias con
     * configuración distinta, el login "andaría" y la sesión no existiría
     * en el request siguiente.
     */
    @Bean("repositorioDeContexto")
    SecurityContextRepository repositorioDeContexto() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @Order(2)
    SecurityFilterChain cadenaDeApi(HttpSecurity http,
            @Qualifier("repositorioDeContexto") SecurityContextRepository contexto,
            AutenticacionDeMunicipio autenticacion) throws Exception {

        http
                .securityMatcher("/api/**")
                .securityContext(sc -> sc.securityContextRepository(contexto))
                .sessionManagement(sesiones -> sesiones
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(csrf -> csrf
                        // La cookie tiene que poder leerse desde JavaScript:
                        // es el frontend el que devuelve el token en la
                        // cabecera. La cookie de sesión, en cambio, sigue
                        // siendo HttpOnly.
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Sin XOR por request: el frontend manda tal cual el
                        // valor de la cookie, que es lo que hace que este
                        // esquema sea usable desde una SPA.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(new SesionDelMunicipioFilter(autenticacion),
                        AuthorizationFilter.class)
                .authorizeHttpRequests(reglas -> reglas
                        // Marca y datos de contacto del municipio: es lo que
                        // ve cualquier vecino que entra al portal.
                        .requestMatchers(HttpMethod.GET, "/api/tenant/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/municipio/**").permitAll()
                        // Entrar y saber si hay sesión tiene que poder
                        // hacerse sin sesión.
                        .requestMatchers(HttpMethod.GET, "/api/sesion").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sesion").permitAll()
                        // Catálogo de módulos (ADR 0012 §7) y ping del
                        // módulo de ejemplo: el gating por entitlement es lo
                        // único que los protege, no la sesión.
                        .requestMatchers(HttpMethod.GET, "/api/modulos").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/ejemplo/ping").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((request, response, e) -> RespuestasJson.error(
                                response, HttpStatus.UNAUTHORIZED,
                                "Hay que iniciar sesión para acceder a esto."))
                        .accessDeniedHandler((request, response, e) -> RespuestasJson.error(
                                response, HttpStatus.FORBIDDEN,
                                "No tenés permiso para hacer esto.")));

        return http.build();
    }
}
