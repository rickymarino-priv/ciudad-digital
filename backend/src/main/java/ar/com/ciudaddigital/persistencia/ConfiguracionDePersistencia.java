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
 * <p>Desde R5 (ADR 0013 §1), el gestor de transacciones por defecto de la
 * aplicación (sin nombrar) es el de tenant, no el de control como antes:
 * {@code tenantTransactionManager} es {@code @Primary} y
 * {@code controlTransactionManager} no. El motivo es
 * {@code JpaEventPublicationRepository}, la clase de Spring Modulith que
 * marca las filas de {@code event_publication} como procesadas o
 * completadas —código de terceros, no anotable por nosotros—: lleva
 * {@code @Transactional} a nivel de clase sin nombrar gestor, y esa
 * transacción sin nombre tiene que resolver al gestor de tenant, porque el
 * {@link EntityManager} que esa misma clase usa (ver
 * {@code tenantEntityManager}, más abajo) está atado a esa unidad; si
 * resolviera al de control, Hibernate rechazaría sus escrituras con
 * {@code TransactionRequiredException: No active transaction}. El código
 * que necesita el de control lo nombra explícitamente
 * ({@code @Transactional("controlTransactionManager")}) — hoy, únicamente
 * {@code AutenticacionDePlataforma} y
 * {@code SembradorDeUsuarioDePlataforma} en {@code tenants.internal}.
 *
 * <p>El {@link EntityManagerFactory} de control, en cambio,
 * <strong>no</strong> lleva {@code @Primary}, aunque nada de este código lo
 * autowirea sin nombrarlo. La razón es indirecta:
 * {@code LocalContainerEntityManagerFactoryBean} implementa
 * {@code SmartFactoryBean<EntityManagerFactory>}, cuyo
 * {@code getObject(Class)} sabe entregar, para el tipo pedido
 * {@link EntityManager} (no {@code EntityManagerFactory}), el
 * {@code EntityManager} compartido interno del propio bean de EMF. Eso
 * convierte tanto a {@code controlEntityManagerFactory} como a
 * {@code tenantEntityManagerFactory} en candidatos <em>implícitos</em> de
 * tipo {@code EntityManager}, además del bean explícito
 * {@code tenantEntityManager} de más abajo — y un bean de EMF que fuera
 * {@code @Primary} arrastraría esa marca también a su vista implícita como
 * {@code EntityManager}. El registro persistente de eventos de Spring
 * Modulith ({@code event_publication}, ADR 0013 §1) pide exactamente ese
 * tipo por autowiring sin nombrarlo
 * ({@code JpaEventPublicationConfiguration.jpaEventPublicationRepository}),
 * y tiene que resolver el de tenant: por eso
 * {@code controlEntityManagerFactory} no lleva {@code @Primary} —para no
 * competir por esa vista implícita— y {@code tenantEntityManager} sí (ver
 * su Javadoc, más abajo, para el detalle completo). Este mecanismo no
 * afecta a los gestores de transacciones: {@code controlTransactionManager}
 * y {@code tenantTransactionManager} son {@code JpaTransactionManager}
 * comunes que este código instancia directamente, sin ningún
 * {@code SmartFactoryBean} de por medio, así que ahí {@code @Primary} se
 * comporta de forma simple y predecible.
 *
 * <p>Este proyecto no habilita
 * {@code spring.modulith.events.republish-outstanding-events-on-restart}
 * (ADR 0013 §2): esa propiedad depende del default de la aplicación al
 * arrancar el proceso, antes de que exista ningún tenant resuelto, y en
 * esta arquitectura de una base por tenant no hay ninguna consulta sin
 * tenant que pueda encontrar algo que reintentar en ninguna base.
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

    /** Reclamos ciudadanos del municipio (ADR 0014). */
    static final String PAQUETE_RECLAMOS = "ar.com.ciudaddigital.reclamos";

    /** Normas del Boletín Oficial del municipio (backlog R7). */
    static final String PAQUETE_BOLETIN = "ar.com.ciudaddigital.boletin";

    /** Sepulturas del cementerio municipal del municipio (backlog R8). */
    static final String PAQUETE_CEMENTERIO = "ar.com.ciudaddigital.cementerio";

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
            basePackages = {
                    PAQUETE_MUNICIPIO, PAQUETE_ACCESO, PAQUETE_AUDITORIA, PAQUETE_RECLAMOS, PAQUETE_BOLETIN,
                    PAQUETE_CEMENTERIO },
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
     * Sin @Primary acá a propósito (ver el Javadoc de la clase): este EMF
     * es, él mismo, un candidato implícito de tipo EntityManager (vía
     * SmartFactoryBean), y esa marca "sangraría" hacia esa vista implícita.
     * Nada en este proyecto autowirea EntityManagerFactory sin nombrarlo
     * —todo usa @Qualifier("controlEntityManagerFactory") o
     * @Qualifier("tenantEntityManagerFactory")—, así que este @Primary no
     * protegería nada propio; sí le haría perder a tenantEntityManager la
     * resolución del EntityManager que necesita Spring Modulith.
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

    /**
     * Sin {@code @Primary} (ver el Javadoc de la clase): el código de
     * control que necesita este gestor lo nombra explícitamente
     * ({@code @Transactional("controlTransactionManager")}) —
     * {@code AutenticacionDePlataforma} y
     * {@code SembradorDeUsuarioDePlataforma}, en {@code tenants.internal}—,
     * porque el default de la aplicación pasa a ser el de tenant.
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
        emf.setPackagesToScan(
                PAQUETE_MUNICIPIO, PAQUETE_ACCESO, PAQUETE_AUDITORIA, PAQUETE_RECLAMOS, PAQUETE_BOLETIN,
                PAQUETE_CEMENTERIO, PAQUETE_EVENTOS);
        emf.setPersistenceUnitName("tenant");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        emf.setJpaPropertyMap(propiedadesDeTenant());
        return emf;
    }

    /**
     * {@code @Primary} (ADR 0013 §1): {@code JpaEventPublicationRepository}
     * —la clase de Spring Modulith que marca {@code event_publication} como
     * procesada o completada, no anotable por nosotros— lleva
     * {@code @Transactional} a nivel de clase sin nombrar gestor, y esa
     * transacción tiene que cubrir el {@link EntityManager} de tenant que
     * usa (ver {@code tenantEntityManager}, más abajo) o Hibernate rechaza
     * sus escrituras con {@code TransactionRequiredException}. A diferencia
     * del EMF de tenant (ver el Javadoc de la clase), este bean es un
     * {@link JpaTransactionManager} común, sin ningún {@code SmartFactoryBean}
     * de por medio: marcarlo {@code @Primary} no tiene ningún efecto
     * colateral sobre otro tipo de bean. El código de control que necesita
     * este gestor lo nombra explícitamente
     * ({@code @Transactional("controlTransactionManager")}, ver
     * {@code controlTransactionManager} más arriba).
     */
    @Bean
    @Primary
    PlatformTransactionManager tenantTransactionManager(
            @org.springframework.beans.factory.annotation.Qualifier("tenantEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    /**
     * {@code JpaEventPublicationConfiguration} de Spring Modulith pide un
     * {@link EntityManager} por autowiring de tipo, sin nombrarlo, para
     * poder leer y escribir {@code event_publication}. No es el único
     * candidato: {@code LocalContainerEntityManagerFactoryBean} implementa
     * {@code SmartFactoryBean<EntityManagerFactory>}, cuyo
     * {@code getObject(Class)} también sabe entregar, para el tipo
     * {@code EntityManager}, el {@code EntityManager} compartido interno del
     * propio bean de EMF — es decir, tanto {@code controlEntityManagerFactory}
     * como {@code tenantEntityManagerFactory} son, ellos mismos, candidatos
     * implícitos de tipo {@code EntityManager}, además de este bean. Con
     * {@code controlEntityManagerFactory} sin {@code @Primary} (ver el
     * Javadoc de la clase y el de ese bean), este es el único candidato
     * {@code @Primary} de tipo {@code EntityManager} en el contexto, así que
     * gana la elección sin ambigüedad y Spring Modulith termina resolviendo
     * el {@code EntityManager} de tenant — que es el que necesita, porque
     * {@code event_publication} vive en la base de cada municipio
     * (ADR 0013 §1).
     */
    @Bean
    @Primary
    EntityManager tenantEntityManager(
            @org.springframework.beans.factory.annotation.Qualifier("tenantEntityManagerFactory") EntityManagerFactory emf) {
        return SharedEntityManagerCreator.createSharedEntityManager(emf);
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
