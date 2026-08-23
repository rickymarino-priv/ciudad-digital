package ar.com.ciudaddigital.tenants.internal;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.ConfiguracionDeBasesDeDatos.Tenants;

/**
 * Aplica el esquema de municipio a la base de un tenant.
 *
 * <p>Las migraciones de {@code db/tenant} son independientes de las de la
 * base de control: cada base de municipio lleva su propio historial, y por
 * eso cada tenant puede estar en una versión distinta sin que eso rompa a
 * los demás (ADR 0001).
 */
@Component
class MigradorDeTenant {

    private static final Logger log = LoggerFactory.getLogger(MigradorDeTenant.class);

    private static final String UBICACION_MIGRACIONES = "classpath:db/tenant";

    private final Tenants config;

    MigradorDeTenant(Tenants config) {
        this.config = config;
    }

    /** Migra la base de un municipio y devuelve la versión que quedó aplicada. */
    String migrar(String nombreBaseDatos) {
        try {
            var resultado = flywayDe(nombreBaseDatos).migrate();
            return resultado.targetSchemaVersion != null
                    ? resultado.targetSchemaVersion
                    : versionActual(nombreBaseDatos);
        } catch (RuntimeException e) {
            throw new AprovisionamientoFallido(
                    "Falló la migración de la base del municipio: " + e.getMessage(), e);
        }
    }

    /**
     * Versión de esquema aplicada, o {@code null} si no se pudo determinar
     * (la base no existe, no responde, o todavía no tiene migraciones).
     *
     * <p>No propaga el error a propósito: esto alimenta el reporte de
     * estado de <em>todos</em> los municipios, y un municipio con la base
     * rota tiene que aparecer como tal en el listado, no tumbar la consulta
     * y esconder a los demás.
     */
    String versionActual(String nombreBaseDatos) {
        try {
            MigrationInfo aplicada = flywayDe(nombreBaseDatos).info().current();
            return aplicada == null || aplicada.getVersion() == null
                    ? null
                    : aplicada.getVersion().getVersion();
        } catch (RuntimeException e) {
            log.warn("No se pudo leer la versión de esquema de la base {}: {}",
                    nombreBaseDatos, e.getMessage());
            return null;
        }
    }

    private Flyway flywayDe(String nombreBaseDatos) {
        return Flyway.configure()
                .dataSource(config.urlDe(nombreBaseDatos), config.usuario(), config.password())
                .locations(UBICACION_MIGRACIONES)
                .load();
    }
}
