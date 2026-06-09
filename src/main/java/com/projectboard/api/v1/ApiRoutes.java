package com.projectboard.api.v1;

import com.projectboard.application.*;
import com.projectboard.domain.model.*;
import com.projectboard.api.middleware.Middleware;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

public class ApiRoutes {

    private final IssueService issues;
    private final SprintService sprints;
    private final CommentService comments;
    private final SearchService search;

    public ApiRoutes(IssueService issues, SprintService sprints,
                     CommentService comments, SearchService search) {
        this.issues = issues;
        this.sprints = sprints;
        this.comments = comments;
        this.search = search;
    }

    public void register(Javalin app) {
        app.before("/api/v1/*", Middleware.auth());

        app.post("/api/v1/projects/{projectId}/issues", this::createIssue);
        app.get("/api/v1/projects/{projectId}/board", this::getBoard);
        app.get("/api/v1/projects/{projectId}/sprints", this::listSprints);
        app.get("/api/v1/projects/{projectId}/activity", this::activity);

        app.get("/api/v1/issues/{issueId}", this::getIssue);
        app.patch("/api/v1/issues/{issueId}", this::updateIssue);
        app.post("/api/v1/issues/{issueId}/transitions", this::transition);
        app.get("/api/v1/issues/{issueId}/comments", this::listComments);
        app.post("/api/v1/issues/{issueId}/comments", this::addComment);

        app.post("/api/v1/sprints/{sprintId}/start", this::startSprint);
        app.post("/api/v1/sprints/{sprintId}/complete", this::completeSprint);
        app.post("/api/v1/issues/{issueId}/sprint", this::moveToSprint);

        app.get("/api/v1/search", this::search);

        // v1 aliases without version prefix for backward compat
        app.before("/api/projects/*", Middleware.auth());
        app.before("/api/issues/*", Middleware.auth());
        app.before("/api/sprints/*", Middleware.auth());
        app.before("/api/search", Middleware.auth());

        app.post("/api/projects/{projectId}/issues", this::createIssue);
        app.get("/api/projects/{projectId}/board", this::getBoard);
        app.patch("/api/issues/{issueId}", this::updateIssue);
        app.post("/api/issues/{issueId}/transitions", this::transition);
        app.get("/api/projects/{projectId}/sprints", this::listSprints);
        app.post("/api/sprints/{sprintId}/start", this::startSprint);
        app.post("/api/sprints/{sprintId}/complete", this::completeSprint);
        app.get("/api/issues/{issueId}/comments", this::listComments);
        app.post("/api/issues/{issueId}/comments", this::addComment);
        app.get("/api/projects/{projectId}/activity", this::activity);
        app.get("/api/search", this::search);
    }

    private void createIssue(Context ctx) throws Exception {
        var req = ctx.bodyAsClass(IssueService.CreateIssueRequest.class);
        Issue issue = issues.create(ctx.pathParam("projectId"), userId(ctx), req);
        ctx.status(201).json(issue);
    }

    private void getBoard(Context ctx) throws Exception {
        ctx.json(issues.getBoard(ctx.pathParam("projectId"), userId(ctx)));
    }

    private void getIssue(Context ctx) throws Exception {
        ctx.json(issues.getIssue(ctx.pathParam("issueId"), userId(ctx)));
    }

    private void updateIssue(Context ctx) throws Exception {
        var req = ctx.bodyAsClass(IssueService.UpdateIssueRequest.class);
        ctx.json(issues.update(ctx.pathParam("issueId"), userId(ctx), req));
    }

    private void transition(Context ctx) throws Exception {
        var req = ctx.bodyAsClass(IssueService.TransitionRequest.class);
        ctx.json(issues.transition(ctx.pathParam("issueId"), userId(ctx), req));
    }

    private void listSprints(Context ctx) throws Exception {
        ctx.json(sprints.list(ctx.pathParam("projectId"), userId(ctx)));
    }

    private void startSprint(Context ctx) throws Exception {
        sprints.start(ctx.pathParam("sprintId"), userId(ctx));
        ctx.status(204);
    }

    private void completeSprint(Context ctx) throws Exception {
        var body = ctx.bodyAsClass(CompleteSprintBody.class);
        ctx.json(sprints.complete(ctx.pathParam("sprintId"), userId(ctx), body.carryOver()));
    }

    private void moveToSprint(Context ctx) throws Exception {
        var body = ctx.bodyAsClass(MoveSprintBody.class);
        sprints.moveIssue(ctx.pathParam("issueId"), body.sprintId(), userId(ctx));
        ctx.status(204);
    }

    private void listComments(Context ctx) throws Exception {
        ctx.json(comments.list(ctx.pathParam("issueId"), userId(ctx)));
    }

    private void addComment(Context ctx) throws Exception {
        var body = ctx.bodyAsClass(AddCommentBody.class);
        ctx.status(201).json(comments.add(ctx.pathParam("issueId"), userId(ctx), body.body(), body.parentId()));
    }

    private void activity(Context ctx) throws Exception {
        String cursor = ctx.queryParam("cursor");
        int limit = parseInt(ctx.queryParam("limit"), 20);
        ctx.json(search.activity(ctx.pathParam("projectId"), userId(ctx), cursor, limit,
                ctx.queryParam("event_type"), ctx.queryParam("issue_id")));
    }

    private void search(Context ctx) throws Exception {
        int limit = parseInt(ctx.queryParam("limit"), 20);
        ctx.json(search.search(ctx.queryParam("q"), ctx.queryParam("filter"),
                ctx.queryParam("cursor"), limit));
    }

    private String userId(Context ctx) {
        return ctx.attribute("userId");
    }

    private int parseInt(String val, int fallback) {
        if (val == null) return fallback;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return fallback; }
    }

    record CompleteSprintBody(List<String> carryOver) {}
    record MoveSprintBody(String sprintId) {}
    record AddCommentBody(String body, String parentId) {}
}
