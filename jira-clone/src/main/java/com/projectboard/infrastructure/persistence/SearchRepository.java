package com.projectboard.infrastructure.persistence;

import com.projectboard.domain.model.Issue;
import com.projectboard.domain.model.IssueType;
import com.projectboard.domain.model.Priority;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class SearchRepository {

    private final DataSource ds;

    public SearchRepository(DataSource ds) {
        this.ds = ds;
    }

    public SearchResult search(String q, String structuredFilter, String cursor, int limit) throws SQLException {
        List<Issue> issues = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT i.*, ws.name AS status_name,
                   a.id AS assignee_id, a.display_name AS assignee_name,
                   r.id AS reporter_id, r.display_name AS reporter_name,
                   s.id AS sprint_id, s.name AS sprint_name, s.start_date, s.end_date
            FROM issues i
            JOIN workflow_statuses ws ON ws.id = i.status_id
            JOIN users r ON r.id = i.reporter_id
            LEFT JOIN users a ON a.id = i.assignee_id
            LEFT JOIN sprints s ON s.id = i.sprint_id
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            sql.append(" AND (to_tsvector('english', i.title || ' ' || coalesce(i.description, '')) @@ plainto_tsquery('english', ?)");
            sql.append(" OR EXISTS (SELECT 1 FROM comments c WHERE c.issue_id = i.id AND to_tsvector('english', c.body) @@ plainto_tsquery('english', ?)))");
            params.add(q);
            params.add(q);
        }

        applyStructuredFilter(sql, params, structuredFilter);

        if (cursor != null) {
            sql.append(" AND i.id > ?");
            params.add(cursor);
        }
        sql.append(" ORDER BY i.id LIMIT ?");
        params.add(limit + 1);

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && issues.size() < limit) {
                    issues.add(IssueWriteRepository.mapIssue(rs, List.of()));
                }
                String nextCursor = null;
                if (rs.next()) nextCursor = issues.get(issues.size() - 1).issueId();
                return new SearchResult(issues, nextCursor);
            }
        }
    }

    private void applyStructuredFilter(StringBuilder sql, List<Object> params, String filter) {
        if (filter == null || filter.isBlank()) return;
        // minimal parser: status = "X" AND assignee = "Y"
        for (String part : filter.split(" AND ")) {
            part = part.trim();
            if (part.startsWith("status =")) {
                String val = unquote(part.substring(part.indexOf('=') + 1).trim());
                sql.append(" AND ws.name = ?");
                params.add(val);
            } else if (part.startsWith("assignee =")) {
                String val = unquote(part.substring(part.indexOf('=') + 1).trim());
                sql.append(" AND a.display_name ILIKE ?");
                params.add(val);
            } else if (part.startsWith("type =")) {
                String val = unquote(part.substring(part.indexOf('=') + 1).trim());
                sql.append(" AND i.type = ?::issue_type");
                params.add(val.toUpperCase());
            }
        }
    }

    private String unquote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }

    public record SearchResult(List<Issue> issues, String nextCursor) {}
}
