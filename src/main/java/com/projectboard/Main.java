package com.projectboard;

import com.projectboard.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnv();
        log.info("Bootstrapping (db={}, port={})...", config.dbUrl(), config.port());
        long started = System.currentTimeMillis();
        Application.Context ctx = Application.create(config);
        log.info("Bootstrap completed in {}ms", System.currentTimeMillis() - started);

        Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));

        ctx.app().start(config.port());
        log.info("Server listening on port {} — health: /api/health/live", config.port());
    }
}
