package ar.com.ciudaddigital.tenants.internal;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Traduce el host del request al municipio correspondiente (ADR 0004).
 *
 * <p>La resolución es siempre exacta: si el host no corresponde a ningún
 * tenant, no hay tenant. Nunca hay municipio "por defecto" ni se cae al
 * primero disponible — un error acá serviría los datos de un municipio bajo
 * el dominio de otro.
 */
@Component
class TenantResolver {

    private final TenantRepository repositorio;

    TenantResolver(TenantRepository repositorio) {
        this.repositorio = repositorio;
    }

    Optional<TenantEntity> resolver(String host) {
        String normalizado = normalizar(host);
        if (normalizado.isEmpty()) {
            return Optional.empty();
        }

        // El dominio propio del municipio tiene precedencia sobre el
        // subdominio de la plataforma: si un municipio ya migró a su dominio,
        // ese es el que manda.
        Optional<TenantEntity> porDominioPropio =
                repositorio.findByDominioPersonalizadoIgnoreCase(normalizado);
        if (porDominioPropio.isPresent()) {
            return porDominioPropio;
        }

        return primerLabel(normalizado).flatMap(repositorio::findBySubdominioIgnoreCase);
    }

    /**
     * Deja el host comparable: sin puerto, en minúsculas y sin el punto final
     * de la forma absoluta ({@code sanmartin.localhost.}).
     */
    private String normalizar(String host) {
        if (host == null) {
            return "";
        }
        String limpio = host.trim().toLowerCase(Locale.ROOT);

        // IPv6 entre corchetes: no es un host de tenant, se descarta entero.
        if (limpio.startsWith("[")) {
            return "";
        }
        int puerto = limpio.indexOf(':');
        if (puerto >= 0) {
            limpio = limpio.substring(0, puerto);
        }
        if (limpio.endsWith(".")) {
            limpio = limpio.substring(0, limpio.length() - 1);
        }
        return limpio;
    }

    /**
     * Primer label del host, que es el subdominio del municipio. Un host de
     * un solo label (ej. {@code localhost}) no identifica a ningún tenant.
     */
    private Optional<String> primerLabel(String host) {
        int punto = host.indexOf('.');
        if (punto <= 0 || punto == host.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(host.substring(0, punto));
    }
}
