package com.projectboard.infrastructure.persistence;

import com.projectboard.domain.model.Sprint;
import com.projectboard.api.NotFoundException;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class SprintRepository {

    private final DataSource ds;

    public SprintRepository(DataSource ds) {
        this.ds = ds;
    }

    public List<Sprint> listByProject(String projectId) throws SQLException {
        List<Sprint> sprints = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM sprints WHERE project_id = ? ORDER BY created_at")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) sprints.add(map(rs));
            }
        }
        return sprints;
    }

    public Sprint findById(Connection conn, String sprintId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM sprints WHERE id = ?")) {
            ps.setString(1, sprintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NotFoundException("Sprint not found");
                return map(rs);
            }
        }
    }

    public void advisoryLock(Connection conn, String sprintId) throws SQLException {
        long lockKey = Math.abs(sprintId.hashCode());
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            ps.setLong(1, lockKey);
            ps.execute();
        }
    }

    public void startSprint(Connection conn, String sprintId) throws SQLException {
        advisoryLock(conn, sprintId);
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE sprints SET status = 'ACTIVE' WHERE id = ? AND status = 'PLANNED'")) {
            ps.setString(1, sprintId);
            if (ps.executeUpdate() == 0) throw new NotFoundException("Sprint not found or already active");
        }
    }

    public SprintCompleteResult completeSprint(Connection conn, String sprintId, List<String> carryOverIssueIds)
            throws SQLException {
        advisoryLock(conn, sprintId);
        Sprint sprint = findById(conn, sprintId);

        int completedPoints = 0;
        int incompletePoints = 0;
        List<String> incomplete = new ArrayList<>();

        String issueSql = """
            SELECT id, story_points, status_id FROM issues WHERE sprint_id = ?
            """;
        String doneStatusId = doneStatusId(conn, sprint.projectId());

        try (PreparedStatement ps = conn.prepareStatement(issueSql)) {
            ps.setString(1, sprintId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String issueId = rs.getString("id");
                    Integer pts = rs.getObject("story_points") != null ? rs.getInt("story_points") : 0;
                    if (rs.getString("status_id").equals(doneStatusId)) {
                        completedPoints += pts;
                    } else {
                        incompletePoints += pts;
                        incomplete.add(issueId);
                    }
                }
            }
        }

        // move non-carried issues back to backlog
        for (String issueId : incomplete) {
            if (!carryOverIssueIds.contains(issueId)) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE issues SET sprint_id = NULL WHERE id = ?")) {
                    ps.setString(1, issueId);
                    ps.executeUpdate();
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE sprints SET status = 'COMPLETED' WHERE id = ?")) {
            ps.setString(1, sprintId);
            ps.executeUpdate();
        }

        return new SprintCompleteResult(sprintId, completedPoints, incompletePoints, incomplete);
    }

    private String doneStatusId(Connection conn, String projectId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM workflow_statuses WHERE project_id = ? AND name = 'Done'")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private Sprint map(ResultSet rs) throws SQLException {
        java.sql.Date start = rs.getDate("start_date");
        java.sql.Date end = rs.getDate("end_date");
        return new Sprint(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("name"),
                start != null ? start.toLocalDate() : null,
                end != null ? end.toLocalDate() : null,
                rs.getString("status")
        );
    }

    public record SprintCompleteResult(
            String sprintId, int completedPoints, int incompletePoints, List<String> incompleteIssueIds
    ) {}
}
