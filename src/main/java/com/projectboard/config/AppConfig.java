package com.projectboard.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public record AppConfig(
        String dbUrl,
        String dbUser,
        String dbPassword,
        String redisUrl,
        int port,
        int dbPoolSize,
        String adminApiKey,
        String publicBaseUrl
) {
    public static AppConfig fromEnv() {
        DbSettings db = DbSettings.resolve();
        return new AppConfig(
                db.jdbcUrl(),
                db.user(),
                db.password(),
                env("REDIS_URL", "redis://localhost:6379"),
                Integer.parseInt(env("PORT", "8001")),
                Integer.parseInt(env("DB_POOL_SIZE", "20")),
                env("ADMIN_API_KEY", ""),
                firstNonBlank(env("PUBLIC_BASE_URL", null), env("RENDER_EXTERNAL_URL", ""))
        );
    }

    public boolean hasAdminApiKey() {
        return adminApiKey != null && !adminApiKey.isBlank();
    }

    public boolean hasPublicBaseUrl() {
        return publicBaseUrl != null && !publicBaseUrl.isBlank();
    }

    private static String env(String key, String fallback) {
        String val = System.getenv(key);
        return val != null && !val.isBlank() ? val : fallback;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return "";
    }

    private record DbSettings(String jdbcUrl, String user, String password) {

        static DbSettings resolve() {
            String dbUrl = System.getenv("DB_URL");
            if (dbUrl != null && !dbUrl.isBlank()) {
                if (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://")) {
                    return fromPostgresUri(dbUrl);
                }
                return new DbSettings(
                        ensureSsl(dbUrl),
                        env("DB_USER", "board"),
                        env("DB_PASSWORD", "board")
                );
            }
            String databaseUrl = System.getenv("DATABASE_URL");
            if (databaseUrl != null && !databaseUrl.isBlank()) {
                return fromPostgresUri(databaseUrl);
            }
            return new DbSettings(
                    "jdbc:postgresql://localhost:5432/projectboard",
                    env("DB_USER", "board"),
                    env("DB_PASSWORD", "board")
            );
        }

        private static DbSettings fromPostgresUri(String uri) {
            String normalized = uri.replaceFirst("^postgres://", "postgresql://");
            URI parsed;
            try {
                parsed = URI.create(normalized);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid Postgres URL", e);
            }

            String host = parsed.getHost();
            int port = parsed.getPort() > 0 ? parsed.getPort() : 5432;
            String dbName = parsed.getPath().replaceFirst("^/", "");
            if (dbName.isBlank()) {
                throw new IllegalStateException("Postgres URL missing database name");
            }

            String user = "board";
            String password = "";
            String userInfo = parsed.getUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                int colon = userInfo.indexOf(':');
                if (colon >= 0) {
                    user = decode(userInfo.substring(0, colon));
                    password = decode(userInfo.substring(colon + 1));
                } else {
                    user = decode(userInfo);
                }
            }

            String jdbc = "jdbc:postgresql://" + host + ":" + port + "/" + dbName + "?sslmode=require";
            return new DbSettings(jdbc, user, password);
        }

        private static String ensureSsl(String jdbcUrl) {
            if (jdbcUrl.contains("localhost") || jdbcUrl.contains("127.0.0.1")) {
                return jdbcUrl;
            }
            if (jdbcUrl.contains("sslmode=")) {
                return jdbcUrl;
            }
            return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=require";
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }

        private static String env(String key, String fallback) {
            String val = System.getenv(key);
            return val != null && !val.isBlank() ? val : fallback;
        }
    }
}
