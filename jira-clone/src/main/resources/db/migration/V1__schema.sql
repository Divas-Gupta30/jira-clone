CREATE TABLE users (
    id          VARCHAR(64) PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE projects (
    id          VARCHAR(64) PRIMARY KEY,
    key         VARCHAR(16) NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TYPE project_role AS ENUM ('ADMIN', 'PROJECT_LEAD', 'MEMBER', 'VIEWER');

CREATE TABLE project_members (
    project_id  VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id     VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        project_role NOT NULL DEFAULT 'MEMBER',
    PRIMARY KEY (project_id, user_id)
);

CREATE TABLE workflow_statuses (
    id          VARCHAR(64) PRIMARY KEY,
    project_id  VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name        VARCHAR(64) NOT NULL,
    position    INT NOT NULL,
    UNIQUE (project_id, name)
);

CREATE TABLE workflow_transitions (
    id              VARCHAR(64) PRIMARY KEY,
    project_id      VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    from_status_id  VARCHAR(64) NOT NULL REFERENCES workflow_statuses(id),
    to_status_id    VARCHAR(64) NOT NULL REFERENCES workflow_statuses(id),
    UNIQUE (project_id, from_status_id, to_status_id)
);

CREATE TABLE transition_actions (
    transition_id   VARCHAR(64) NOT NULL REFERENCES workflow_transitions(id) ON DELETE CASCADE,
    action_type     VARCHAR(64) NOT NULL,
    action_value    VARCHAR(255),
    PRIMARY KEY (transition_id, action_type)
);

CREATE TYPE issue_type AS ENUM ('EPIC', 'STORY', 'TASK', 'BUG', 'SUB_TASK');
CREATE TYPE issue_priority AS ENUM ('LOWEST', 'LOW', 'MEDIUM', 'HIGH', 'HIGHEST');

CREATE TABLE sprints (
    id          VARCHAR(64) PRIMARY KEY,
    project_id  VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    start_date  DATE,
    end_date    DATE,
    status      VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE issues (
    id              VARCHAR(64) PRIMARY KEY,
    project_id      VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    issue_number    INT NOT NULL,
    type            issue_type NOT NULL,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    status_id       VARCHAR(64) NOT NULL REFERENCES workflow_statuses(id),
    priority        issue_priority NOT NULL DEFAULT 'MEDIUM',
    version         INT NOT NULL DEFAULT 1,
    assignee_id     VARCHAR(64) REFERENCES users(id),
    reporter_id     VARCHAR(64) NOT NULL REFERENCES users(id),
    sprint_id       VARCHAR(64) REFERENCES sprints(id),
    parent_id       VARCHAR(64) REFERENCES issues(id),
    story_points    INT,
    labels          TEXT[] DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, issue_number)
);

CREATE INDEX idx_issues_project ON issues(project_id);
CREATE INDEX idx_issues_sprint ON issues(sprint_id);
CREATE INDEX idx_issues_assignee ON issues(assignee_id);
CREATE INDEX idx_issues_status ON issues(status_id);
CREATE INDEX idx_issues_parent ON issues(parent_id);
CREATE INDEX idx_issues_search ON issues USING gin(to_tsvector('english', title || ' ' || coalesce(description, '')));

CREATE TYPE custom_field_type AS ENUM ('TEXT', 'NUMBER', 'DROPDOWN', 'DATE');

CREATE TABLE custom_field_defs (
    id          VARCHAR(64) PRIMARY KEY,
    project_id  VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name        VARCHAR(128) NOT NULL,
    field_type  custom_field_type NOT NULL,
    options     TEXT[],
    UNIQUE (project_id, name)
);

CREATE TABLE custom_field_values (
    issue_id    VARCHAR(64) NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    field_id    VARCHAR(64) NOT NULL REFERENCES custom_field_defs(id) ON DELETE CASCADE,
    value_text  TEXT,
    PRIMARY KEY (issue_id, field_id)
);

CREATE TABLE comments (
    id          VARCHAR(64) PRIMARY KEY,
    issue_id    VARCHAR(64) NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    author_id   VARCHAR(64) NOT NULL REFERENCES users(id),
    body        TEXT NOT NULL,
    parent_id   VARCHAR(64) REFERENCES comments(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comments_issue ON comments(issue_id);
CREATE INDEX idx_comments_search ON comments USING gin(to_tsvector('english', body));

CREATE TABLE issue_watchers (
    issue_id    VARCHAR(64) NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    user_id     VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (issue_id, user_id)
);

CREATE TABLE activity_log (
    id          VARCHAR(64) PRIMARY KEY,
    project_id  VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    issue_id    VARCHAR(64) REFERENCES issues(id) ON DELETE SET NULL,
    actor_id    VARCHAR(64) REFERENCES users(id),
    event_type  VARCHAR(64) NOT NULL,
    payload     JSONB NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activity_project_time ON activity_log(project_id, created_at DESC);

CREATE TABLE notifications (
    id          VARCHAR(64) PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(64) NOT NULL,
    payload     JSONB NOT NULL DEFAULT '{}',
    read        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);

CREATE TABLE idempotency_keys (
    key         VARCHAR(128) PRIMARY KEY,
    response    JSONB NOT NULL,
    status_code INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_log (
    id          VARCHAR(64) PRIMARY KEY,
    actor_id    VARCHAR(64) REFERENCES users(id),
    action      VARCHAR(128) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id   VARCHAR(64) NOT NULL,
    details     JSONB NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE issue_counters (
    project_id  VARCHAR(64) PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
    next_number INT NOT NULL DEFAULT 1
);
