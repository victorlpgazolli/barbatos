# Design: Replace the JSON-RPC envelope with REST-style endpoints under /v0/api

## Goal

Remove the JSON-RPC 2.0 envelope (`jsonrpc`, `id`) from the HTTP transport. Today every
call is a single `POST /rpc` with:

```json
{ "jsonrpc": "2.0", "id": 1, "method": "injectGadgetFromScratch", "params": { "with_logs": true, "limit": 100 } }
```

and returns:

```json
{ "jsonrpc": "2.0", "result": { "status": "completed", "steps": [] }, "id": 1 }
```

The target is one endpoint per method under `/v0/api/<snake_case>`, with the params object
as the body and the plain result as the response:

```
POST /v0/api/inject_gadget_from_scratch
{ "with_logs": true, "limit": 100 }

→ 200
{ "status": "completed", "steps": [] }
```

This drops one level of nesting on both input and output and makes the API trivial to
call from curl or any HTTP client.

## Scope

- HTTP transport only (`Server.kt`, `RpcHandler.kt`, `RpcBase.kt`, tests, docs).
- **MCP transport is intentionally untouched**: `barbatos mcp` keeps its camelCase tool
  names and its JSON-RPC 2.0 wire protocol (MCP is JSON-RPC by spec). MCP and HTTP both
  dispatch into the same `RpcHandler.processMethod`, which stays.
- Frida-internal JSON-RPC (`FridaMessage.kt` / `FridaPayload.RpcRequest`) is a separate
  channel inside the agent and is **not** part of this change.

## Endpoint mapping

camelCase method name → `POST /v0/api/<snake_case>`. Source of truth is
`RpcHandler.tools` (13 wired methods), resolved via a `camelToSnake` helper.

| Method (HTTP path) | Params schema | Result schema |
|---|---|---|
| `POST /v0/api/count_instances` | `CountInstancesParams` | `CountInstancesResult` |
| `POST /v0/api/inspect_class` | `InspectClassParams` | `InspectClassResult` |
| `POST /v0/api/list_instances` | `ListInstancesParams` | `ListInstancesResult` |
| `POST /v0/api/get_instance_addresses` | `GetInstanceAddressesParams` | `GetInstanceAddressesResult` |
| `POST /v0/api/inspect_instance` | `InspectInstanceParams` | `InspectInstanceResult` |
| `POST /v0/api/set_field_value` | `SetFieldValueParams` | `StatusResult` |
| `POST /v0/api/hook_method` | `HookParams` | `StatusResult` |
| `POST /v0/api/get_hook_events` | (none) | `HookEventsResult` |
| `POST /v0/api/set_method_implementation` | `SetMethodImplementationParams` | `StatusResult` |
| `POST /v0/api/run_once` | `RunOnceParams` | `StatusResult` |
| `POST /v0/api/inject_gadget_from_scratch` | `InjectGadgetParams` | `InjectGadgetResult` |
| `POST /v0/api/health_check` | (none) | `HealthCheckResult` |
| `POST /v0/api/list_classes_stream` | `ListClassesParams` | NDJSON stream of `ListClassesPartialResult` |

Unimplemented/doc-only methods (`debugPing`, `testRpc`, `prepareEnvironment`,
`injectJdwp`) exist in `web/openapi.yaml` but are **not** wired in
`RpcHandler.processMethod` today — they would 404. See Decision D6.

## Design decisions

### D1 — Request body

`POST /v0/api/<method>` carries the params object as the raw JSON body
(`Content-Type: application/json`). For parameterless methods the body may be omitted
or empty. `RpcHandler` decodes the body as a nullable `JsonElement` and passes it straight
to `processMethod`; a missing body for a method that needs params fails with
`Missing params` (same behavior as today).

### D2 — Success response

Plain serialized result; no envelope. The response body is exactly what
`processMethod` currently places inside `result`.

### D3 — Error response

Alongside the envelope we drop the "#always 200" contract. Errors now use a body without
`jsonrpc`/`id` and the natural HTTP status:

```json
{ "error": { "code": -32603, "message": "..." } }
```

