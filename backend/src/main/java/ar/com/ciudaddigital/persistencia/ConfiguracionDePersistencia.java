package ar.com.ciudaddigital.persistencia;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

import ar.com.ciudaddigital.ConfiguracionDeBasesDeDatos.Control;
import ar.com.ciudaddigital.ConfiguracionDeBasesDeDatos.Tenants;
import jakarta.persistence.EntityManagerFactory;

/**
 * Las dos unidades de persistencia de la aplicación.
 *
 * <p>La base de control y las bases de municipio tienen esquemas
 * distintos, así que necesitan {@link EntityManagerFactory} separados: un
 * único EMF sobre el datasource ruteado no podría validar ningún esquema,
 * porque las tablas de control no existen en las bases de municipio ni al
 * revés.
 *
 * <p>El reparto es por módulo: las entidades del módulo {@code tenants}
 * van a la base de control, y las de los módulos que trabajan sobre datos
 * de un municipio van a la base del tenant en curso. Un módulo nuevo que
 * guarde datos del municipio se suma a esa lista; no hay descubrimiento
 * automático a propósito, porque equivocarse de unidad de persistencia
 * significa escribir en la base equivocada.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ Control.class, Tenants.class })
class ConfiguracionDePersistencia {

    /** Entidades de la base de control: el registro de municipios. */
    static final String PAQUETE_CONTROL = "ar.com.ciudaddigital.tenants";

    /** Datos del municipio: contacto, y lo que vaya sumando cada módulo. */
    static final String PAQUETE_MUNICIPIO = "ar.com.ciudaddigital.municipio";

    /** Usuarios, roles y permisos del municipio (ADR 0010). */
    static final String PAQUETE_ACCESO = "ar.com.ciudaddigital.acceso";

    /*
     * @EnableJpaRepositories no es repetible, así que cada unidad de
     * persistencia necesita su propia clase de configuración. Es lo que
     * ata cada repositorio a su EntityManagerFactory y a su gestor de
     * transacciones: sin esto, los repositorios de municipio usarían el
     * gestor de la base de control.
     */

    @Configuration(proxyBeanMethods = false)
    @EnableJpaRepositories(
            basePackages = PAQUETE_CONTROL,
            entityManagerFactoryRef = "controlEntityManagerFactory",
            transactionManagerRef = "controlTransactionManager")
    static class RepositoriosDeControl {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJpaRepositories(
            basePackages = { PAQUETE_MUNICIPIO, PAQUETE_ACCESO },
            entityManagerFactoryRef = "tenantEntityManagerFactory",
            transactionManagerRef = "tenantTransactionManager")
    static class RepositoriosDeTenant {
    }

    // --- Base de control ---

    @Bean
    @Primary
    DataSource controlDataSource(Control config) {
        HikariDataSource pool = new HikariDataSource();
        pool.setJdbcUrl(config.url());
        pool.setUsername(config.usuario());
        pool.setPassword(config.password());
        pool.setPoolName("control");
        return pool;
    }

    @Bean
    @Primary
    LocalContainerEntityManagerFactoryBean controlEntityManagerFactory(DataSource controlDataSource) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(controlDataSource);
        emf.setPackagesToScan(PAQUETE_CONTROL);
        emf.setPersistenceUnitName("control");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        emf.setJpaPropertyMap(propiedadesDeControl());
        return emf;
    }

    @Bean
    @Primary
    PlatformTransactionManager controlTransactionManager(
            @org.springframework.beans.factory.annotation.Qualifier("controlEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    private Map<String, Object> propiedadesDeControl() {
        Map<String, Object> propiedades = new HashMap<>();
        // El esquema lo crea Flyway; Hibernate solo verifica que coincida.
        propiedades.put("hibernate.hbm2ddl.auto", "validate");
        return propiedades;
    }

    // --- Bases de municipio ---

    @Bean
    DataSourceDeTenants tenantDataSource(Tenants config) {
        return new DataSourceDeTenants(config);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(DataSourceDeTenants tenantDataSource) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(tenantDataSource);
        emf.setPackagesToScan(PAQUETE_MUNICIPIO, PAQUETE_ACCESO);
        emf.setPersistenceUnitName("tenant");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        emf.setJpaPropertyMap(propiedadesDeTenant());
        return emf;
    }

    @Bean
    PlatformTransactionManager tenantTransactionManager(
            @org.springframework.beans.factory.annotation.Qualifier("tenantEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    private Map<String, Object> propiedadesDeTenant() {
        Map<String, Object> propiedades = new HashMap<>();
        // Al arrancar todavía no hay ningún municipio resuelto, así que
        // este EMF no puede abrir una conexión para inspeccionar el motor:
        // se le dice el dialecto y se le prohíbe consultar metadata.
        propiedades.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        propiedades.put("hibernate.boot.allow_jdbc_metadata_access", "false");
        // El esquema de cada municipio lo crea Flyway durante el alta.
        propiedades.put("hibernate.hbm2ddl.auto", "none");
        return propiedades;
    }
}
