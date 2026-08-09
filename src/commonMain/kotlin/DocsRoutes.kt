import io.ktor.http.ContentType
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utils.EmbeddedScripts

private val swaggerUiHtml = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <title>Barbatos API Documentation</title>
      <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
      <style>
        body { margin: 0; padding: 0; }
      </style>
    </head>
    <body>
      <div id="swagger-ui"></div>
      <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
      <script>
        window.onload = () => {
          window.ui = SwaggerUIBundle({
            url: "/openapi.yaml",
            dom_id: '#swagger-ui',
            deepLinking: true,
            presets: [
              SwaggerUIBundle.presets.apis,
              SwaggerUIBundle.SwaggerUIStandalonePreset
            ],
          });
        };
      </script>
    </body>
    </html>
""".trimIndent()

/**
 * Swagger UI for the JSON-RPC bridge, served off the same port as `/rpc`.
 *
 * Installed by [module], so it only exists in HTTP mode — `barbatos mcp` never builds a
 * routing tree.
 *
 * The spec is embedded at build time (`generateResources` reads `web/openapi.yaml`)
 * because nothing in `packaging/` ships that file next to the binary.
 */
fun Route.docsRoutes() {
    get("/") {
        call.respondText(swaggerUiHtml, ContentType.Text.Html)
    }
    get("/docs") {
        call.respondText(swaggerUiHtml, ContentType.Text.Html)
    }
    get("/openapi.yaml") {
        call.respondText(EmbeddedScripts.openapiYaml, ContentType("application", "yaml"))
    }
}
