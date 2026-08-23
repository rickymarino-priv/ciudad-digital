package ar.com.ciudaddigital.persistencia;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.AbstractDataSource;

import com.zaxxer.hikari.HikariDataSource;

import ar.com.ciudaddigital.ConfiguracionDeBasesDeDatos.Tenants;
import ar.com.ciudaddigital.tenants.TenantContext;
import ar.com.ciudaddigital.tenants.TenantInfo;

/**
 * DataSource que dirige cada conexión a la base del municipio del request
 * en curso (ADR 0001).
 *
 * <p>Si no hay tenant resuelto no elige ninguna base: falla. Un "default"
 * acá significaría escribir los datos de un municipio en la base de otro,
 * que es exactamente la falla que el producto no puede tener.
 */
class DataSourceDeTenants extends AbstractDataSource implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DataSourceDeTenants.class);

    private final Tenants config;
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    DataSourceDeTenants(Tenants config) {
        this.config = config;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delTenantActual().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delTenantActual().getConnection(username, password);
    }

    private DataSource delTenantActual() {
        TenantInfo tenant = TenantContext.requerido();
        return poolDe(tenant.nombreBaseDatos());
    }

    /**
     * Pool de conexiones de una base de tenant, creado la primera vez que
     * se usa.
     *
     * <p>Los pools no se desalojan: con la cantidad de municipios de esta
     * etapa no hace falta, y el ADR 0001 deja anotado que la gestión de
     * pools a escala se resuelve cuando haya un problema medido.
     */
    DataSource poolDe(String nombreBaseDatos) {
        return pools.computeIfAbsent(nombreBaseDatos, base -> {
            log.info("Abriendo pool de conexiones para la base {}", base);
            HikariDataSource pool = new HikariDataSource();
            pool.setJdbcUrl(config.urlDe(base));
            pool.setUsername(config.usuario());
            pool.setPassword(config.password());
            pool.setMaximumPoolSize(config.tamanoDePool());
            pool.setPoolName("tenant-" + base);
            return pool;
        });
    }

    @Override
    public void destroy() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
