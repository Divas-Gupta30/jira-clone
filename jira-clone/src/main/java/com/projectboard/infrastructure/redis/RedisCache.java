package com.projectboard.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectboard.domain.model.BoardView;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.Optional;

public class RedisCache implements AutoCloseable {

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> conn;
    private final RedisCommands<String, String> cmd;
    private final ObjectMapper mapper;

    public RedisCache(String redisUrl, ObjectMapper mapper) {
        this.client = RedisClient.create(redisUrl);
        this.conn = client.connect();
        this.cmd = conn.sync();
        this.mapper = mapper;
    }

    public Optional<BoardView> getBoard(String projectId) {
        try {
            String json = cmd.get(boardKey(projectId));
            if (json == null) return Optional.empty();
            return Optional.of(mapper.readValue(json, BoardView.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void putBoard(String projectId, BoardView board) {
        try {
            cmd.setex(boardKey(projectId), 30, mapper.writeValueAsString(board));
        } catch (Exception ignored) {}
    }

    public void invalidateBoard(String projectId) {
        cmd.del(boardKey(projectId));
    }

    public boolean checkRateLimit(String key, int max, Duration window) {
        String count = cmd.get(key);
        if (count == null) {
            cmd.setex(key, window.getSeconds(), "1");
            return true;
        }
        int n = Integer.parseInt(count);
        if (n >= max) return false;
        cmd.incr(key);
        return true;
    }

    public Optional<IdempotencyRecord> getIdempotency(String key) {
        try {
            String json = cmd.get("idempotency:" + key);
            if (json == null) return Optional.empty();
            return Optional.of(mapper.readValue(json, IdempotencyRecord.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void storeIdempotency(String key, IdempotencyRecord record) {
        try {
            cmd.setex("idempotency:" + key, 86400, mapper.writeValueAsString(record));
        } catch (Exception ignored) {}
    }

    private String boardKey(String projectId) {
        return "board:" + projectId;
    }

    @Override
    public void close() {
        conn.close();
        client.shutdown();
    }

    public record IdempotencyRecord(String body, int statusCode) {}
}
