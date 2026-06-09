package com.projectboard.config;

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
        return new AppConfig(
                env("DB_URL", "jdbc:postgresql://localhost:5432/projectboard"),
                env("DB_USER", "board"),
                env("DB_PASSWORD", "board"),
                env("REDIS_URL", "redis://localhost:6379"),
                Integer.parseInt(env("PORT", "8001")),
                Integer.parseInt(env("DB_POOL_SIZE", "20")),
                env("ADMIN_API_KEY", ""),
                env("PUBLIC_BASE_URL", "")
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
}
