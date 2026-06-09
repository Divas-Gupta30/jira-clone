package com.projectboard.domain.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IssueCreated(
        String eventId,
        String projectId,
        String issueId,
        Instant occurredAt,
        Map<String, Object> payload
) implements DomainEvent {

    public IssueCreated(String projectId, String issueId, Map<String, Object> payload) {
        this(UUID.randomUUID().toString(), projectId, issueId, Instant.now(), payload);
    }
}
