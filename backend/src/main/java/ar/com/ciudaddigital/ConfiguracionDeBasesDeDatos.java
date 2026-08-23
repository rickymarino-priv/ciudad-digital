package ar.com.ciudaddigital;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coordenadas de conexión a la base de control y a las bases de tenant.
 *
 * <p>Vive en el paquete raíz porque es infraestructura de toda la
 * aplicación, no de un módulo en particular.
 */
public final class ConfiguracionDeBasesDeDatos {

    /** Base de control: el registro de municipios (ADR 0007). */
    @ConfigurationProperties("ciudad.control")
    public record Control(String url, String usuario, String password) {
    }

    /**
     * Bases de los municipios (ADR 0005).
     *
     * <p>Las credenciales son compartidas a nivel aplicación: todas las
     * bases viven en el mismo motor, así que el tenant solo aporta el
     * nombre de su base.
     */
    @ConfigurationProperties("ciudad.tenants")
    public record Tenants(
            String servidor,
            String usuario,
            String password,
            String baseDeMantenimiento,
            int tamanoDePool) {

        /** URL JDBC de la base de un municipio. */
        public String urlDe(String nombreBaseDatos) {
            return servidor + nombreBaseDatos;
        }

        /**
         * URL de la base contra la que se ejecuta {@code CREATE DATABASE}:
         * no se puede crear una base estando conectado a ella.
         */
        public String urlDeMantenimiento() {
            return urlDe(baseDeMantenimiento);
        }
    }

    private ConfiguracionDeBasesDeDatos() {
    }
}
