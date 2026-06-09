package com.projectboard.infrastructure.notification;

import com.projectboard.domain.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final DataSource ds;
    private final CircuitBreaker breaker = new CircuitBreaker(5);
    private final ConcurrentLinkedQueue<PendingNotification> queue = new ConcurrentLinkedQueue<>();
    private volatile boolean simulateFailure;

    public NotificationService(DataSource ds) {
        this.ds = ds;
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public void handle(DomainEvent event) {
        List<String> recipients = resolveRecipients(event);
        for (String userId : recipients) {
            PendingNotification n = new PendingNotification(userId, eventType(event), event.payload());
            if (breaker.allowRequest()) {
                try {
                    deliver(n);
                    breaker.recordSuccess();
                } catch (Exception e) {
                    breaker.recordFailure();
                    queue.offer(n);
                    log.warn("Notification delivery failed, queued: {}", e.getMessage());
                }
            } else {
                queue.offer(n);
            }
        }
        drainQueue();
    }

    private void deliver(PendingNotification n) throws Exception {
        if (simulateFailure) throw new RuntimeException("notification service unavailable");

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO notifications (id, user_id, type, payload)
                     VALUES (?, ?, ?, ?::jsonb)
                     """)) {
            ps.setString(1, "ntf_" + UUID.randomUUID().toString().substring(0, 8));
            ps.setString(2, n.userId());
            ps.setString(3, n.type());
            ps.setString(4, new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(n.payload()));
            ps.executeUpdate();
        }
    }

    private void drainQueue() {
        if (!breaker.allowRequest()) return;
        PendingNotification n;
        while ((n = queue.poll()) != null) {
            try {
                deliver(n);
                breaker.recordSuccess();
            } catch (Exception e) {
                queue.offer(n);
                breaker.recordFailure();
                break;
            }
        }
    }

    private List<String> resolveRecipients(DomainEvent event) {
        Map<String, Object> p = event.payload();
        return switch (event) {
            case StatusChanged sc -> {
                String assignee = (String) p.get("assignee_id");
                yield assignee != null ? List.of(assignee) : List.of();
            }
            case CommentAdded ca -> {
                @SuppressWarnings("unchecked")
                List<String> mentions = (List<String>) p.getOrDefault("mentions", List.of());
                yield mentions;
            }
            case IssueUpdated iu -> {
                String assignee = (String) p.get("assignee_id");
                yield assignee != null ? List.of(assignee) : List.of();
            }
            default -> List.of();
        };
    }

    private String eventType(DomainEvent event) {
        return switch (event) {
            case StatusChanged ignored -> "status_changed";
            case CommentAdded ignored -> "mention";
            case IssueUpdated ignored -> "assignment";
            default -> "general";
        };
    }

    private record PendingNotification(String userId, String type, Map<String, Object> payload) {}
}
