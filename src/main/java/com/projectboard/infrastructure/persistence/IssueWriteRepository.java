package com.projectboard.infrastructure.persistence;

import com.projectboard.domain.model.*;
import com.projectboard.api.NotFoundException;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class IssueWriteRepository {

    private final DataSource ds;

    public IssueWriteRepository(DataSource ds) {
        this.ds = ds;
    }

    public String nextIssueId(Connection conn, String projectId) throws SQLException {
        // Keep counter at least max(issue_number)+1 — handles stale seed/migration state
        try (PreparedStatement sync = conn.prepareStatement("""
                UPDATE issue_counters ic
                SET next_number = GREATEST(
                    ic.next_number,
                    (SELECT COALESCE(MAX(issue_number), 0) + 1 FROM issues WHERE project_id = ?))
                WHERE ic.project_id = ?
                """)) {
            sync.setString(1, projectId);
            sync.setString(2, projectId);
            sync.executeUpdate();
        }

        String sql = """
            UPDATE issue_counters
            SET next_number = next_number + 1
            WHERE project_id = ?
            RETURNING next_number - 1
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No issue counter for project: " + projectId);
                }
                int num = rs.getInt(1);
                String key = projectKey(conn, projectId);
                return key + "-" + num;
            }
        }
    }

    private String projectKey(Connection conn, String projectId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT key FROM projects WHERE id = ?")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NotFoundException("Project not found");
                return rs.getString(1);
            }
        }
    }

    public Issue insert(Connection conn, CreateIssue cmd) throws SQLException {
        String id = nextIssueId(conn, cmd.projectId());
        String statusId = defaultStatusId(conn, cmd.projectId());
        int num = Integer.parseInt(id.split("-")[1]);

        String sql = """
            INSERT INTO issues (id, project_id, issue_number, type, title, description, status_id,
                priority, assignee_id, reporter_id, sprint_id, parent_id, story_points, labels)
            VALUES (?, ?, ?, ?::issue_type, ?, ?, ?, ?::issue_priority, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, cmd.projectId());
            ps.setInt(3, num);
            ps.setString(4, cmd.type().name());
            ps.setString(5, cmd.title());
            ps.setString(6, cmd.description());
            ps.setString(7, statusId);
            ps.setString(8, cmd.priority().name());
            ps.setString(9, cmd.assigneeId());
            ps.setString(10, cmd.reporterId());
            ps.setString(11, cmd.sprintId());
            ps.setString(12, cmd.parentId());
            if (cmd.storyPoints() != null) ps.setInt(13, cmd.storyPoints());
            else ps.setNull(13, Types.INTEGER);
            ps.setArray(14, conn.createArrayOf("text", cmd.labels().toArray()));
            ps.executeUpdate();
        }
        return findById(conn, id);
    }

    public Issue update(Connection conn, UpdateIssue cmd) throws SQLException {
        String sql = """
            UPDATE issues SET title = COALESCE(?, title), description = COALESCE(?, description),
                priority = COALESCE(?::issue_priority, priority), assignee_id = COALESCE(?, assignee_id),
                story_points = COALESCE(?, story_points), labels = COALESCE(?, labels),
                version = version + 1, updated_at = NOW()
            WHERE id = ? AND version = ?
            RETURNING version
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cmd.title());
            ps.setString(2, cmd.description());
            ps.setString(3, cmd.priority() != null ? cmd.priority().name() : null);
            ps.setString(4, cmd.assigneeId());
            if (cmd.storyPoints() != null) ps.setInt(5, cmd.storyPoints());
            else ps.setNull(5, Types.INTEGER);
            if (cmd.labels() != null) {
                ps.setArray(6, conn.createArrayOf("text", cmd.labels().toArray()));
            } else {
                ps.setNull(6, Types.ARRAY);
            }
            ps.setString(7, cmd.issueId());
            ps.setInt(8, cmd.expectedVersion());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
            }
        }
        return findById(conn, cmd.issueId());
    }

    public Issue transition(Connection conn, String issueId, String toStatusId, int expectedVersion) throws SQLException {
        String sql = """
            UPDATE issues SET status_id = ?, version = version + 1, updated_at = NOW()
            WHERE id = ? AND version = ?
            RETURNING id
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toStatusId);
            ps.setString(2, issueId);
            ps.setInt(3, expectedVersion);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
            }
        }
        return findById(conn, issueId);
    }

    public void moveToSprint(Connection conn, String issueId, String sprintId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE issues SET sprint_id = ?, updated_at = NOW() WHERE id = ?")) {
            if (sprintId == null) ps.setNull(1, Types.VARCHAR);
            else ps.setString(1, sprintId);
            ps.setString(2, issueId);
            ps.executeUpdate();
        }
    }

    public Issue findById(Connection conn, String issueId) throws SQLException {
        String sql = """
            SELECT i.*, ws.name AS status_name, p.key,
                   a.id AS assignee_id, a.display_name AS assignee_name,
                   r.id AS reporter_id, r.display_name AS reporter_name,
                   s.id AS sprint_id, s.name AS sprint_name, s.start_date, s.end_date
            FROM issues i
            JOIN workflow_statuses ws ON ws.id = i.status_id
            JOIN projects p ON p.id = i.project_id
            JOIN users r ON r.id = i.reporter_id
            LEFT JOIN users a ON a.id = i.assignee_id
            LEFT JOIN sprints s ON s.id = i.sprint_id
            WHERE i.id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, issueId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NotFoundException("Issue not found: " + issueId);
                return mapIssue(rs, loadWatchers(conn, issueId));
            }
        }
    }

    public String projectIdForIssue(Connection conn, String issueId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT project_id FROM issues WHERE id = ?")) {
            ps.setString(1, issueId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NotFoundException("Issue not found");
                return rs.getString(1);
            }
        }
    }

    private List<String> loadWatchers(Connection conn, String issueId) throws SQLException {
        List<String> watchers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT user_id FROM issue_watchers WHERE issue_id = ?")) {
            ps.setString(1, issueId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) watchers.add(rs.getString(1));
            }
        }
        return watchers;
    }

    private String defaultStatusId(Connection conn, String projectId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM workflow_statuses WHERE project_id = ? ORDER BY position LIMIT 1")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    static Issue mapIssue(ResultSet rs, List<String> watchers) throws SQLException {
        UserRef assignee = rs.getString("assignee_id") != null
                ? new UserRef(rs.getString("assignee_id"), rs.getString("assignee_name"))
                : null;
        UserRef reporter = new UserRef(rs.getString("reporter_id"), rs.getString("reporter_name"));
        SprintRef sprint = rs.getString("sprint_id") != null
                ? new SprintRef(
                        rs.getString("sprint_id"),
                        rs.getString("sprint_name"),
                        rs.getString("start_date"),
                        rs.getString("end_date"))
                : null;
        Array labelsArr = rs.getArray("labels");
        List<String> labels = labelsArr != null
                ? Arrays.asList((String[]) labelsArr.getArray())
                : List.of();
        Integer storyPoints = rs.getObject("story_points") != null ? rs.getInt("story_points") : null;

        return new Issue(
                rs.getString("id"),
                rs.getString("project_id"),
                IssueType.valueOf(rs.getString("type")),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status_name"),
                Priority.valueOf(rs.getString("priority")),
                rs.getInt("version"),
                assignee,
                reporter,
                sprint,
                labels,
                storyPoints,
                rs.getString("parent_id"),
                watchers,
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    public record CreateIssue(
            String projectId, IssueType type, String title, String description,
            Priority priority, String assigneeId, String reporterId,
            String sprintId, String parentId, Integer storyPoints, List<String> labels
    ) {}

    public record UpdateIssue(
            String issueId, int expectedVersion, String title, String description,
            Priority priority, String assigneeId, Integer storyPoints, List<String> labels
    ) {}
}
