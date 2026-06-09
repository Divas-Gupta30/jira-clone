package com.projectboard.application;

import com.projectboard.domain.events.CommentAdded;
import com.projectboard.domain.model.Comment;
import com.projectboard.domain.ports.AccessControl;
import com.projectboard.domain.ports.EventPublisher;
import com.projectboard.infrastructure.persistence.CommentRepository;
import com.projectboard.infrastructure.persistence.IssueWriteRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

public class CommentService {

    private final DataSource ds;
    private final CommentRepository commentRepo;
    private final IssueWriteRepository issueRepo;
    private final AccessControl access;
    private final EventPublisher events;

    public CommentService(DataSource ds, CommentRepository commentRepo, IssueWriteRepository issueRepo,
                          AccessControl access, EventPublisher events) {
        this.ds = ds;
        this.commentRepo = commentRepo;
        this.issueRepo = issueRepo;
        this.access = access;
        this.events = events;
    }

    public List<Comment> list(String issueId, String userId) throws Exception {
        String projectId = projectIdFor(issueId);
        access.requireAccess(projectId, userId);
        return commentRepo.listByIssue(issueId);
    }

    public Comment add(String issueId, String userId, String body, String parentId) throws Exception {
        String projectId = projectIdFor(issueId);
        access.requireWrite(projectId, userId);

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            Comment comment = commentRepo.add(conn, issueId, userId, body, parentId);
            conn.commit();

            List<String> mentions = commentRepo.extractMentions(body);
            events.publish(new CommentAdded(projectId, issueId, Map.of(
                    "issue_id", issueId,
                    "comment_id", comment.id(),
                    "mentions", mentions,
                    "actor_id", userId
            )));
            return comment;
        }
    }

    private String projectIdFor(String issueId) throws Exception {
        try (Connection conn = ds.getConnection()) {
            return issueRepo.projectIdForIssue(conn, issueId);
        }
    }
}
