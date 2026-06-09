package com.projectboard.infrastructure.persistence;

import com.projectboard.domain.model.ActivityEvent;
import com.projectboard.domain.model.Comment;
import com.projectboard.domain.model.UserRef;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class CommentRepository {

    private final DataSource ds;

    public CommentRepository(DataSource ds) {
        this.ds = ds;
    }

    public Comment add(Connection conn, String issueId, String authorId, String body, String parentId)
            throws SQLException {
        String id = "cmt_" + UUID.randomUUID().toString().substring(0, 8);
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO comments (id, issue_id, author_id, body, parent_id) VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, id);
            ps.setString(2, issueId);
            ps.setString(3, authorId);
            ps.setString(4, body);
            ps.setString(5, parentId);
            ps.executeUpdate();
        }
        return findById(conn, id);
    }

    public List<Comment> listByIssue(String issueId) throws SQLException {
        List<Comment> comments = new ArrayList<>();
        String sql = """
            SELECT c.*, u.display_name FROM comments c
            JOIN users u ON u.id = c.author_id
            WHERE c.issue_id = ? ORDER BY c.created_at
            """;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, issueId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) comments.add(map(rs));
            }
        }
        return comments;
    }

    private Comment findById(Connection conn, String id) throws SQLException {
        String sql = """
            SELECT c.*, u.display_name FROM comments c
            JOIN users u ON u.id = c.author_id WHERE c.id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return map(rs);
            }
        }
    }

    private Comment map(ResultSet rs) throws SQLException {
        return new Comment(
                rs.getString("id"),
                rs.getString("issue_id"),
                new UserRef(rs.getString("author_id"), rs.getString("display_name")),
                rs.getString("body"),
                rs.getString("parent_id"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    public List<String> extractMentions(String body) {
        List<String> mentions = new ArrayList<>();
        int idx = 0;
        while ((idx = body.indexOf('@', idx)) >= 0) {
            int end = idx + 1;
            while (end < body.length() && (Character.isLetterOrDigit(body.charAt(end)) || body.charAt(end) == '_')) {
                end++;
            }
            if (end > idx + 1) mentions.add(body.substring(idx + 1, end));
            idx = end;
        }
        return mentions;
    }
}
