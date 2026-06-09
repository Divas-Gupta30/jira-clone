package com.projectboard.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

public final class Database {

    private Database() {}

    public static DataSource create(AppConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.dbUrl());
        hc.setUsername(config.dbUser());
        hc.setPassword(config.dbPassword());
        hc.setMaximumPoolSize(config.dbPoolSize());
        hc.setMinimumIdle(2);
        hc.setConnectionTimeout(5000);
        hc.setPoolName("board-pool");
        return new HikariDataSource(hc);
    }

    public static void migrate(DataSource ds) {
        var config = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration");

        if ("true".equalsIgnoreCase(System.getenv("FLYWAY_REPAIR"))) {
            config.load().repair();
        }

        config.load().migrate();
    }
}
