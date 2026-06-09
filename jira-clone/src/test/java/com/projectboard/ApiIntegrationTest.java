package com.projectboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("API integration tests")
class ApiIntegrationTest extends IntegrationTestBase {

    @Nested
    @DisplayName("Health & docs")
    class HealthTests {

        @Test
        void liveness() throws Exception {
            var res = get("/api/health/live");
            assertEquals(200, res.statusCode());
            assertEquals("UP", parse(res.body()).get("status").asText());
        }

        @Test
        void readiness() throws Exception {
            var res = get("/api/health/ready");
            assertEquals(200, res.statusCode());
            assertEquals("UP", parse(res.body()).get("status").asText());
        }

        @Test
        void metrics() throws Exception {
            var res = get("/api/metrics");
            assertEquals(200, res.statusCode());
            assertTrue(res.body().contains("ws_active_connections") || res.body().contains("jvm"));
        }

        @Test
        void openApiSpec() throws Exception {
            var res = get("/api/docs/openapi.yaml");
            assertEquals(200, res.statusCode());
            assertTrue(res.body().contains("openapi:"));
        }

        @Test
        void swaggerUi() throws Exception {
            var res = get("/api/docs");
            assertEquals(200, res.statusCode());
            assertTrue(res.body().contains("swagger"));
        }
    }

    @Nested
    @DisplayName("Auth & RBAC")
    class AuthTests {

        @Test
        void missingUserHeaderReturns401() throws Exception {
            var res = get("/api/v1/projects/" + PROJECT + "/board");
            assertEquals(401, res.statusCode());
            assertEquals("UNAUTHORIZED", parse(res.body()).get("error").asText());
        }

        @Test
        void viewerCannotCreateIssue() throws Exception {
            var res = post("/api/v1/projects/" + PROJECT + "/issues", VIEWER, """
                    {"type":"TASK","title":"Nope","priority":"LOW"}
                    """);
            assertEquals(403, res.statusCode());
        }

        @Test
        void memberCanReadBoard() throws Exception {
            var res = get("/api/v1/projects/" + PROJECT + "/board", MEMBER);
            assertEquals(200, res.statusCode());
            assertTrue(parse(res.body()).get("columns").size() >= 4);
        }

        @Test
        void viewerCanReadBoard() throws Exception {
            var res = get("/api/v1/projects/" + PROJECT + "/board", VIEWER);
            assertEquals(200, res.statusCode());
        }

        @Test
        void memberCannotStartSprint() throws Exception {
            var res = post("/api/v1/sprints/sprint_11/start", MEMBER, null);
            assertEquals(403, res.statusCode());
        }
    }

    @Nested
    @DisplayName("Issues (v1)")
    class IssueTests {

        @Test
        void getIssue() throws Exception {
            var res = get("/api/v1/issues/PROJ-123", MEMBER);
            assertEquals(200, res.statusCode());
            assertEquals("PROJ-123", parse(res.body()).get("issueId").asText());
        }

        @Test
        void createIssue() throws Exception {
            var issue = createTaskIssue("Integration test bug");
            assertTrue(issue.get("issueId").asText().startsWith("PROJ-"));
            assertEquals("Integration test bug", issue.get("title").asText());
        }

        @Test
        void updateIssue() throws Exception {
            var created = createTaskIssue("Patch me");
            String issueId = created.get("issueId").asText();
            int version = created.get("version").asInt();

            var res = patch("/api/v1/issues/" + issueId, MEMBER, """
                    {"version": %d, "priority": "HIGH"}
                    """.formatted(version));
            assertEquals(200, res.statusCode());
            assertEquals("HIGH", parse(res.body()).get("priority").asText());
            assertEquals(version + 1, parse(res.body()).get("version").asInt());
        }

        @Test
        void staleVersionReturns409() throws Exception {
            var created = createTaskIssue("Conflict test");
            String issueId = created.get("issueId").asText();
            int version = created.get("version").asInt();

            patch("/api/v1/issues/" + issueId, MEMBER, """
                    {"version": %d, "title": "First"}
                    """.formatted(version));

            var res = patch("/api/v1/issues/" + issueId, LEAD, """
                    {"version": %d, "title": "Stale"}
                    """.formatted(version));
            assertEquals(409, res.statusCode());
            assertEquals("CONFLICT", parse(res.body()).get("error").asText());
            assertNotNull(parse(res.body()).get("current"));
        }

