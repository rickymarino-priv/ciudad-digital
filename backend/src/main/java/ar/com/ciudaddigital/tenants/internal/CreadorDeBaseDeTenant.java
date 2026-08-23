package ar.com.ciudaddigital.tenants.internal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.ConfiguracionDeBasesDeDatos.Tenants;

/**
 * Crea la base de datos física de un municipio nuevo (ADR 0005).
 *
 * <p>Se conecta a la base de mantenimiento porque {@code CREATE DATABASE}
 * no puede ejecutarse desde la base que se está creando, y tampoco dentro
 * de una transacción — por eso no usa el pool de la aplicación.
 */
@Component
class CreadorDeBaseDeTenant {

    /**
     * Nombres de base admitidos.
     *
     * <p>El nombre de una base no se puede pasar como parámetro de una
     * sentencia preparada: va concatenado. Restringirlo a este patrón es lo
     * que evita que un slug malicioso se convierta en SQL.
     */
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^[a-z][a-z0-9_]{2,60}$");

    private final Tenants config;

    CreadorDeBaseDeTenant(Tenants config) {
        this.config = config;
    }

    void crear(String nombreBaseDatos) {
        exigirNombreValido(nombreBaseDatos);

        try (Connection conexion = DriverManager.getConnection(
                config.urlDeMantenimiento(), config.usuario(), config.password());
                Statement sentencia = conexion.createStatement()) {

            sentencia.executeUpdate("create database \"" + nombreBaseDatos + "\"");

        } catch (SQLException e) {
            throw new AprovisionamientoFallido(
                    "No se pudo crear la base del municipio: " + e.getMessage(), e);
        }
    }

    boolean existe(String nombreBaseDatos) {
        exigirNombreValido(nombreBaseDatos);

        try (Connection conexion = DriverManager.getConnection(
                config.urlDeMantenimiento(), config.usuario(), config.password());
                var consulta = conexion.prepareStatement(
                        "select 1 from pg_database where datname = ?")) {

            consulta.setString(1, nombreBaseDatos);
            try (ResultSet resultado = consulta.executeQuery()) {
                return resultado.next();
            }

        } catch (SQLException e) {
            throw new AprovisionamientoFallido(
                    "No se pudo verificar la base del municipio: " + e.getMessage(), e);
        }
    }

    private void exigirNombreValido(String nombreBaseDatos) {
        if (nombreBaseDatos == null || !NOMBRE_VALIDO.matcher(nombreBaseDatos).matches()) {
            throw new AprovisionamientoFallido(
                    "Nombre de base inválido para un municipio: " + nombreBaseDatos);
        }
    }
}
