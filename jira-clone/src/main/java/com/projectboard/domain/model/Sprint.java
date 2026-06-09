package com.projectboard.domain.model;

import java.time.LocalDate;

public record Sprint(
        String id,
        String projectId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String status
) {}
