package com.projectboard.infrastructure.persistence;

import com.projectboard.api.ForbiddenException;
import com.projectboard.api.NotFoundException;
import com.projectboard.domain.model.ProjectRole;
import com.projectboard.domain.ports.AccessControl;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

public class AccessControlRepository implements AccessControl {

    private final DataSource ds;

    public AccessControlRepository(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public Optional<ProjectRole> roleFor(String projectId, String userId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT role FROM project_members WHERE project_id = ? AND user_id = ?")) {
            ps.setString(1, projectId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(ProjectRole.valueOf(rs.getString(1)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void requireAccess(String projectId, String userId) {
        if (roleFor(projectId, userId).isEmpty()) {
            throw new ForbiddenException("No access to project");
        }
    }

    @Override
    public void requireWrite(String projectId, String userId) {
        ProjectRole role = roleFor(projectId, userId)
                .orElseThrow(() -> new ForbiddenException("No access to project"));
        if (!role.canWrite()) throw new ForbiddenException("Read-only access");
    }

    @Override
    public void requireManage(String projectId, String userId) {
        ProjectRole role = roleFor(projectId, userId)
                .orElseThrow(() -> new ForbiddenException("No access to project"));
        if (!role.canManageProject()) throw new ForbiddenException("Insufficient permissions");
    }

    public boolean isAnyAdmin(String userId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM project_members WHERE user_id = ? AND role = 'ADMIN' LIMIT 1")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String projectIdForIssue(String issueId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT project_id FROM issues WHERE id = ?")) {
            ps.setString(1, issueId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NotFoundException("Issue not found");
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
