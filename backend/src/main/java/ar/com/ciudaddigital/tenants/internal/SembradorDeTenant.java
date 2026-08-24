package ar.com.ciudaddigital.tenants.internal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.ConfiguracionDeBasesDeDatos.Tenants;

/**
 * Deja la base de un municipio recién creada con sus datos iniciales
 * (paso 3 del alta, ADR 0005).
 *
 * <p>Escribe por JDBC directo y no por el módulo {@code municipio} a
 * propósito: durante el alta todavía no hay ningún tenant resuelto, así
 * que el datasource ruteado no puede saber a qué base ir. La siembra es
 * parte del aprovisionamiento, no lógica de negocio del municipio.
 */
@Component
class SembradorDeTenant {

    /** SQLSTATE de Postgres para índice único violado. */
    private static final String VIOLACION_DE_UNICIDAD = "23505";

    private final Tenants config;
    private final PasswordEncoder encoder;

    SembradorDeTenant(Tenants config, PasswordEncoder encoder) {
        this.config = config;
        this.encoder = encoder;
    }

    void sembrarDatosDeContacto(String nombreBaseDatos, String direccion, String telefono,
            String email) {

        String sql = """
                insert into datos_de_contacto (id, direccion, telefono, email)
                values (1, ?, ?, ?)
                """;

        try (Connection conexion = DriverManager.getConnection(
                config.urlDe(nombreBaseDatos), config.usuario(), config.password());
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {

            sentencia.setString(1, direccion);
            sentencia.setString(2, telefono);
            sentencia.setString(3, email);
            sentencia.executeUpdate();

        } catch (SQLException e) {
            throw new AprovisionamientoFallido(
                    "No se pudieron sembrar los datos del municipio: " + e.getMessage(), e);
        }
    }

    /**
     * Crea el usuario administrador del municipio y le da el rol de
     * administrador que sembró la migración (ADR 0010).
     *
     * <p>Un municipio recién dado de alta sin nadie que pueda entrar no
     * sirve para nada, así que esto es parte del alta y no un paso
     * posterior que alguien tenga que acordarse de hacer.
     */
    void sembrarAdministrador(String nombreBaseDatos, String nombre, String email,
            String password) {

        // El usuario y su rol se insertan en una sola sentencia: un
        // administrador sin rol sería un usuario que puede entrar y no
        // puede hacer nada, o sea un municipio igual de inutilizable.
        String sql = """
                with nuevo as (
                    insert into usuario (nombre, email, hash_password)
                    values (?, ?, ?)
                    returning id
                )
                insert into usuario_rol (usuario_id, rol_id)
                select nuevo.id, rol.id
                from nuevo, rol
                where rol.codigo = 'administrador'
                """;

        try (Connection conexion = DriverManager.getConnection(
                config.urlDe(nombreBaseDatos), config.usuario(), config.password());
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {

            sentencia.setString(1, nombre);
            sentencia.setString(2, email);
            sentencia.setString(3, encoder.encode(password));

            if (sentencia.executeUpdate() == 0) {
                throw new AprovisionamientoFallido(
                        "No se pudo asignar el rol de administrador: la base del municipio "
                                + "no tiene los roles de sistema sembrados.");
            }

        } catch (SQLException e) {
            if (VIOLACION_DE_UNICIDAD.equals(e.getSQLState())) {
                throw new AltaDeMunicipio.SolicitudInvalida(
                        "Ya hay un usuario con el correo " + email + " en este municipio.");
            }
            throw new AprovisionamientoFallido(
                    "No se pudo crear el usuario administrador del municipio: "
                            + e.getMessage(), e);
        }
    }
}
