package com.projectboard.application;

import com.projectboard.domain.events.SprintUpdated;
import com.projectboard.domain.model.Sprint;
import com.projectboard.domain.ports.AccessControl;
import com.projectboard.domain.ports.EventPublisher;
import com.projectboard.infrastructure.persistence.IssueWriteRepository;
import com.projectboard.infrastructure.persistence.SprintRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

public class SprintService {

    private final DataSource ds;
    private final SprintRepository sprintRepo;
    private final IssueWriteRepository issueRepo;
    private final AccessControl access;
    private final EventPublisher events;

    public SprintService(DataSource ds, SprintRepository sprintRepo, IssueWriteRepository issueRepo,
                         AccessControl access, EventPublisher events) {
        this.ds = ds;
        this.sprintRepo = sprintRepo;
        this.issueRepo = issueRepo;
        this.access = access;
        this.events = events;
    }

    public List<Sprint> list(String projectId, String userId) throws Exception {
        access.requireAccess(projectId, userId);
        return sprintRepo.listByProject(projectId);
    }

    public void start(String sprintId, String userId) throws Exception {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            Sprint sprint = sprintRepo.findById(conn, sprintId);
            access.requireManage(sprint.projectId(), userId);
            sprintRepo.startSprint(conn, sprintId);
            conn.commit();

            events.publish(new SprintUpdated(sprint.projectId(), sprintId, Map.of(
                    "sprint_id", sprintId, "action", "started", "actor_id", userId
            )));
        }
    }

    public SprintRepository.SprintCompleteResult complete(String sprintId, String userId,
                                                           List<String> carryOver) throws Exception {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            Sprint sprint = sprintRepo.findById(conn, sprintId);
            access.requireManage(sprint.projectId(), userId);
            var result = sprintRepo.completeSprint(conn, sprintId, carryOver != null ? carryOver : List.of());
            conn.commit();

            events.publish(new SprintUpdated(sprint.projectId(), sprintId, Map.of(
                    "sprint_id", sprintId,
                    "action", "completed",
                    "completed_points", result.completedPoints(),
                    "incomplete_points", result.incompletePoints(),
                    "actor_id", userId
            )));
            return result;
        }
    }

    public void moveIssue(String issueId, String sprintId, String userId) throws Exception {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            String projectId = issueRepo.projectIdForIssue(conn, issueId);
            access.requireWrite(projectId, userId);
            issueRepo.moveToSprint(conn, issueId, sprintId);
            conn.commit();
        }
    }
}
