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
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

import ar.com.ciudaddigital.ConfiguracionDeBasesDeDatos.Control;
import ar.com.ciudaddigital.ConfiguracionDeBasesDeDatos.Tenants;
import jakarta.persistence.EntityManager;
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
 *
 * <p>Desde R5 (ADR 0013), el gestor de transacciones y el
 * {@link EntityManager} <em>por defecto</em> de la aplicación son los de
 * tenant, no los de control como antes: el registro persistente de eventos
 * de Spring Modulith es código de terceros que siempre usa el default sin
 * nombrarlo, y tiene que escribir en la base del municipio. El código que
 * necesita explícitamente el de control lo nombra
 * ({@code @Transactional("controlTransactionManager")}). Los dos
 * envoltorios {@code *TolerantePorAusenciaDeTenant} de este paquete
 * existen porque ese mismo mecanismo de Spring Modulith corre una vez al
 * arrancar el proceso, antes de que exista ningún tenant resuelto — ver su
 * Javadoc para el detalle.
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

    /** Registro de auditoría del municipio (ADR 0013 §3). */
    static final String PAQUETE_AUDITORIA = "ar.com.ciudaddigital.auditoria";

    /**
     * Entidad de Spring Modulith que registra las publicaciones de eventos
     * (tabla {@code event_publication}). Vive en la base de tenant, no en
     * la de control: todo evento hoy nace de una acción dentro del portal
     * de un municipio (ADR 0013 §1).
     */
    static final String PAQUETE_EVENTOS = "org.springframework.modulith.events.jpa";

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
            basePackages = { PAQUETE_MUNICIPIO, PAQUETE_ACCESO, PAQUETE_AUDITORIA },
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

    /*
     * Sin @Primary acá a propósito: Spring, al resolver por tipo un
     * EntityManager ambiguo entre las dos unidades, también evalúa este
     * EMF como candidato (el objeto que produce implementa
     * org.hibernate.SessionFactory, que a su vez extiende
     * EntityManagerFactory) aunque nunca sea, en los hechos, un
     * EntityManager. Si este bean fuera @Primary competiría con
     * tenantEntityManager por esa ambigüedad y Spring Modulith terminaría
     * escribiendo eventos contra la base de control (ADR 0013 §1).
     */
    @Bean
    LocalContainerEntityManagerFactoryBean controlEntityManagerFactory(DataSource controlDataSource) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(controlDataSource);
        emf.setPackagesToScan(PAQUETE_CONTROL);
        emf.setPersistenceUnitName("control");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        emf.setJpaPropertyMap(propiedadesDeControl());
        return emf;
    }

    /*
     * Sin @Primary acá a propósito, desde R5 (ver tenantTransactionManager,
     * más abajo, y el porqué en su comentario): el código de este proyecto
     * que necesita este gestor lo nombra explícitamente
     * ({@code @Transactional("controlTransactionManager")}), como ya hacía
     * el resto del código de tenant.
     */
    @Bean
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
        emf.setPackagesToScan(PAQUETE_MUNICIPIO, PAQUETE_ACCESO, PAQUETE_AUDITORIA, PAQUETE_EVENTOS);
        emf.setPersistenceUnitName("tenant");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        emf.setJpaPropertyMap(propiedadesDeTenant());
        return emf;
    }

    /**
     * {@code @Primary} desde R5 (ADR 0013 §1, §2): el registro persistente
     * de eventos de Spring Modulith —{@code JpaEventPublicationRepository},
     * {@code DefaultEventPublicationRegistry}— es código de terceros que no
     * conoce las dos unidades de persistencia de este proyecto y siempre
     * abre sus propias transacciones sin nombrar un gestor, así que
     * necesita que el default de la aplicación sea el correcto: el de
     * tenant, porque {@code event_publication} vive en la base de cada
     * municipio. El costo es que el código que sí necesita explícitamente
     * el de control ({@code tenants.internal}) lo tiene que nombrar —y ya
     * lo nombra, siguiendo la misma convención que {@code acceso}.
     */
    @Bean
    @Primary
    PlatformTransactionManager tenantTransactionManager(
            @org.springframework.beans.factory.annotation.Qualifier("tenantEntityManagerFactory") EntityManagerFactory emf) {
        return TransactionManagerTolerantePorAusenciaDeTenant.envolver(new JpaTransactionManager(emf));
    }

    /**
     * {@code JpaEventPublicationConfiguration} de Spring Modulith pide un
     * {@link EntityManager} por autowiring de tipo. Sin este bean no habría
     * ninguno en el contexto (los repositorios de arriba se resuelven por
     * {@code EntityManagerFactory}, no por {@code EntityManager}); con dos
     * EMFs en juego, exponer el de tenant explícitamente es lo que evita
     * que Spring Modulith termine, por ambigüedad, sin saber a cuál
     * conectarse — o peor, resolviendo el de control (ADR 0013 §1).
     */
    @Bean
    @Primary
    EntityManager tenantEntityManager(
            @org.springframework.beans.factory.annotation.Qualifier("tenantEntityManagerFactory") EntityManagerFactory emf) {
        EntityManager compartido = SharedEntityManagerCreator.createSharedEntityManager(emf);
        return EntityManagerTolerantePorAusenciaDeTenant.envolver(compartido);
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
