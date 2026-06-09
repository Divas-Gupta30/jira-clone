package com.projectboard.api;

import com.projectboard.config.AppConfig;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class OpenApiRoutes {

    private static final String SPEC_RESOURCE = "/openapi.yaml";
    private final AppConfig config;

    public OpenApiRoutes(AppConfig config) {
        this.config = config;
    }

    public void register(Javalin app) {
        app.get("/api/docs/openapi.yaml", this::serveSpec);
        app.get("/api/docs", ctx -> ctx.html(swaggerHtml()));
        app.get("/api/docs/", ctx -> ctx.redirect("/api/docs"));
        if (config.hasPublicBaseUrl()) {
            app.get("/api/public-url", ctx -> ctx.json(java.util.Map.of(
                    "publicBaseUrl", config.publicBaseUrl(),
                    "docsUrl", config.publicBaseUrl() + "/api/docs",
                    "websocketUrl", wsPublicUrl()
            )));
        }
    }

    private String wsPublicUrl() {
        String base = config.publicBaseUrl();
        if (base.startsWith("https://")) {
            return "wss://" + base.substring("https://".length()) + "/ws/board";
        }
        if (base.startsWith("http://")) {
            return "ws://" + base.substring("http://".length()) + "/ws/board";
        }
        return base + "/ws/board";
    }

    private void serveSpec(Context ctx) {
        try (InputStream in = getClass().getResourceAsStream(SPEC_RESOURCE)) {
            if (in == null) {
                ctx.status(404).result("OpenAPI spec not found");
                return;
            }
            String spec = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (config.hasPublicBaseUrl()) {
                spec = spec.replace("http://localhost:8001", config.publicBaseUrl().replaceAll("/$", ""));
            }
            ctx.contentType("application/yaml");
            ctx.result(spec);
        } catch (Exception e) {
            ctx.status(500).result("Failed to load OpenAPI spec");
        }
    }

    private String swaggerHtml() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8"/>
              <title>Project Board API</title>
              <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css"/>
            </head>
            <body>
              <div id="swagger-ui"></div>
              <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
              <script>
                SwaggerUIBundle({
                  url: '/api/docs/openapi.yaml',
                  dom_id: '#swagger-ui',
                  presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset],
                  layout: 'BaseLayout'
                });
              </script>
            </body>
            </html>
            """;
    }
}
