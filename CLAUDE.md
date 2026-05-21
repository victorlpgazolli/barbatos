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
- **API**: Ktor-based JSON-RPC 2.0 server on port 8080.
- **Frida Integration**: Kotlin Native CInterop mapped to `libfrida-core.a`.
- **Mocking**: `MockFridaBridge` used for all unit tests to simulate Frida behavior without devices.

## Coding Standards
- **Language**: All code and documentation must be in **English**.
- **TDD**: New endpoints must have corresponding test cases in `RpcHandlerTest.kt`.
- **RPC Protocol**:
    - Always return **HTTP 200** for application-level errors (method not found, etc.).
    - Use standard JSON-RPC 2.0 error codes:
        - `-32700`: Parse error
        - `-32601`: Method not found
        - `-32603`: Internal error
- **Style**:
    - Use `HandlerResult` for robust HTTP response handling in the server.
    - Prefer interface-driven design (`FridaBridge`).
    - Use `ShellResult` for system command execution.
    - No emojis in commit messages or code.

## File Structure
- `src/commonMain/kotlin/rpc/`: JSON-RPC models and handler logic.
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
