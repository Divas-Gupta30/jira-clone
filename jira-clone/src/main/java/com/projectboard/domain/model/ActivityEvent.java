package com.projectboard.domain.model;

import java.time.Instant;
import java.util.Map;

public record ActivityEvent(
        String id,
        String projectId,
        String issueId,
        String actorId,
        String eventType,
        Map<String, Object> payload,
        Instant createdAt
) {}