| Case | HTTP status | code |
|---|---|---|
| Malformed JSON body / empty body where params required | `400` | `-32700` |
| Unknown endpoint (not under `/v0/api`) | `404` | `-32601` |
| Bridge / handler threw | `500` | `-32603` |

The numeric codes are kept for continuity with MCP tool errors and any client that logs
them; only the envelope is removed. `CLAUDE.md`'s "always HTTP 200 for application-level
errors" rule is updated to reflect this (HTTP transport), while MCP keeps the JSON-RPC
codes it already uses.

### D4 — /rpc backwards compatibility

**Removal is recommended** — the project is at `2.0.0-alpha`, so breaking the HTTP
surface now is cheap and avoids carrying two parsers. If kept, add an alias route that
maps a `method` field to the same handler; still recommend shipping a deprecation and
removing in the next minor.

### D5 — Streaming (`list_classes_stream`)

Stays NDJSON (`application/x-ndjson`). Each line becomes the plain chunk object:

```json
{ "list": ["com.example.MainActivity"] }
```

instead of the `RpcResponse` envelope. A mid-stream failure emits one error line
`{"error":{"code":-32603,"message":"..."}}` and ends the stream.

MCP's `streamToolResult` accumulates these plain chunk lines instead of envelopes; its
error detection changes from "decodes as `RpcErrorResponse`" to "line contains an
`error` member".

### D6 — Doc-only methods

`debugPing`, `testRpc`, `prepareEnvironment`, `injectJdwp` are documented in
`web/openapi.yaml` but have no dispatch in `RpcHandler.processMethod` and are absent
from `RpcHandler.tools`. They also have Gradle `registerRpcTask` entries. **Recommendation:**
the new spec only advertises endpoints that actually route. Either (a) implement the four
methods in `processMethod` + add descriptors, or (b) drop them from the spec and from the
Gradle task list. Default to (a) for `prepareEnvironment`/`injectJdwp` (they are
referenced as low-level recovery steps in the session lifecycle docs) and (b) for
`debugPing`/`testRpc` unless they are wired to something real.

## Code changes

### `src/commonMain/kotlin/model/rpc/RpcBase.kt`
- Delete `RpcRequest`, `RpcResponse`, `RpcErrorResponse` (only the HTTP path uses them).
- Add `ApiError(code: Int, message: String)` and `ApiErrorResponse(error: ApiError)`.
- `RpcError` can be reused or folded into `ApiError`.

### `src/commonMain/kotlin/rpc/RpcHandler.kt`
- Add `camelToSnake(name: String)` and expose the route table, e.g.
  `val routeByPath: Map<String, ActionDescriptor>` built from `tools` (both directions:
  path → tool, tool → path). Keep `tools` as the single registry.
- `isStreamMethod(method: String): Boolean` — drop the JSON-parsing implementation.
- `handleStream(method: String, paramsJson: String?, emit: suspend (String) -> Unit)` —
  emit plain `ListClassesPartialResult` lines; error line has no envelope.
- `handle(method: String, body: String?): HandlerResult` — decodes the body
  (null on empty), routes to `processMethod`, returns plain result or `ApiErrorResponse`.
  Parse errors return `code = -32700`.
- Keep `processMethod(method, params)` unchanged (MCP still calls it).
- `streamToolResult(method, params)` for MCP: serialize params directly (no envelope)
  and detect errors from plain error lines.

### `src/commonMain/kotlin/Server.kt`
- In `routing {}`, iterate `RpcHandler.tools`, registering
  `post("/v0/api/${snake_case(name)}")` for each. The stream tool uses
  `respondBytesWriter` with `application/x-ndjson`; everything else is a single JSON object.
- Add a fallback `post("/v0/api/{...}")` → `404` for unknown endpoints.
- Remove the `post("/rpc")` block (per D4).

### `src/commonMain/kotlin/DocsRoutes.kt`
- Update the doc comment that references `/rpc`.

### `src/unixMain/kotlin/Main.kt`
- Update the `rpc` help block: `POST /v0/api/<snake_case>` + `GET /ping`, `GET /docs`,
  `GET /openapi.yaml`.

### `web/openapi.yaml`
The single source of truth — rewrite for the new surface:
- `paths` gains one `post` per endpoint under `/v0/api/...` with `requestBody` = the
  corresponding `*Params` schema and a `200` response = the result schema; add schemes
  for `400`/`404`/`500` referencing an `ApiError` schema.
