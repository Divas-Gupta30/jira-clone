package com.projectboard.domain.model;

import java.time.Instant;
import java.util.List;

public record Issue(
        String issueId,
        String projectId,
        IssueType type,
        String title,
        String description,
        String status,
        Priority priority,
        int version,
        UserRef assignee,
        UserRef reporter,
        SprintRef sprint,
        List<String> labels,
        Integer storyPoints,
        String parentId,
        List<String> watchers,
        Instant createdAt,
        Instant updatedAt
) {}
