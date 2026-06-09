package com.projectboard.infrastructure.persistence;

import com.projectboard.domain.model.*;
import com.projectboard.api.NotFoundException;
import com.projectboard.api.ValidationException;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class WorkflowRepository {

    private final DataSource ds;

    public WorkflowRepository(DataSource ds) {
        this.ds = ds;
    }

    public String statusIdByName(Connection conn, String projectId, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM workflow_statuses WHERE project_id = ? AND name = ?")) {
            ps.setString(1, projectId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NotFoundException("Status not found: " + name);
                return rs.getString(1);
            }
        }
    }

    public void validateTransition(Connection conn, String projectId, String fromStatusId, String toStatusId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1 FROM workflow_transitions
                WHERE project_id = ? AND from_status_id = ? AND to_status_id = ?
                """)) {
            ps.setString(1, projectId);
            ps.setString(2, fromStatusId);
            ps.setString(3, toStatusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        List<String> allowed = allowedTransitionNames(conn, projectId, fromStatusId);
        throw new ValidationException("Transition not allowed", allowed);
    }

    public List<String> allowedTransitionNames(Connection conn, String projectId, String fromStatusId)
            throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = """
            SELECT ws.name FROM workflow_transitions t
            JOIN workflow_statuses ws ON ws.id = t.to_status_id
            WHERE t.project_id = ? AND t.from_status_id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, fromStatusId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        }
        return names;
    }

    public Map<String, String> transitionActions(Connection conn, String projectId,
                                                  String fromStatusId, String toStatusId) throws SQLException {
        Map<String, String> actions = new HashMap<>();
        String sql = """
            SELECT ta.action_type, ta.action_value FROM workflow_transitions t
            JOIN transition_actions ta ON ta.transition_id = t.id
            WHERE t.project_id = ? AND t.from_status_id = ? AND t.to_status_id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, fromStatusId);
            ps.setString(3, toStatusId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) actions.put(rs.getString(1), rs.getString(2));
            }
        }
        return actions;
    }
}
