package it.tesis.weatherbench.conf.jpa;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariDataSource;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * Configurazione esplicita dello stack Jakarta Persistence (JPA 3.1 / Hibernate 6).
 * <p>
 * Catena dei bean:
 * <pre>
 *   HikariDataSource  -->  EntityManagerFactory (persistence unit "weatherbench-jpa")
 *                               |
 *                               +--> EntityManager (proxy condiviso, transaction-scoped)
 *                               |        un Persistence Context per transazione
 *                               +--> JpaTransactionManager (demarcazione @Transactional)
 * </pre>
 * I repository JPA sono confinati nel package {@code repository.jpa}: in questo modo Spring Data
 * non tenta di interpretarli come repository MongoDB (strict repository configuration mode).
 */
@Slf4j
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = JpaConfig.REPOSITORY_PACKAGE,
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager")
public class JpaConfig {

    public static final String REPOSITORY_PACKAGE = "it.tesis.weatherbench.repository.jpa";
    public static final String ENTITY_PACKAGE = "it.tesis.weatherbench.model.entity";
    public static final String PERSISTENCE_UNIT = "weatherbench-jpa";

    /**
     * Pool JDBC. URL, credenziali e parametri Hikari sono letti da {@code spring.datasource.*}
     * (host e porta parametrizzati con MYSQL_HOST / MYSQL_PORT).
     */
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties dataSourceProperties) {
        log.info("Initializing JPA DataSource on {}", dataSourceProperties.getUrl());
        return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /**
     * EntityManagerFactory: oggetto thread-safe e costoso, creato una sola volta. Contiene il
     * metamodello delle entita', la cache dei piani di query e la configurazione del batching JDBC
     * ({@code hibernate.jdbc.batch_size}, {@code order_inserts}).
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource,
            JpaProperties jpaProperties, HibernateProperties hibernateProperties) {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(jpaProperties.isShowSql());

        Map<String, Object> hibernateSettings = hibernateProperties.determineHibernateProperties(
                jpaProperties.getProperties(), new HibernateSettings().ddlAuto(() -> "none"));

        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setPersistenceUnitName(PERSISTENCE_UNIT);
        emf.setDataSource(dataSource);
        emf.setPackagesToScan(ENTITY_PACKAGE);
        emf.setJpaVendorAdapter(vendorAdapter);
        emf.setJpaPropertyMap(hibernateSettings);
        log.info("JPA persistence unit '{}' - hibernate settings: {}", PERSISTENCE_UNIT, hibernateSettings);
        return emf;
    }

    /**
     * Transaction manager JPA. All'avvio di una transazione apre un nuovo EntityManager (e quindi
     * un nuovo Persistence Context) che viene flushato al commit e chiuso al termine.
     * MongoDB non registra alcun transaction manager: opera in modalita' non transazionale.
     */
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
