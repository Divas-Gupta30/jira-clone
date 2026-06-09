package com.projectboard.api.middleware;

import com.projectboard.api.AppException;
import com.projectboard.api.ConflictException;
import com.projectboard.api.ForbiddenException;
import com.projectboard.api.ValidationException;
import com.projectboard.config.AppConfig;
import com.projectboard.infrastructure.persistence.AccessControlRepository;
import com.projectboard.infrastructure.redis.RedisCache;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Middleware {

    private static final Logger log = LoggerFactory.getLogger(Middleware.class);

    private Middleware() {}

    public static Handler correlationId() {
        return ctx -> {
            String id = ctx.header("X-Correlation-Id");
            if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
            ctx.attribute("correlationId", id);
            MDC.put("correlationId", id);
            ctx.header("X-Correlation-Id", id);
        };
    }

    public static Handler auth() {
        return ctx -> {
            if (ctx.path().startsWith("/api/v1/admin")) {
                return;
            }
            String userId = ctx.header("X-User-Id");
            if (userId == null || userId.isBlank()) {
                error(ctx, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing X-User-Id header");
                ctx.skipRemainingHandlers();
                return;
            }
            ctx.attribute("userId", userId);
        };
    }

    public static Handler adminAuth(AppConfig config, AccessControlRepository access) {
        return ctx -> {
            String apiKey = ctx.header("X-Admin-Key");
            if (config.hasAdminApiKey() && config.adminApiKey().equals(apiKey)) {
                String userId = ctx.header("X-User-Id");
                ctx.attribute("userId", userId != null && !userId.isBlank() ? userId : "system");
                return;
            }

            String userId = ctx.header("X-User-Id");
            if (userId == null || userId.isBlank()) {
                error(ctx, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Missing X-User-Id header or valid X-Admin-Key");
                ctx.skipRemainingHandlers();
                return;
            }
            if (!access.isAnyAdmin(userId)) {
                error(ctx, HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin access required");
                ctx.skipRemainingHandlers();
                return;
            }
            ctx.attribute("userId", userId);
        };
    }

    public static Handler rateLimit(RedisCache cache, int maxPerMinute) {
        return ctx -> {
            String userId = ctx.attribute("userId");
            String key = "rl:" + userId + ":" + ctx.method() + ":" + ctx.path();
            if (!cache.checkRateLimit(key, maxPerMinute, Duration.ofMinutes(1))) {
                error(ctx, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests");
                ctx.skipRemainingHandlers();
            }
        };
    }

    public static void handleException(Exception e, Context ctx) {
        MDC.put("correlationId", ctx.attribute("correlationId"));
        String corrId = ctx.attribute("correlationId");

        if (e instanceof AppException app) {
            HttpStatus status;
            if (app instanceof ConflictException) {
                status = HttpStatus.CONFLICT;
            } else if (app instanceof ValidationException) {
                status = HttpStatus.UNPROCESSABLE_CONTENT;
            } else if (app instanceof ForbiddenException) {
                status = HttpStatus.FORBIDDEN;
            } else {
                status = HttpStatus.NOT_FOUND;
            }
            Map<String, Object> body = errorBody(app.code(), app.getMessage(), corrId);
            if (app instanceof ValidationException ve && !ve.allowedTransitions().isEmpty()) {
                body.put("allowed_transitions", ve.allowedTransitions());
            }
            if (app instanceof ConflictException ce) {
                body.put("current", ce.current());
            }
            ctx.status(status).json(body);
            return;
        }

        log.error("Unhandled error", e);
        ctx.status(500).json(errorBody("INTERNAL_ERROR", "Internal server error", corrId));
    }

    public static void error(Context ctx, HttpStatus status, String code, String message) {
        ctx.status(status).json(errorBody(code, message, ctx.attribute("correlationId")));
    }

    private static Map<String, Object> errorBody(String code, String message, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        body.put("correlation_id", correlationId);
        return body;
    }
}
