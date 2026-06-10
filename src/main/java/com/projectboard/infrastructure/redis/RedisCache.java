package com.projectboard.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectboard.domain.model.BoardView;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
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
    private final ObjectMapper mapper;
    private volatile StatefulRedisConnection<String, String> conn;
    private volatile RedisCommands<String, String> cmd;

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
        this.mapper = mapper;
        log.info("Redis client configured (connects on first use)");
    }

    private RedisCommands<String, String> commands() {
        RedisCommands<String, String> existing = cmd;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (cmd == null) {
                conn = client.connect();
                cmd = conn.sync();
                log.info("Redis connected");
            }
            return cmd;
        }
    }

    public Optional<BoardView> getBoard(String projectId) {
        try {
            String json = commands().get(boardKey(projectId));
            if (json == null) return Optional.empty();
            return Optional.of(mapper.readValue(json, BoardView.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void putBoard(String projectId, BoardView board) {
        try {
            commands().setex(boardKey(projectId), 30, mapper.writeValueAsString(board));
        } catch (Exception ignored) {}
    }

    public void invalidateBoard(String projectId) {
        try {
            commands().del(boardKey(projectId));
        } catch (Exception ignored) {}
    }

    /** Returns true if allowed. Fails open when Redis is unavailable. */
    public boolean checkRateLimit(String key, int max, Duration window) {
        try {
            RedisCommands<String, String> redis = commands();
            String count = redis.get(key);
            if (count == null) {
                redis.setex(key, window.getSeconds(), "1");
                return true;
            }
            int n = Integer.parseInt(count);
            if (n >= max) return false;
            redis.incr(key);
            return true;
        } catch (RedisConnectionException e) {
            log.warn("Rate limit skipped (Redis unavailable): {}", e.getMessage());
            return true;
        } catch (Exception e) {
            log.warn("Rate limit skipped (Redis error): {}", e.getMessage());
            return true;
        }
    }

    public Optional<IdempotencyRecord> getIdempotency(String key) {
        try {
            String json = commands().get("idempotency:" + key);
            if (json == null) return Optional.empty();
            return Optional.of(mapper.readValue(json, IdempotencyRecord.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void storeIdempotency(String key, IdempotencyRecord record) {
        try {
            commands().setex("idempotency:" + key, 86400, mapper.writeValueAsString(record));
        } catch (Exception ignored) {}
    }

    private String boardKey(String projectId) {
        return "board:" + projectId;
    }

    @Override
    public void close() {
        if (conn != null) {
            conn.close();
        }
        client.shutdown();
    }

    public record IdempotencyRecord(String body, int statusCode) {}
}
