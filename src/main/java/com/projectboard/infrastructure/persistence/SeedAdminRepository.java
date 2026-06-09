package com.projectboard.infrastructure.persistence;

import com.projectboard.api.ConflictException;
import com.projectboard.api.NotFoundException;
import com.projectboard.domain.model.ProjectRole;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SeedAdminRepository {

    private final DataSource ds;

    public SeedAdminRepository(DataSource ds) {
        this.ds = ds;
    }

    public Map<String, Object> snapshot() throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", listUsers());
        out.put("projects", listProjects());
        out.put("members", listMembers());
        return out;
    }

    public void addUser(String id, String email, String displayName) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (id, email, display_name) VALUES (?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, email);
            ps.setString(3, displayName);
            ps.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new ConflictException("User already exists: " + id);
            }
            throw e;
        }
    }

    public void removeUser(String userId) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setString(1, userId);
            if (ps.executeUpdate() == 0) throw new NotFoundException("User not found: " + userId);
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState())) {
                throw new ConflictException("User is referenced by issues, comments, or memberships");
            }
            throw e;
        }
    }

    public void addMember(String projectId, String userId, ProjectRole role) throws SQLException {
        ensureProjectExists(projectId);
        ensureUserExists(userId);
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO project_members (project_id, user_id, role) VALUES (?, ?, ?::project_role)")) {
            ps.setString(1, projectId);
            ps.setString(2, userId);
            ps.setString(3, role.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new ConflictException("User is already a project member");
            }
            throw e;
        }
    }

    public void removeMember(String projectId, String userId) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM project_members WHERE project_id = ? AND user_id = ?")) {
            ps.setString(1, projectId);
            ps.setString(2, userId);
            if (ps.executeUpdate() == 0) {
                throw new NotFoundException("Project member not found");
            }
        }
    }

    public Map<String, Object> createProject(String id, String key, String name, String adminUserId)
            throws SQLException {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                insertProjectRow(conn, id, key, name);
                bootstrapWorkflow(conn, id, adminUserId);
                insertCounter(conn, id, 1);
                if (adminUserId != null && !adminUserId.isBlank()) {
                    insertMemberRow(conn, id, adminUserId, ProjectRole.ADMIN);
                }
                conn.commit();
                return Map.of("id", id, "key", key, "name", name);
            } catch (SQLException e) {
                conn.rollback();
                if ("23505".equals(e.getSQLState())) {
                    throw new ConflictException("Project id or key already exists: " + id);
                }
                throw e;
            }
        }
    }

    public void removeProject(String projectId) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM projects WHERE id = ?")) {
            ps.setString(1, projectId);
            if (ps.executeUpdate() == 0) {
                throw new NotFoundException("Project not found: " + projectId);
            }
        }
    }

    public void resetDemoSeed() throws SQLException {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                deleteDemoProject(conn);
                deleteDemoUsers(conn);
                insertDefaultSeed(conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void deleteDemoProject(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM projects WHERE id = ?")) {
            ps.setString(1, "proj_abc");
            ps.executeUpdate();
        }
    }

    private void deleteDemoUsers(Connection conn) throws SQLException {
        for (String userId : List.of("user_admin", "user_lead", "user_member", "user_viewer")) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                ps.setString(1, userId);
                ps.executeUpdate();
            }
        }
    }

    private void insertDefaultSeed(Connection conn) throws SQLException {
        exec(conn, """
                INSERT INTO users (id, email, display_name) VALUES
                ('user_admin', 'admin@example.com', 'Admin User'),
                ('user_lead', 'lead@example.com', 'Jane Smith'),
                ('user_member', 'member@example.com', 'Bob Chen'),
                ('user_viewer', 'viewer@example.com', 'Alex Lee')
                """);
        exec(conn, "INSERT INTO projects (id, key, name) VALUES ('proj_abc', 'PROJ', 'Platform Team')");
        exec(conn, """
                INSERT INTO project_members (project_id, user_id, role) VALUES
                ('proj_abc', 'user_admin', 'ADMIN'),
                ('proj_abc', 'user_lead', 'PROJECT_LEAD'),
                ('proj_abc', 'user_member', 'MEMBER'),
                ('proj_abc', 'user_viewer', 'VIEWER')
                """);
        exec(conn, "INSERT INTO issue_counters (project_id, next_number) VALUES ('proj_abc', 124)");
        exec(conn, """
                INSERT INTO workflow_statuses (id, project_id, name, position) VALUES
                ('st_todo', 'proj_abc', 'To Do', 0),
                ('st_progress', 'proj_abc', 'In Progress', 1),
                ('st_review', 'proj_abc', 'In Review', 2),
                ('st_done', 'proj_abc', 'Done', 3)
                """);
        exec(conn, """
                INSERT INTO workflow_transitions (id, project_id, from_status_id, to_status_id) VALUES
                ('tr_1', 'proj_abc', 'st_todo', 'st_progress'),
                ('tr_2', 'proj_abc', 'st_progress', 'st_review'),
                ('tr_3', 'proj_abc', 'st_review', 'st_done'),
                ('tr_4', 'proj_abc', 'st_progress', 'st_todo'),
                ('tr_5', 'proj_abc', 'st_review', 'st_progress')
                """);
        exec(conn, """
                INSERT INTO transition_actions (transition_id, action_type, action_value) VALUES
                ('tr_2', 'ASSIGN_REVIEWER', 'user_lead')
                """);
        exec(conn, """
                INSERT INTO sprints (id, project_id, name, start_date, end_date, status) VALUES
                ('sprint_10', 'proj_abc', 'Sprint 10', '2024-01-15', '2024-01-29', 'ACTIVE'),
                ('sprint_11', 'proj_abc', 'Sprint 11', '2024-02-01', '2024-02-14', 'PLANNED')
                """);
        exec(conn, """
                INSERT INTO issues (id, project_id, issue_number, type, title, description, status_id, priority,
                                    version, assignee_id, reporter_id, sprint_id, parent_id, story_points, labels) VALUES
                ('PROJ-100', 'proj_abc', 100, 'EPIC', 'User Authentication', 'OAuth and session management',
                 'st_progress', 'HIGH', 2, 'user_lead', 'user_admin', 'sprint_10', NULL, 13, ARRAY['auth']),
                ('PROJ-123', 'proj_abc', 123, 'STORY', 'Add user authentication via OAuth',
                 'Implement OAuth 2.0 login flow...', 'st_progress', 'HIGH', 3, 'user_member', 'user_lead',
                 'sprint_10', 'PROJ-100', 5, ARRAY['auth', 'backend']),
                ('PROJ-124', 'proj_abc', 124, 'TASK', 'Configure OAuth providers', NULL,
                 'st_todo', 'MEDIUM', 1, NULL, 'user_lead', NULL, 'PROJ-123', 2, ARRAY['auth'])
                """);
        exec(conn, """
                INSERT INTO issue_watchers (issue_id, user_id) VALUES
                ('PROJ-123', 'user_member'),
                ('PROJ-123', 'user_lead')
                """);
        exec(conn, """
                INSERT INTO custom_field_defs (id, project_id, name, field_type, options) VALUES
                ('cf_env', 'proj_abc', 'Environment', 'DROPDOWN', ARRAY['dev', 'staging', 'prod'])
                """);
        exec(conn, "INSERT INTO custom_field_values (issue_id, field_id, value_text) VALUES ('PROJ-123', 'cf_env', 'staging')");
        exec(conn, """
                INSERT INTO comments (id, issue_id, author_id, body) VALUES
                ('cmt_1', 'PROJ-123', 'user_lead', 'Please coordinate with @user_member on provider setup.')
                """);
        exec(conn, """
                INSERT INTO activity_log (id, project_id, issue_id, actor_id, event_type, payload) VALUES
                ('act_1', 'proj_abc', 'PROJ-123', 'user_lead', 'issue_created',
                 '{"title": "Add user authentication via OAuth"}'::jsonb)
                """);
    }

    private void exec(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private void insertProjectRow(Connection conn, String id, String key, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO projects (id, key, name) VALUES (?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, key);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }

    private void insertCounter(Connection conn, String projectId, int nextNumber) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO issue_counters (project_id, next_number) VALUES (?, ?)")) {
            ps.setString(1, projectId);
            ps.setInt(2, nextNumber);
            ps.executeUpdate();
        }
    }

    private void insertMemberRow(Connection conn, String projectId, String userId, ProjectRole role)
            throws SQLException {
        ensureUserExists(userId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO project_members (project_id, user_id, role) VALUES (?, ?, ?::project_role)")) {
            ps.setString(1, projectId);
            ps.setString(2, userId);
            ps.setString(3, role.name());
            ps.executeUpdate();
        }
    }

    private void bootstrapWorkflow(Connection conn, String projectId, String reviewerUserId) throws SQLException {
        String todo = projectId + "_st_todo";
        String progress = projectId + "_st_progress";
        String review = projectId + "_st_review";
        String done = projectId + "_st_done";

        insertStatus(conn, todo, projectId, "To Do", 0);
        insertStatus(conn, progress, projectId, "In Progress", 1);
        insertStatus(conn, review, projectId, "In Review", 2);
        insertStatus(conn, done, projectId, "Done", 3);

        String tr1 = projectId + "_tr_1";
        String tr2 = projectId + "_tr_2";
        String tr3 = projectId + "_tr_3";
        String tr4 = projectId + "_tr_4";
        String tr5 = projectId + "_tr_5";

        insertTransition(conn, tr1, projectId, todo, progress);
        insertTransition(conn, tr2, projectId, progress, review);
        insertTransition(conn, tr3, projectId, review, done);
        insertTransition(conn, tr4, projectId, progress, todo);
        insertTransition(conn, tr5, projectId, review, progress);

        if (reviewerUserId != null && !reviewerUserId.isBlank()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO transition_actions (transition_id, action_type, action_value) VALUES (?, ?, ?)")) {
                ps.setString(1, tr2);
                ps.setString(2, "ASSIGN_REVIEWER");
                ps.setString(3, reviewerUserId);
                ps.executeUpdate();
            }
        }
    }

    private void insertStatus(Connection conn, String id, String projectId, String name, int position)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO workflow_statuses (id, project_id, name, position) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, projectId);
            ps.setString(3, name);
            ps.setInt(4, position);
            ps.executeUpdate();
        }
    }

    private void insertTransition(Connection conn, String id, String projectId, String fromId, String toId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO workflow_transitions (id, project_id, from_status_id, to_status_id) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, projectId);
            ps.setString(3, fromId);
            ps.setString(4, toId);
            ps.executeUpdate();
        }
    }

    private void ensureProjectExists(String projectId) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM projects WHERE id = ?")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NotFoundException("Project not found: " + projectId);
            }
        }
    }

    private void ensureUserExists(String userId) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM users WHERE id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NotFoundException("User not found: " + userId);
            }
        }
    }

    private List<Map<String, Object>> listUsers() throws SQLException {
        List<Map<String, Object>> users = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, email, display_name FROM users ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(Map.of(
                        "id", rs.getString("id"),
                        "email", rs.getString("email"),
                        "displayName", rs.getString("display_name")
                ));
            }
        }
        return users;
    }

    private List<Map<String, Object>> listProjects() throws SQLException {
        List<Map<String, Object>> projects = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, key, name FROM projects ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                projects.add(Map.of(
                        "id", rs.getString("id"),
                        "key", rs.getString("key"),
                        "name", rs.getString("name")
                ));
            }
        }
        return projects;
    }

    private List<Map<String, Object>> listMembers() throws SQLException {
        List<Map<String, Object>> members = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT project_id, user_id, role::text AS role FROM project_members ORDER BY project_id, user_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                members.add(Map.of(
                        "projectId", rs.getString("project_id"),
                        "userId", rs.getString("user_id"),
                        "role", rs.getString("role")
                ));
            }
        }
        return members;
    }
}
