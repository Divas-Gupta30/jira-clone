package com.projectboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectboard.api.HealthRoutes;
import com.projectboard.api.OpenApiRoutes;
import com.projectboard.api.middleware.Middleware;
import com.projectboard.api.v1.AdminRoutes;
import com.projectboard.api.v1.ApiRoutes;
import com.projectboard.application.*;
import com.projectboard.config.AppConfig;
import com.projectboard.config.Database;
import com.projectboard.config.Json;
import com.projectboard.infrastructure.events.InProcessEventBus;
import com.projectboard.infrastructure.notification.NotificationService;
import com.projectboard.infrastructure.persistence.*;
import com.projectboard.infrastructure.redis.RedisCache;
import com.projectboard.infrastructure.websocket.BoardWebSocketHub;
import io.javalin.Javalin;
import org.slf4j.MDC;

import javax.sql.DataSource;

public final class Application {

    private Application() {}

    public record Context(Javalin app, DataSource dataSource, RedisCache cache) implements AutoCloseable {
        @Override
        public void close() {
            app.stop();
            cache.close();
            if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hds) {
                hds.close();
            }
        }
    }

    public static Context create(AppConfig config) {
        DataSource ds = Database.create(config);
        Database.migrate(ds);

        ObjectMapper mapper = Json.mapper();
        RedisCache cache = new RedisCache(config.redisUrl(), mapper);
        BoardWebSocketHub wsHub = new BoardWebSocketHub(mapper);

        IssueWriteRepository issueWriteRepo = new IssueWriteRepository(ds);
        BoardReadRepository boardReadRepo = new BoardReadRepository(ds);
        WorkflowRepository workflowRepo = new WorkflowRepository(ds);
        SprintRepository sprintRepo = new SprintRepository(ds);
        CommentRepository commentRepo = new CommentRepository(ds);
        ActivityRepository activityRepo = new ActivityRepository(ds, mapper);
        SearchRepository searchRepo = new SearchRepository(ds);
        AccessControlRepository accessRepo = new AccessControlRepository(ds);

        NotificationService notificationService = new NotificationService(ds);
        InProcessEventBus eventBus = new InProcessEventBus(ds, activityRepo, notificationService, wsHub, cache);

        IssueService issueService = new IssueService(ds, issueWriteRepo, boardReadRepo, workflowRepo,
                accessRepo, eventBus, cache);
        SprintService sprintService = new SprintService(ds, sprintRepo, issueWriteRepo, accessRepo, eventBus);
        CommentService commentService = new CommentService(ds, commentRepo, issueWriteRepo, accessRepo, eventBus);
        SearchService searchService = new SearchService(searchRepo, activityRepo, accessRepo);
        AdminSeedService adminSeedService = new AdminSeedService(new SeedAdminRepository(ds), cache);

        HealthRoutes health = new HealthRoutes(ds, wsHub);
        OpenApiRoutes openApi = new OpenApiRoutes(config);
        ApiRoutes api = new ApiRoutes(issueService, sprintService, commentService, searchService);
        AdminRoutes admin = new AdminRoutes(adminSeedService);

        Javalin app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.jsonMapper(new io.javalin.json.JavalinJackson(mapper, false));
        });

        app.before(Middleware.correlationId());
        app.before("/api/v1/admin/*", Middleware.adminAuth(config, accessRepo));
        app.before("/api/*", Middleware.rateLimit(cache, 120));
        app.after(ctx -> MDC.clear());

        health.register(app);
        openApi.register(app);
        api.register(app);
        admin.register(app);

        app.exception(Exception.class, Middleware::handleException);

        app.ws("/ws/board", ws -> {
            ws.onConnect(ctx -> {
                health.markWsConnect();
                wsHub.onConnect(ctx);
            });
            ws.onMessage(ctx -> wsHub.onMessage(ctx));
            ws.onClose(ctx -> wsHub.onClose(ctx));
        });

        return new Context(app, ds, cache);
    }
}
