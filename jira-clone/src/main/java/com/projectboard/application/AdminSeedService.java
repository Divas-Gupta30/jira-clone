package com.projectboard.application;

import com.projectboard.domain.model.ProjectRole;
import com.projectboard.infrastructure.persistence.SeedAdminRepository;
import com.projectboard.infrastructure.redis.RedisCache;

import java.util.Map;

public class AdminSeedService {

    private final SeedAdminRepository repo;
    private final RedisCache cache;

    public AdminSeedService(SeedAdminRepository repo, RedisCache cache) {
        this.repo = repo;
        this.cache = cache;
    }

    public record AddUserRequest(String id, String email, String displayName) {}
    public record AddMemberRequest(String userId, String role) {}
    public record CreateProjectRequest(String id, String key, String name, String adminUserId) {}

    public Object snapshot() throws Exception {
        return repo.snapshot();
    }

    public void addUser(AddUserRequest req) throws Exception {
        repo.addUser(req.id(), req.email(), req.displayName());
    }

    public void removeUser(String userId) throws Exception {
        repo.removeUser(userId);
    }

    public void addMember(String projectId, AddMemberRequest req) throws Exception {
        repo.addMember(projectId, req.userId(), ProjectRole.valueOf(req.role()));
    }

    public void removeMember(String projectId, String userId) throws Exception {
        repo.removeMember(projectId, userId);
    }

    public Map<String, Object> createProject(CreateProjectRequest req) throws Exception {
        return repo.createProject(req.id(), req.key(), req.name(), req.adminUserId());
    }

    public void removeProject(String projectId) throws Exception {
        repo.removeProject(projectId);
        cache.invalidateBoard(projectId);
    }

    public Object reset() throws Exception {
        repo.resetDemoSeed();
        cache.invalidateBoard("proj_abc");
        return repo.snapshot();
    }
}
