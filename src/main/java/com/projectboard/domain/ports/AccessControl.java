package com.projectboard.domain.ports;

import com.projectboard.domain.model.ProjectRole;

import java.util.Optional;

public interface AccessControl {
    Optional<ProjectRole> roleFor(String projectId, String userId);
    void requireAccess(String projectId, String userId);
    void requireWrite(String projectId, String userId);
    void requireManage(String projectId, String userId);
}
