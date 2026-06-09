package com.projectboard.api;

import com.projectboard.infrastructure.websocket.BoardWebSocketHub;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.common.TextFormat;

import javax.sql.DataSource;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class HealthRoutes {

    private final DataSource ds;
    private final BoardWebSocketHub wsHub;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final Counter httpRequests = Counter.build()
            .name("http_requests_total").help("Total HTTP requests")
            .labelNames("method", "path", "status").register();
    private final Histogram httpLatency = Histogram.build()
            .name("http_request_duration_seconds").help("HTTP latency")
            .labelNames("method", "path").register();
    private final Counter wsConnections = Counter.build()
            .name("ws_connections_total").help("WebSocket connections").register();

    public HealthRoutes(DataSource ds, BoardWebSocketHub wsHub) {
        this.ds = ds;
        this.wsHub = wsHub;
    }

    public void register(Javalin app) {
        app.before(ctx -> {
            if (shuttingDown.get() && !ctx.path().startsWith("/api/health")) {
                ctx.status(503).json(Map.of("error", "SHUTTING_DOWN"));
                ctx.skipRemainingHandlers();
            }
        });

        app.after(ctx -> {
            httpRequests.labels(ctx.method().name(), normalizePath(ctx.path()), String.valueOf(ctx.status())).inc();
        });

        app.get("/api/health/live", ctx -> ctx.json(Map.of("status", "UP")));
        app.get("/api/health/ready", ctx -> {
            if (shuttingDown.get()) {
                ctx.status(503).json(Map.of("status", "SHUTTING_DOWN"));
                return;
            }
            try (var conn = ds.getConnection()) {
                conn.isValid(2);
                ctx.json(Map.of("status", "UP", "db", "connected"));
            } catch (Exception e) {
                ctx.status(503).json(Map.of("status", "DOWN", "db", e.getMessage()));
            }
        });

        app.get("/api/metrics", ctx -> {
            StringWriter writer = new StringWriter();
            TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
            writer.write("# HELP ws_active_connections Active WebSocket connections\n");
            writer.write("# TYPE ws_active_connections gauge\n");
            writer.write("ws_active_connections " + wsHub.connectionCount() + "\n");
            ctx.contentType(TextFormat.CONTENT_TYPE_004);
            ctx.result(writer.toString());
        });
    }

    public Histogram.Timer startTimer(String method, String path) {
        return httpLatency.labels(method, normalizePath(path)).startTimer();
    }

    public void markWsConnect() {
        wsConnections.inc();
    }

    public void beginShutdown() {
        shuttingDown.set(true);
        wsHub.closeAll();
        if (ds instanceof HikariDataSource hds) hds.close();
    }

    private String normalizePath(String path) {
        return path.replaceAll("/[a-f0-9-]{8,}", "/:id")
                .replaceAll("/PROJ-\\d+", "/:issueId");
    }
}
