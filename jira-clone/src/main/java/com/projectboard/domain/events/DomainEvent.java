package com.projectboard.domain.events;

import java.time.Instant;
import java.util.Map;

public sealed interface DomainEvent permits
        IssueCreated, IssueUpdated, StatusChanged, CommentAdded, SprintUpdated {

    String eventId();
    String projectId();
    Instant occurredAt();
    Map<String, Object> payload();
}
