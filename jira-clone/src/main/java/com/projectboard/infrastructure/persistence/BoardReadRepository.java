package com.projectboard.infrastructure.persistence;

import com.projectboard.domain.model.BoardColumn;
import com.projectboard.domain.model.BoardView;
import com.projectboard.domain.model.Issue;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class BoardReadRepository {

    private final DataSource ds;

    public BoardReadRepository(DataSource ds) {
        this.ds = ds;
    }

    public BoardView getBoard(String projectId) throws SQLException {
        Map<String, List<Issue>> byStatus = new LinkedHashMap<>();

        String statusSql = """
            SELECT name FROM workflow_statuses WHERE project_id = ? ORDER BY position
            """;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(statusSql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) byStatus.put(rs.getString(1), new ArrayList<>());
            }

            String issueSql = """
                SELECT i.*, ws.name AS status_name,
                       a.id AS assignee_id, a.display_name AS assignee_name,
                       r.id AS reporter_id, r.display_name AS reporter_name,
                       s.id AS sprint_id, s.name AS sprint_name, s.start_date, s.end_date
                FROM issues i
                JOIN workflow_statuses ws ON ws.id = i.status_id
                JOIN users r ON r.id = i.reporter_id
                LEFT JOIN users a ON a.id = i.assignee_id
                LEFT JOIN sprints s ON s.id = i.sprint_id
                WHERE i.project_id = ?
                ORDER BY ws.position, i.updated_at DESC
                """;
            try (PreparedStatement ips = conn.prepareStatement(issueSql)) {
                ips.setString(1, projectId);
                try (ResultSet rs = ips.executeQuery()) {
                    while (rs.next()) {
                        String status = rs.getString("status_name");
                        Issue issue = IssueWriteRepository.mapIssue(rs, List.of());
                        byStatus.computeIfAbsent(status, k -> new ArrayList<>()).add(issue);
                    }
                }
            }
        }

        List<BoardColumn> columns = byStatus.entrySet().stream()
                .map(e -> new BoardColumn(e.getKey(), e.getValue()))
                .toList();
        return new BoardView(projectId, columns);
    }
}
