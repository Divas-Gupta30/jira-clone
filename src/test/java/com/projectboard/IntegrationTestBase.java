package com.projectboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectboard.config.AppConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

abstract class IntegrationTestBase {

    static final String PROJECT = "proj_abc";
    static final String MEMBER = "user_member";
    static final String LEAD = "user_lead";
    static final String VIEWER = "user_viewer";

    private static boolean useLocalServices() {
        if (Boolean.getBoolean("integration.local")) {
            return true;
        }
        if (Boolean.getBoolean("integration.testcontainers")) {
            return false;
        }
        return localServicesAvailable();
    }

    private static boolean localServicesAvailable() {
        return portOpen("127.0.0.1", 5432) && portOpen("127.0.0.1", 6379);
    }

    private static boolean portOpen(String host, int port) {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static PostgreSQLContainer<?> postgres;
    static GenericContainer<?> redis;

    static Application.Context appContext;
    static HttpClient http;
    static ObjectMapper json;
    static String baseUrl;

    @BeforeAll
    static void startApp() {
        AppConfig config;
        if (useLocalServices()) {
            config = new AppConfig(
                    env("DB_URL", "jdbc:postgresql://localhost:5432/projectboard"),
                    env("DB_USER", "board"),
                    env("DB_PASSWORD", "board"),
                    env("REDIS_URL", "redis://localhost:6379"),
                    0,
                    5,
                    "test-admin-key",
                    ""
            );
        } else {
            postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("projectboard")
                    .withUsername("board")
                    .withPassword("board");
            redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
            postgres.start();
            redis.start();
            String redisUrl = "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
            config = new AppConfig(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword(),
                    redisUrl,
                    0,
                    5,
                    "test-admin-key",
                    ""
            );
        }

        appContext = Application.create(config);
        appContext.app().start(0);
        baseUrl = "http://localhost:" + appContext.app().port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        json = new ObjectMapper();
    }

    @AfterAll
    static void stopApp() {
        if (appContext != null) {
            appContext.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    private static String env(String key, String fallback) {
        String val = System.getenv(key);
        return val != null && !val.isBlank() ? val : fallback;
    }

    HttpResponse<String> get(String path) throws Exception {
        return get(path, null);
    }

    HttpResponse<String> get(String path, String userId) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
        if (userId != null) {
            builder.header("X-User-Id", userId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, String userId, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (userId != null) {
            builder.header("X-User-Id", userId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> patch(String path, String userId, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        builder.header("X-User-Id", userId);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> adminGet(String path) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("X-Admin-Key", "test-admin-key")
                .GET();
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> adminPost(String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("X-Admin-Key", "test-admin-key")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> adminDelete(String path) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("X-Admin-Key", "test-admin-key")
                .DELETE();
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    JsonNode parse(String body) throws Exception {
        return json.readTree(body);
    }

    JsonNode createTaskIssue(String title) throws Exception {
        var res = post("/api/v1/projects/" + PROJECT + "/issues", MEMBER, """
                {"type":"TASK","title":"%s","priority":"LOW"}
                """.formatted(title.replace("\"", "\\\"")));
        if (res.statusCode() != 201) {
            throw new IllegalStateException("create issue failed: " + res.statusCode() + " " + res.body());
        }
        return parse(res.body());
    }

    int issueVersion(String issueId) throws Exception {
        var res = get("/api/v1/issues/" + issueId, MEMBER);
        return parse(res.body()).get("version").asInt();
    }

    JsonNode listSprints() throws Exception {
        var res = get("/api/v1/projects/" + PROJECT + "/sprints", MEMBER);
        if (res.statusCode() != 200) {
            throw new IllegalStateException("list sprints failed: " + res.statusCode() + " " + res.body());
        }
        return parse(res.body());
    }

    String sprintStatus(String sprintId) throws Exception {
        for (var sprint : listSprints()) {
            if (sprintId.equals(sprint.get("id").asText())) {
                return sprint.get("status").asText();
            }
        }
        throw new IllegalStateException("sprint not found: " + sprintId);
    }

    Optional<String> firstSprintWithStatus(String status) throws Exception {
        for (var sprint : listSprints()) {
            if (status.equals(sprint.get("status").asText())) {
                return Optional.of(sprint.get("id").asText());
            }
        }
        return Optional.empty();
    }

    void ensurePlannedSprint(String sprintId, String name) throws Exception {
        try (var conn = appContext.dataSource().getConnection();
             var ps = conn.prepareStatement("""
                     INSERT INTO sprints (id, project_id, name, start_date, end_date, status)
                     VALUES (?, ?, ?, DATE '2024-03-01', DATE '2024-03-14', 'PLANNED')
                     ON CONFLICT (id) DO UPDATE
                     SET status = 'PLANNED', name = EXCLUDED.name, project_id = EXCLUDED.project_id
                     """)) {
            ps.setString(1, sprintId);
            ps.setString(2, PROJECT);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }
}
