package com.projectboard.infrastructure.persistence;

import com.projectboard.domain.model.ActivityEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class ActivityRepository {

    private final DataSource ds;
    private final ObjectMapper mapper;

    public ActivityRepository(DataSource ds, ObjectMapper mapper) {
        this.ds = ds;
        this.mapper = mapper;
    }

    public void log(Connection conn, String projectId, String issueId, String actorId,
                    String eventType, Map<String, Object> payload) throws SQLException {
        String id = "act_" + UUID.randomUUID().toString().substring(0, 8);
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO activity_log (id, project_id, issue_id, actor_id, event_type, payload)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """)) {
            ps.setString(1, id);
            ps.setString(2, projectId);
            ps.setString(3, issueId);
            ps.setString(4, actorId);
            ps.setString(5, eventType);
            ps.setString(6, mapper.valueToTree(payload).toString());
            ps.executeUpdate();
        }
    }

    public List<ActivityEvent> list(String projectId, String cursor, int limit,
                                     String eventType, String issueId) throws SQLException {
        List<ActivityEvent> events = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM activity_log WHERE project_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(projectId);

        if (eventType != null) {
            sql.append(" AND event_type = ?");
            params.add(eventType);
        }
        if (issueId != null) {
            sql.append(" AND issue_id = ?");
            params.add(issueId);
        }
        if (cursor != null) {
            sql.append(" AND created_at < (SELECT created_at FROM activity_log WHERE id = ?)");
            params.add(cursor);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(limit);

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) events.add(map(rs));
            }
        }
        return events;
    }

    private ActivityEvent map(ResultSet rs) throws SQLException {
        Map<String, Object> payload;
        try {
            payload = mapper.readValue(rs.getString("payload"), new TypeReference<>() {});
        } catch (Exception e) {
            payload = Map.of();
        }
        return new ActivityEvent(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("issue_id"),
                rs.getString("actor_id"),
                rs.getString("event_type"),
                payload,
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
