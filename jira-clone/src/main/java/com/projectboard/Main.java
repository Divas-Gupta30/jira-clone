package com.projectboard;

import com.projectboard.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnv();
        Application.Context ctx = Application.create(config);

        Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));

        ctx.app().start(config.port());
        log.info("Server started on port {}", config.port());
    }
}