        @Test
        void validTransition() throws Exception {
            var created = createTaskIssue("Transition test");
            String issueId = created.get("issueId").asText();
            int version = created.get("version").asInt();

            var res = post("/api/v1/issues/" + issueId + "/transitions", MEMBER, """
                    {"toStatus": "In Progress", "version": %d}
                    """.formatted(version));
            assertEquals(200, res.statusCode());
            assertEquals("In Progress", parse(res.body()).get("status").asText());
        }

        @Test
        void invalidTransitionReturns422() throws Exception {
            var created = createTaskIssue("Invalid transition test");
            String issueId = created.get("issueId").asText();
            int version = created.get("version").asInt();

            var res = post("/api/v1/issues/" + issueId + "/transitions", MEMBER, """
                    {"toStatus": "Done", "version": %d}
                    """.formatted(version));
            assertEquals(422, res.statusCode());
            assertEquals("VALIDATION_ERROR", parse(res.body()).get("error").asText());
            assertTrue(parse(res.body()).get("allowed_transitions").size() > 0);
        }

        @Test
        void moveIssueToSprint() throws Exception {
            var created = createTaskIssue("Sprint move test");
            String issueId = created.get("issueId").asText();

            var res = post("/api/v1/issues/" + issueId + "/sprint", LEAD, """
                    {"sprintId": "sprint_10"}
                    """);
            assertEquals(204, res.statusCode());
        }
    }

    @Nested
    @DisplayName("Board")
    class BoardTests {

        @Test
        void boardHasIssuesInColumns() throws Exception {
            var res = get("/api/v1/projects/" + PROJECT + "/board", MEMBER);
            assertEquals(200, res.statusCode());
            var columns = parse(res.body()).get("columns");
            assertTrue(columns.size() >= 4);
            boolean hasIssue = false;
            for (var col : columns) {
                if (col.get("issues").size() > 0) {
                    hasIssue = true;
                }
            }
            assertTrue(hasIssue);
        }
    }

    @Nested
    @DisplayName("Sprints")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SprintTests {

        @Test
        @Order(1)
        void listSprints() throws Exception {
            var res = get("/api/v1/projects/" + PROJECT + "/sprints", MEMBER);
            assertEquals(200, res.statusCode());
            assertTrue(parse(res.body()).size() >= 2);
        }

        @Test
        @Order(2)
        void completeSprint() throws Exception {
            ensurePlannedSprint("sprint_it_complete", "IT Complete Sprint");

            var start = post("/api/v1/sprints/sprint_it_complete/start", LEAD, null);
            assertEquals(204, start.statusCode(), start.body());

            var res = post("/api/v1/sprints/sprint_it_complete/complete", LEAD, """
                    {"carryOver": []}
                    """);
            assertEquals(200, res.statusCode(), res.body());
            assertEquals("sprint_it_complete", parse(res.body()).get("sprintId").asText());
            assertEquals("COMPLETED", sprintStatus("sprint_it_complete"));
        }

        @Test
        @Order(3)
        void startSprint() throws Exception {
            ensurePlannedSprint("sprint_it_start", "IT Start Sprint");

            var res = post("/api/v1/sprints/sprint_it_start/start", LEAD, null);
            assertEquals(204, res.statusCode(), res.body());
            assertEquals("ACTIVE", sprintStatus("sprint_it_start"));
        }
    }

    @Nested
    @DisplayName("Comments")
    class CommentTests {

        @Test
        void listComments() throws Exception {
            var res = get("/api/v1/issues/PROJ-123/comments", MEMBER);
            assertEquals(200, res.statusCode());
            assertTrue(parse(res.body()).size() >= 1);
        }

        @Test
        void addComment() throws Exception {
            var res = post("/api/v1/issues/PROJ-123/comments", LEAD, """
                    {"body": "Integration test comment @user_member", "parentId": null}
                    """);
            assertEquals(201, res.statusCode());
            assertEquals("user_lead", parse(res.body()).get("author").get("userId").asText());
        }
    }

    @Nested
    @DisplayName("Activity & search")
    class ActivitySearchTests {

