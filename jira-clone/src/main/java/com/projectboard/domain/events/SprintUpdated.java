package com.projectboard.domain.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SprintUpdated(
        String eventId,
        String projectId,
        String issueId,
        Instant occurredAt,
        Map<String, Object> payload
) implements DomainEvent {

    public SprintUpdated(String projectId, String sprintId, Map<String, Object> payload) {
        this(UUID.randomUUID().toString(), projectId, sprintId, Instant.now(), payload);
    }
}