- Remove `RpcRequest`/`RpcResponse`/`RpcErrorResponse`/`JsonRpcError`; add `ApiError`.
- Replace the "JSON-RPC method contract" section with a per-endpoint description;
  rewrite the transports table (`barbatos rpc` row) and the error-handling section.
- Keep the `*Params`/result schemas unchanged — they are still valid request/response
  bodies, just without the wrapper.
- Bump `version` (e.g. `2.0.0-alpha03`) and note the MCP transport keeps tool names.

### `build.gradle.kts`
- `registerRpcTask`: post to the new URL and send the params object only (no
  `jsonrpc`/`id`). Keep the `barbatos-rpc` gradle tasks or drop/update per D6.

### `CLAUDE.md`
- Update the RPC protocol rules: HTTP transport now returns real status codes and no
  envelope; JSON-RPC error codes still apply on the MCP transport.

### `README.md`
- Update the curl example and the "JSON-RPC 2.0 API" feature bullet / architecture
  diagram label to reflect the REST surface (MCP bullet stays).

## Test changes

### `src/commonTest/kotlin/rpc/RpcHandlerTest.kt`
- Convert every fixture from
  `{"jsonrpc":"2.0","method":"X","id":n,"params":{...}}` to
  `handle("x_snake_case", body)`.
- Rewrite assertions: decode `ApiErrorResponse` for errors, plain object for success.
- Add tests:
  - `camelToSnake` covers all 13 tools (injectGadgetFromScratch →
    inject_gadget_from_scratch, getInstanceAddresses → get_instance_addresses, …).
  - `handle` parse error → `-32700`; unknown path → `-32601`; bridge throw → `-32603`.
  - `handleStream` emits plain chunks (no envelope) and an error line on failure.
- Keep MCP-facing tests intact where they assert `processMethod` behavior.

### `src/commonTest/kotlin/mcp/McpServerTest.kt`
- `toolsCall_listClassesStream_returnsAccumulatedChunks` asserts the text contains
  `"result"` (the old envelope) — update to assert the plain `"list"` chunk instead.
- Add an HTTP routing smoke test (optional but cheap, `ktor-server-test-host` is already
  a dependency): start `module(FakeFridaBridge())`, assert
  `GET /ping`, `POST /v0/api/health_check`, `POST /v0/api/count_instances`,
  `POST /v0/api/inject_gadget_from_scratch`, unknown path → 404.

## Example calls after the change

```bash
curl -s http://127.0.0.1:8080/v0/api/health_check
# {"overall":"ok","checks":{...},"recommendation":null}

curl -s -X POST http://127.0.0.1:8080/v0/api/count_instances \
  -H 'Content-Type: application/json' \
  -d '{"className":"com.example.MainActivity"}'
# {"count":5}

curl -s -X POST http://127.0.0.1:8080/v0/api/inject_gadget_from_scratch \
  -H 'Content-Type: application/json' \
  -d '{"with_logs":true,"limit":100}'
# {"status":"completed","steps":[...],"logs":null}

curl -s -X POST http://127.0.0.1:8080/v0/api/list_classes_stream \
  -H 'Content-Type: application/json' \
  -d '{"search_param":"Main"}'
# application/x-ndjson: {"list":["com.example.MainActivity"]}
```

## Validation

1. `./scripts/download_frida_devkit.sh arm64`
2. `./gradlew macosArm64Test` (Linux: `linuxX64Test`)
3. `./gradlew clean check`
4. Manual smoke: run the binary, hit the endpoints above, confirm `/docs` renders the
   updated spec.

## Risks / notes

- **Breaking change to the whole HTTP surface** — mitigated by the `2.0.0-alpha` version.
- `.context/` plans, `.claude/settings.local.json` curl examples and old `README`/`GEMINI`
  references mention `/rpc`; historical docs are left as-is, live docs are updated.
- MCP tests must stay green: tool names, schemas and `processMethod` are unchanged.
- The 13 endpoint paths are derived from one registry (`RpcHandler.tools`), so a new
  method automatically gets both an MCP tool and an HTTP route with no extra wiring.