        @Test
        void activityFeed() throws Exception {
            var res = get("/api/v1/projects/" + PROJECT + "/activity?limit=5", LEAD);
            assertEquals(200, res.statusCode());
            assertTrue(parse(res.body()).size() >= 1);
        }

        @Test
        void searchByText() throws Exception {
            var res = get("/api/v1/search?q=OAuth&limit=10", MEMBER);
            assertEquals(200, res.statusCode());
            assertTrue(parse(res.body()).get("issues").size() >= 1);
        }

        @Test
        void searchWithFilter() throws Exception {
            var res = get("/api/v1/search?filter=type%20%3D%20%22STORY%22&limit=10", MEMBER);
            assertEquals(200, res.statusCode());
            assertTrue(parse(res.body()).get("issues").size() >= 1);
        }
    }

    @Nested
    @DisplayName("Unversioned API aliases")
    class LegacyAliasTests {

        @Test
        void getBoardViaLegacyPath() throws Exception {
            var res = get("/api/projects/" + PROJECT + "/board", MEMBER);
            assertEquals(200, res.statusCode());
            assertTrue(parse(res.body()).get("columns").size() >= 4);
        }

        @Test
        void createIssueViaLegacyPath() throws Exception {
            var res = post("/api/projects/" + PROJECT + "/issues", MEMBER, """
                    {"type":"TASK","title":"Legacy path","priority":"LOW"}
                    """);
            assertEquals(201, res.statusCode());
            assertTrue(parse(res.body()).get("issueId").asText().startsWith("PROJ-"));
        }

        @Test
        void searchViaLegacyPath() throws Exception {
            var res = get("/api/search?q=OAuth&limit=5", MEMBER);
            assertEquals(200, res.statusCode());
            assertTrue(parse(res.body()).get("issues").size() >= 1);
        }
    }

    @Nested
    @DisplayName("Admin seed API")
    class AdminSeedTests {

        @Test
        void listSeedData() throws Exception {
            var res = adminGet("/api/v1/admin/seed");
            assertEquals(200, res.statusCode());
            assertTrue(parse(res.body()).get("users").size() >= 4);
        }

        @Test
        void memberCannotAccessAdminApi() throws Exception {
            var res = get("/api/v1/admin/seed", MEMBER);
            assertEquals(403, res.statusCode());
        }

        @Test
        void addAndRemoveSeedUser() throws Exception {
            var add = adminPost("/api/v1/admin/seed/users", """
                    {"id":"user_demo","email":"demo@example.com","displayName":"Demo User"}
                    """);
            assertEquals(201, add.statusCode(), add.body());

            var addMember = adminPost("/api/v1/admin/seed/projects/" + PROJECT + "/members", """
                    {"userId":"user_demo","role":"VIEWER"}
                    """);
            assertEquals(201, addMember.statusCode(), addMember.body());

            var removeMember = adminDelete("/api/v1/admin/seed/projects/" + PROJECT + "/members/user_demo");
            assertEquals(204, removeMember.statusCode());

            var removeUser = adminDelete("/api/v1/admin/seed/users/user_demo");
            assertEquals(204, removeUser.statusCode());
        }

        @Test
        void adminUserCanAccessWithoutApiKey() throws Exception {
            var res = get("/api/v1/admin/seed", "user_admin");
            assertEquals(200, res.statusCode());
        }

        @Test
        void createAndRemoveProject() throws Exception {
            var create = adminPost("/api/v1/admin/seed/projects", """
                    {
                      "id": "proj_demo",
                      "key": "DEMO",
                      "name": "Demo Project",
                      "adminUserId": "user_admin"
                    }
                    """);
            assertEquals(201, create.statusCode(), create.body());
            assertEquals("proj_demo", parse(create.body()).get("id").asText());

            var board = get("/api/v1/projects/proj_demo/board", "user_admin");
            assertEquals(200, board.statusCode());
            assertEquals(4, parse(board.body()).get("columns").size());

            var createIssue = post("/api/v1/projects/proj_demo/issues", "user_admin", """
                    {"type":"TASK","title":"First issue","priority":"LOW"}
                    """);
            assertEquals(201, createIssue.statusCode(), createIssue.body());
            assertTrue(parse(createIssue.body()).get("issueId").asText().startsWith("DEMO-"));

            var remove = adminDelete("/api/v1/admin/seed/projects/proj_demo");
            assertEquals(204, remove.statusCode());
        }
    }
}
