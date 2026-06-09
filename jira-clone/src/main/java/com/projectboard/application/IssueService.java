package com.projectboard.application;

import com.projectboard.api.ConflictException;
import com.projectboard.api.NotFoundException;
import com.projectboard.domain.events.*;
import com.projectboard.domain.model.*;
import com.projectboard.domain.ports.AccessControl;
import com.projectboard.domain.ports.EventPublisher;
import com.projectboard.infrastructure.persistence.*;
import com.projectboard.infrastructure.redis.RedisCache;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IssueService {

    private final DataSource ds;
    private final IssueWriteRepository writeRepo;
    private final BoardReadRepository boardRepo;
    private final WorkflowRepository workflowRepo;
    private final AccessControl access;
    private final EventPublisher events;
    private final RedisCache cache;

    public IssueService(DataSource ds, IssueWriteRepository writeRepo, BoardReadRepository boardRepo,
                        WorkflowRepository workflowRepo, AccessControl access,
                        EventPublisher events, RedisCache cache) {
        this.ds = ds;
        this.writeRepo = writeRepo;
        this.boardRepo = boardRepo;
        this.workflowRepo = workflowRepo;
        this.access = access;
        this.events = events;
        this.cache = cache;
    }

    public Issue create(String projectId, String userId, CreateIssueRequest req) throws Exception {
        access.requireWrite(projectId, userId);
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            var cmd = new IssueWriteRepository.CreateIssue(
                    projectId, req.type(), req.title(), req.description(), req.priority(),
                    req.assigneeId(), userId, req.sprintId(), req.parentId(),
                    req.storyPoints(), req.labels() != null ? req.labels() : List.of());
            Issue issue = writeRepo.insert(conn, cmd);
            conn.commit();

            events.publish(new IssueCreated(projectId, issue.issueId(), Map.of(
                    "issue_id", issue.issueId(),
                    "title", issue.title(),
                    "actor_id", userId
            )));
            return issue;
        }
    }

    public Issue update(String issueId, String userId, UpdateIssueRequest req) throws Exception {
        String projectId = projectIdFor(issueId);
        access.requireWrite(projectId, userId);

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            var cmd = new IssueWriteRepository.UpdateIssue(
                    issueId, req.version(), req.title(), req.description(),
                    req.priority(), req.assigneeId(), req.storyPoints(), req.labels());
            Issue updated = writeRepo.update(conn, cmd);
            if (updated == null) {
                Issue current = writeRepo.findById(conn, issueId);
                conn.rollback();
                throw new ConflictException("Issue was modified by another user", current);
            }
            conn.commit();

            Map<String, Object> payload = new HashMap<>();
            payload.put("issue_id", issueId);
            payload.put("actor_id", userId);
            if (req.assigneeId() != null) payload.put("assignee_id", req.assigneeId());
            events.publish(new IssueUpdated(projectId, issueId, payload));
            return updated;
        }
    }

    public Issue transition(String issueId, String userId, TransitionRequest req) throws Exception {
        String projectId = projectIdFor(issueId);
        access.requireWrite(projectId, userId);

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            Issue current = writeRepo.findById(conn, issueId);
            String fromStatusId = workflowRepo.statusIdByName(conn, projectId, current.status());
            String toStatusId = workflowRepo.statusIdByName(conn, projectId, req.toStatus());

            workflowRepo.validateTransition(conn, projectId, fromStatusId, toStatusId);

            Map<String, String> actions = workflowRepo.transitionActions(conn, projectId, fromStatusId, toStatusId);
            Issue updated = writeRepo.transition(conn, issueId, toStatusId, req.version());
            if (updated == null) {
                current = writeRepo.findById(conn, issueId);
                conn.rollback();
                throw new ConflictException("Issue was modified by another user", current);
            }

            if (actions.containsKey("ASSIGN_REVIEWER")) {
                try (var ps = conn.prepareStatement("UPDATE issues SET assignee_id = ? WHERE id = ?")) {
                    ps.setString(1, actions.get("ASSIGN_REVIEWER"));
                    ps.setString(2, issueId);
                    ps.executeUpdate();
                }
                updated = writeRepo.findById(conn, issueId);
            }

            conn.commit();

            Map<String, Object> payload = new HashMap<>();
            payload.put("issue_id", issueId);
            payload.put("from", current.status());
            payload.put("to", req.toStatus());
            payload.put("actor_id", userId);
            if (updated.assignee() != null) payload.put("assignee_id", updated.assignee().userId());
            events.publish(new StatusChanged(projectId, issueId, payload));
            return updated;
        }
    }

    public BoardView getBoard(String projectId, String userId) throws Exception {
        access.requireAccess(projectId, userId);
        return cache.getBoard(projectId).orElseGet(() -> {
            try {
                BoardView board = boardRepo.getBoard(projectId);
                cache.putBoard(projectId, board);
                return board;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Issue getIssue(String issueId, String userId) throws Exception {
        String projectId = projectIdFor(issueId);
        access.requireAccess(projectId, userId);
        try (Connection conn = ds.getConnection()) {
            return writeRepo.findById(conn, issueId);
        }
    }

    private String projectIdFor(String issueId) throws Exception {
        try (Connection conn = ds.getConnection()) {
            return writeRepo.projectIdForIssue(conn, issueId);
        }
    }

    public record CreateIssueRequest(
            IssueType type, String title, String description, Priority priority,
            String assigneeId, String sprintId, String parentId, Integer storyPoints, List<String> labels
    ) {}

    public record UpdateIssueRequest(
            int version, String title, String description, Priority priority,
            String assigneeId, Integer storyPoints, List<String> labels
    ) {}

    public record TransitionRequest(String toStatus, int version) {}
}
