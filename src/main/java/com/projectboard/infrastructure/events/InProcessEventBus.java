package com.projectboard.infrastructure.events;

import com.projectboard.domain.events.*;
import com.projectboard.domain.ports.EventPublisher;
import com.projectboard.infrastructure.notification.NotificationService;
import com.projectboard.infrastructure.persistence.ActivityRepository;
import com.projectboard.infrastructure.redis.RedisCache;
import com.projectboard.infrastructure.websocket.BoardWebSocketHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

public class InProcessEventBus implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InProcessEventBus.class);

    private final DataSource ds;
    private final ActivityRepository activityRepo;
    private final NotificationService notifications;
    private final BoardWebSocketHub wsHub;
    private final RedisCache cache;

    public InProcessEventBus(DataSource ds, ActivityRepository activityRepo,
                             NotificationService notifications, BoardWebSocketHub wsHub,
                             RedisCache cache) {
        this.ds = ds;
        this.activityRepo = activityRepo;
        this.notifications = notifications;
        this.wsHub = wsHub;
        this.cache = cache;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            persistActivity(event);
            cache.invalidateBoard(event.projectId());
            wsHub.broadcast(event.projectId(), toWsEvent(event));
            notifications.handle(event);
        } catch (Exception e) {
            log.warn("Event handler failed for {}: {}", event.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void persistActivity(DomainEvent event) throws Exception {
        String issueId = event.payload().getOrDefault("issue_id", event.payload().get("sprint_id")).toString();
        if (event instanceof SprintUpdated) issueId = null;

        String type = switch (event) {
            case IssueCreated ignored -> "issue_created";
            case IssueUpdated ignored -> "issue_updated";
            case StatusChanged ignored -> "issue_moved";
            case CommentAdded ignored -> "comment_added";
            case SprintUpdated ignored -> "sprint_updated";
        };

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(true);
            activityRepo.log(conn, event.projectId(), issueId,
                    (String) event.payload().getOrDefault("actor_id", null),
                    type, event.payload());
        }
    }

    private Map<String, Object> toWsEvent(DomainEvent event) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", switch (event) {
            case IssueCreated ignored -> "issue_created";
            case IssueUpdated ignored -> "issue_updated";
            case StatusChanged ignored -> "issue_moved";
            case CommentAdded ignored -> "comment_added";
            case SprintUpdated ignored -> "sprint_updated";
        });
        msg.put("project_id", event.projectId());
        msg.put("payload", event.payload());
        msg.put("occurred_at", event.occurredAt().toString());
        return msg;
    }
}
