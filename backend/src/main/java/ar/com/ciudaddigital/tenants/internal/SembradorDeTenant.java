package ar.com.ciudaddigital.tenants.internal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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

    private final Tenants config;

    SembradorDeTenant(Tenants config) {
        this.config = config;
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
}
