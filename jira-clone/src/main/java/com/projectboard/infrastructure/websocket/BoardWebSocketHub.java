package com.projectboard.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BoardWebSocketHub {

    private static final Logger log = LoggerFactory.getLogger(BoardWebSocketHub.class);

    private final ObjectMapper mapper;
    private final Map<String, Set<WsContext>> projectSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionProject = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUser = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> missedEvents = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> presence = new ConcurrentHashMap<>();

    public BoardWebSocketHub(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void onConnect(WsContext ctx) {
        String projectId = ctx.queryParam("project_id");
        String userId = ctx.queryParam("user_id");
        if (projectId == null) {
            ctx.closeSession();
            return;
        }
        projectSessions.computeIfAbsent(projectId, k -> ConcurrentHashMap.newKeySet()).add(ctx);
        sessionProject.put(ctx.sessionId(), projectId);
        if (userId != null) sessionUser.put(ctx.sessionId(), userId);

        presence.computeIfAbsent(projectId, k -> ConcurrentHashMap.newKeySet()).add(
                userId != null ? userId : ctx.sessionId());

        replayMissed(ctx, projectId);
        broadcastPresence(projectId);
    }

    public void onMessage(WsMessageContext ctx) {
        // clients can ping for presence refresh
        String projectId = sessionProject.get(ctx.sessionId());
        if (projectId != null) broadcastPresence(projectId);
    }

    public void onClose(WsContext ctx) {
        String projectId = sessionProject.remove(ctx.sessionId());
        String userId = sessionUser.remove(ctx.sessionId());
        if (projectId != null) {
            Set<WsContext> sessions = projectSessions.get(projectId);
            if (sessions != null) sessions.remove(ctx);
            Set<String> users = presence.get(projectId);
            if (users != null) {
                users.remove(userId != null ? userId : ctx.sessionId());
            }
            broadcastPresence(projectId);
        }
    }

    public void broadcast(String projectId, Map<String, Object> event) {
        Set<WsContext> sessions = projectSessions.get(projectId);
        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (Exception e) {
            return;
        }

        if (sessions == null || sessions.isEmpty()) {
            missedEvents.computeIfAbsent(projectId, k -> new ArrayList<>()).add(event);
            if (missedEvents.get(projectId).size() > 100) {
                missedEvents.get(projectId).removeFirst();
            }
            return;
        }

        for (WsContext ctx : sessions) {
            try {
                ctx.send(json);
            } catch (Exception e) {
                log.debug("Failed to send WS message: {}", e.getMessage());
            }
        }
    }

    private void replayMissed(WsContext ctx, String projectId) {
        List<Map<String, Object>> events = missedEvents.remove(projectId);
        if (events == null) return;
        for (Map<String, Object> event : events) {
            try {
                ctx.send(mapper.writeValueAsString(event));
            } catch (Exception ignored) {}
        }
    }

    private void broadcastPresence(String projectId) {
        Set<String> users = presence.getOrDefault(projectId, Set.of());
        Map<String, Object> msg = Map.of(
                "type", "presence",
                "project_id", projectId,
                "viewers", users
        );
        broadcast(projectId, msg);
    }

    public int connectionCount() {
        return sessionProject.size();
    }

    public void closeAll() {
        for (Set<WsContext> sessions : projectSessions.values()) {
            for (WsContext ctx : sessions) {
                try { ctx.closeSession(); } catch (Exception ignored) {}
            }
        }
        projectSessions.clear();
        sessionProject.clear();
    }
}
