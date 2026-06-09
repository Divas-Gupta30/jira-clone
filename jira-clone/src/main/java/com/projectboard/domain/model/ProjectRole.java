package com.projectboard.domain.model;

public enum ProjectRole {
    ADMIN, PROJECT_LEAD, MEMBER, VIEWER;

    public boolean canWrite() {
        return this != VIEWER;
    }

    public boolean canManageProject() {
        return this == ADMIN || this == PROJECT_LEAD;
    }
}
