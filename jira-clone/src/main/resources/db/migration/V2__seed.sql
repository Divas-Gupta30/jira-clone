-- Users
INSERT INTO users (id, email, display_name) VALUES
('user_admin', 'admin@example.com', 'Admin User'),
('user_lead', 'lead@example.com', 'Jane Smith'),
('user_member', 'member@example.com', 'Bob Chen'),
('user_viewer', 'viewer@example.com', 'Alex Lee');

-- Project
INSERT INTO projects (id, key, name) VALUES ('proj_abc', 'PROJ', 'Platform Team');

INSERT INTO project_members (project_id, user_id, role) VALUES
('proj_abc', 'user_admin', 'ADMIN'),
('proj_abc', 'user_lead', 'PROJECT_LEAD'),
('proj_abc', 'user_member', 'MEMBER'),
('proj_abc', 'user_viewer', 'VIEWER');

INSERT INTO issue_counters (project_id, next_number) VALUES ('proj_abc', 124);

-- Workflow
INSERT INTO workflow_statuses (id, project_id, name, position) VALUES
('st_todo', 'proj_abc', 'To Do', 0),
('st_progress', 'proj_abc', 'In Progress', 1),
('st_review', 'proj_abc', 'In Review', 2),
('st_done', 'proj_abc', 'Done', 3);

INSERT INTO workflow_transitions (id, project_id, from_status_id, to_status_id) VALUES
('tr_1', 'proj_abc', 'st_todo', 'st_progress'),
('tr_2', 'proj_abc', 'st_progress', 'st_review'),
('tr_3', 'proj_abc', 'st_review', 'st_done'),
('tr_4', 'proj_abc', 'st_progress', 'st_todo'),
('tr_5', 'proj_abc', 'st_review', 'st_progress');

INSERT INTO transition_actions (transition_id, action_type, action_value) VALUES
('tr_2', 'ASSIGN_REVIEWER', 'user_lead');

-- Sprints
INSERT INTO sprints (id, project_id, name, start_date, end_date, status) VALUES
('sprint_10', 'proj_abc', 'Sprint 10', '2024-01-15', '2024-01-29', 'ACTIVE'),
('sprint_11', 'proj_abc', 'Sprint 11', '2024-02-01', '2024-02-14', 'PLANNED');

-- Issues
INSERT INTO issues (id, project_id, issue_number, type, title, description, status_id, priority,
                    version, assignee_id, reporter_id, sprint_id, parent_id, story_points, labels) VALUES
('PROJ-100', 'proj_abc', 100, 'EPIC', 'User Authentication', 'OAuth and session management',
 'st_progress', 'HIGH', 2, 'user_lead', 'user_admin', 'sprint_10', NULL, 13, ARRAY['auth']),
('PROJ-123', 'proj_abc', 123, 'STORY', 'Add user authentication via OAuth',
 'Implement OAuth 2.0 login flow...', 'st_progress', 'HIGH', 3, 'user_member', 'user_lead',
 'sprint_10', 'PROJ-100', 5, ARRAY['auth', 'backend']),
('PROJ-124', 'proj_abc', 124, 'TASK', 'Configure OAuth providers', NULL,
 'st_todo', 'MEDIUM', 1, NULL, 'user_lead', NULL, 'PROJ-123', 2, ARRAY['auth']);

INSERT INTO issue_watchers (issue_id, user_id) VALUES
('PROJ-123', 'user_member'),
('PROJ-123', 'user_lead');

-- Custom field
INSERT INTO custom_field_defs (id, project_id, name, field_type, options) VALUES
('cf_env', 'proj_abc', 'Environment', 'DROPDOWN', ARRAY['dev', 'staging', 'prod']);

INSERT INTO custom_field_values (issue_id, field_id, value_text) VALUES
('PROJ-123', 'cf_env', 'staging');

-- Sample comment
INSERT INTO comments (id, issue_id, author_id, body) VALUES
('cmt_1', 'PROJ-123', 'user_lead', 'Please coordinate with @user_member on provider setup.');

INSERT INTO activity_log (id, project_id, issue_id, actor_id, event_type, payload) VALUES
('act_1', 'proj_abc', 'PROJ-123', 'user_lead', 'issue_created',
 '{"title": "Add user authentication via OAuth"}'::jsonb);
