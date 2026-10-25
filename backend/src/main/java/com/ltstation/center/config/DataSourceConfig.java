package com.ltstation.center.config;

import com.ltstation.center.legacy.Naming;
import com.ltstation.center.legacy.Sugar;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deux bases comme dans l'original : la base Center (spring.datasource) et la base locale
 * de la station (station.datasource, StationNEW d'origine).
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties centerDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties centerDataSourceProperties) {
        return centerDataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /** Déclarés explicitement : la présence des beans Station désactiverait l'autoconfiguration. */
    @Bean
    @Primary
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean
    public Sugar centerSugar(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                             @Qualifier("transactionManager") PlatformTransactionManager tm) {
        return new Sugar("center", namedParameterJdbcTemplate, tm, new Naming("center"));
    }

    @Bean
    public Sugar stationSugar(@Qualifier("stationJdbc") NamedParameterJdbcTemplate jdbc,
                              @Qualifier("stationTxManager") PlatformTransactionManager tm) {
        return new Sugar("station", jdbc, tm, new Naming("station"));
    }

    @Bean
    @ConfigurationProperties("station.datasource")
    public DataSourceProperties stationDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public HikariDataSource stationDataSource(@Qualifier("stationDataSourceProperties") DataSourceProperties props) {
        HikariDataSource ds = props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setPoolName("station");
        return ds;
    }

    @Bean
    public NamedParameterJdbcTemplate stationJdbc(@Qualifier("stationDataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }

    @Bean
    public PlatformTransactionManager stationTxManager(@Qualifier("stationDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean
    public TransactionTemplate stationTx(@Qualifier("stationTxManager") PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }
}
