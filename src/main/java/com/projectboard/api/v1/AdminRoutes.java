package com.projectboard.api.v1;

import com.projectboard.application.AdminSeedService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

public class AdminRoutes {

    private final AdminSeedService admin;

    public AdminRoutes(AdminSeedService admin) {
        this.admin = admin;
    }

    public void register(Javalin app) {
        app.get("/api/v1/admin/seed", this::snapshot);
        app.post("/api/v1/admin/seed/users", this::addUser);
        app.delete("/api/v1/admin/seed/users/{userId}", this::removeUser);
        app.post("/api/v1/admin/seed/projects", this::createProject);
        app.delete("/api/v1/admin/seed/projects/{projectId}", this::removeProject);
        app.post("/api/v1/admin/seed/projects/{projectId}/members", this::addMember);
        app.delete("/api/v1/admin/seed/projects/{projectId}/members/{userId}", this::removeMember);
        app.post("/api/v1/admin/seed/reset", this::reset);
    }

    private void snapshot(Context ctx) throws Exception {
        ctx.json(admin.snapshot());
    }

    private void addUser(Context ctx) throws Exception {
        var req = ctx.bodyAsClass(AdminSeedService.AddUserRequest.class);
        admin.addUser(req);
        ctx.status(201).json(req);
    }

    private void removeUser(Context ctx) throws Exception {
        admin.removeUser(ctx.pathParam("userId"));
        ctx.status(204);
    }

    private void createProject(Context ctx) throws Exception {
        var req = ctx.bodyAsClass(AdminSeedService.CreateProjectRequest.class);
        Map<String, Object> created = admin.createProject(req);
        ctx.status(201).json(created);
    }

    private void removeProject(Context ctx) throws Exception {
        admin.removeProject(ctx.pathParam("projectId"));
        ctx.status(204);
    }

    private void addMember(Context ctx) throws Exception {
        var req = ctx.bodyAsClass(AdminSeedService.AddMemberRequest.class);
        admin.addMember(ctx.pathParam("projectId"), req);
        ctx.status(201).json(req);
    }

    private void removeMember(Context ctx) throws Exception {
        admin.removeMember(ctx.pathParam("projectId"), ctx.pathParam("userId"));
        ctx.status(204);
    }

    private void reset(Context ctx) throws Exception {
        ctx.json(admin.reset());
    }
}
