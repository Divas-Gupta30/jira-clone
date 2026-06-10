package com.projectboard.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectboard.domain.model.BoardView;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

public class RedisCache implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisCache.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(2);

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> conn;
    private final RedisCommands<String, String> cmd;
    private final ObjectMapper mapper;

    public RedisCache(String redisUrl, ObjectMapper mapper) {
        this.client = RedisClient.create(redisUrl);
        this.client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build())
                .timeoutOptions(TimeoutOptions.builder()
                        .fixedTimeout(COMMAND_TIMEOUT)
                        .build())
                .autoReconnect(true)
                .build());
        this.conn = client.connect();
        this.cmd = conn.sync();
        this.mapper = mapper;
        log.info("Redis connected");
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
        try {
            cmd.del(boardKey(projectId));
        } catch (Exception ignored) {}
    }

    /** Returns true if allowed. Fails open when Redis is unavailable. */
    public boolean checkRateLimit(String key, int max, Duration window) {
        try {
            String count = cmd.get(key);
            if (count == null) {
                cmd.setex(key, window.getSeconds(), "1");
                return true;
            }
            int n = Integer.parseInt(count);
            if (n >= max) return false;
            cmd.incr(key);
            return true;
        } catch (Exception e) {
            log.warn("Rate limit skipped (Redis unavailable): {}", e.getMessage());
            return true;
        }
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
