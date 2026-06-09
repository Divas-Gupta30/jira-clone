package com.projectboard.domain.model;

import java.time.Instant;

public record Comment(
        String id,
        String issueId,
        UserRef author,
        String body,
        String parentId,
        Instant createdAt
) {}
