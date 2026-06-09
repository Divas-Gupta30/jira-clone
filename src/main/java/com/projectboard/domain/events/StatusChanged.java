package com.projectboard.domain.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StatusChanged(
        String eventId,
        String projectId,
        String issueId,
        Instant occurredAt,
        Map<String, Object> payload
) implements DomainEvent {

    public StatusChanged(String projectId, String issueId, Map<String, Object> payload) {
        this(UUID.randomUUID().toString(), projectId, issueId, Instant.now(), payload);
    }
}
