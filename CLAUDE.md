# CLAUDE.md — Developer Guide

## Build & Test Commands
- **Download SDK**: `./scripts/download_frida_devkit.sh [arch]` (arch: `arm64`, `aarch64`, `x86_64`)
- **Compile (Native)**: `./gradlew linkReleaseExecutableMacosArm64`
- **Run Tests (macOS)**: `./gradlew macosArm64Test`
- **Run Tests (Linux)**: `./gradlew linuxX64Test`
- **Full Validation**: `./gradlew clean check`
- **Run Binary**: `./build/bin/macosArm64/releaseExecutable/barbatos.kexe`

## Technical Architecture
- **Multiplatform**: Logic in `src/commonMain`, platform-specific entry and Frida bindings in `src/unixMain`.
- **API**: Ktor-based HTTP server on port 8080. REST endpoints under `/v0/api/*` in RPC mode; JSON-RPC 2.0 over Streamable HTTP in MCP mode.
- **Frida Integration**: Kotlin Native CInterop mapped to `libfrida-core.a`.
- **Mocking**: `MockFridaBridge` used for all unit tests to simulate Frida behavior without devices.

## Coding Standards
- **Language**: All code and documentation must be in **English**.
- **TDD**: New endpoints must have corresponding test cases in `RpcHandlerTest.kt`.
- **RPC Protocol**:
    - HTTP transport (`barbatos rpc`): one `POST /v0/api/<snake_case>` per method. The body is the params object and the response is the plain result — no `jsonrpc`/`id` envelope.
    - Errors use real HTTP statuses: `400` malformed body, `404` unknown endpoint, `500` internal error, with a body of `{"error":{"code","message"}}`.
    - Keep standard JSON-RPC error codes in the `error` body for traceability:
        - `-32700`: Parse error
        - `-32601`: Method not found
        - `-32603`: Internal error
    - The MCP transport keeps JSON-RPC 2.0 semantics and forwards application-level errors as non-error MCP results.
- **Style**:
    - Use `HandlerResult` for robust HTTP response handling in the server.
    - Prefer interface-driven design (`FridaBridge`).
    - Use `ShellResult` for system command execution.
    - No emojis in commit messages or code.

## File Structure
- `src/commonMain/kotlin/rpc/`: HTTP and MCP handler logic (models + dispatch).
- `src/commonMain/kotlin/bridge/`: Bridge interfaces and mocks.
- `src/unixMain/kotlin/bridge/`: Real Frida Core implementation.
- `src/commonMain/resources/`: Frida JS agents.
- `scripts/`: Automation for SDK management and CI.
- `web/openapi.yaml`: Single source of truth for the API contract.

## Release & Distribution
- **Tag Release**: `git tag -a v1.x.x -m "version description"`
- **Production Build**: `make release` (assembles `dist/barbatos`)
- **CI Pipelines**:
  - **Validation**: Parallel jobs for Linux (x64/ARM64) and macOS.
  - **Publishing**: Automated distribution to APT, Snap, and GitHub.
