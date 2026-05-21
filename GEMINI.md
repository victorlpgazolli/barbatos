# GEMINI.md — Barbatos JSON-RPC Bridge

## Project Overview

`barbatos` is a high-performance **Kotlin Multiplatform (KMP) JSON-RPC Bridge** designed for Android and iOS runtime debugging via Frida. It replaces the legacy Python bridge and TUI with a unified, native binary that exposes a standardized JSON-RPC 2.0 API over HTTP.

The bridge enables both human developers (via TUI or Web) and AI agents (via MCP) to interact with live application memory, inspect objects, and hook methods with zero setup.

---

## Technical Architecture

The project follows a modular, interface-driven design to ensure testability and multi-platform support.

### Component Map
```
Main.kt (Native) ──▶ NativeFridaBridge (Unix)
Server.kt (Common) ──▶ RpcHandler (Common) ──▶ FridaBridge (Interface)
                                             ┝━ MockFridaBridge (Test/Common)
                                             ┕━ NativeFridaBridge (Prod/Native)
```

### Key Modules
- **Server (`src/commonMain/kotlin/server/`)**: Built on **Ktor (CIO engine)**. Exposes `/ping` for health checks and `/rpc` for all debugger operations.
- **RPC Handler (`src/commonMain/kotlin/rpc/`)**: Orchestrates request parsing, validation against JSON-RPC 2.0 standards, and routing to the active `FridaBridge` implementation.
- **Native Bridge (`src/unixMain/kotlin/bridge/`)**: The core engine. Interacts with **Frida Core (C API)** using Kotlin Native **CInterop**. It statically links `libfrida-core.a` and manages process attachment and script injection.
- **Resources (`src/commonMain/resources/`)**: Contains the JavaScript agents (`agent.js`, `agent.objc.js`) that are injected into the target processes.

---

## Build & Development

### Requirements
- **macOS ARM64** or **Linux (x64/ARM64)**.
- **JDK 17+**.
- **Frida Devkit**: Headers and static libraries managed by our automation.

### Setup & Run
```bash
# 1. Prepare environment (Download Frida Core SDK)
./scripts/download_frida_devkit.sh

# 2. Compile Release Binary
./gradlew linkReleaseExecutableMacosArm64

# 3. Start Bridge
./build/bin/macosArm64/releaseExecutable/barbatos.kexe
```

The bridge will listen on `http://127.0.0.1:8080`.

---

## Engineering Standards

### TDD (Test Driven Development)
All endpoints must be validated in `src/commonTest/kotlin/rpc/RpcHandlerTest.kt` using `MockFridaBridge`.
- **Verify Failure**: `./gradlew macosArm64Test`
- **Align Schema**: Check against `web/openapi.yaml`.
- **Strict Logic**: Return HTTP 200 with standard JSON-RPC error codes (-32601, -32603, etc.) for application-level errors.

### CInterop & Native
- **Definition**: `src/nativeInterop/cinterop/frida.def`.
- **Linkage**: Static linking to avoid runtime shared library dependency.
- **Memory**: Use `memScoped` and `pin` when interacting with C pointers to ensure safety.

### Documentation
- **Language**: English only (code, comments, commits, and docs).
- **Style**: No emojis, concise and technical tone.

---

## CI/CD & Publishing

### GitHub Actions
The project uses automated workflows for validation and distribution:
- **Validation (`ci-validation.yml`)**: Triggered on every PR. Runs unit tests on macOS and Linux (x64/ARM64) and verifies native compilation.
- **Release (`release.yml`)**: Triggered on tag push (`v*`). Builds production binaries, assembles Debian packages, builds Snaps, and publishes to:
  - GitHub Releases
  - Self-hosted APT Repository
  - Snapcraft Store

### Release Process
1.  **Tagging**: `git tag -a v1.0.0 -m "Release v1.0.0" && git push origin v1.0.0`.
2.  **SDK Management**: CI automatically downloads the correct Frida Devkit using `./scripts/download_frida_devkit.sh`.
3.  **Artifacts**: A single unified binary `barbatos` is packaged for all supported platforms.
