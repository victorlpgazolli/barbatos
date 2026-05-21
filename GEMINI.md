# GEMINI.md — Barbatos JSON-RPC Bridge

## Project Overview

`barbatos` is a **Kotlin Multiplatform (KMP) JSON-RPC Bridge** for Android and iOS debugging via Frida. It replaces the original Python bridge and TUI with a high-performance nativo binary that exposes a standardized JSON-RPC 2.0 API over HTTP.

The tool bridges:
- **Kotlin Native binary** (`src/unixMain/kotlin/`) — the HTTP server and Frida Core orchestrator.
- **Ktor Server** (`src/commonMain/kotlin/server/`) — handles the HTTP layer (`/ping` and `/rpc`).
- **Frida Core** (CInterop) — linked statically via `libfrida-core.a`.
- **Frida JS agent** (`src/commonMain/resources/agent*.js`) — embedded instrumentation injected into processes.

---

## Architecture

```
Main.kt (Native) → NativeFridaBridge (Unix)
Server.kt (Common) → RpcHandler (Common) → FridaBridge (Interface)
                                       ↳ MockFridaBridge (Testing)
                                       ↳ NativeFridaBridge (Production)
```

### Components
- **FridaBridge**: Common interface defining all debugger operations (list classes, inspect instances, hook methods).
- **RpcHandler**: Logic for parsing JSON-RPC 2.0 requests and routing them to the appropriate bridge implementation.
- **NativeFridaBridge**: The real implementation using `frida-core` via Kotlin Native CInterop.
- **MockFridaBridge**: Used for unit tests and local validation without physical devices.

---

## Build & Run

```bash
# 1. Download Frida Devkit (Headers & Static Lib)
./scripts/download_frida_devkit.sh

# 2. Build native binary
./gradlew linkReleaseExecutableMacosArm64

# 3. Run
./build/bin/macosArm64/releaseExecutable/barbatos.kexe
```

The server starts on `http://127.0.0.1:8080`.

---

## Development Conventions

### TDD Workflow
All new endpoints must be implemented following TDD:
1. Add test case to `RpcHandlerTest.kt`.
2. Verify failure with `./gradlew macosArm64Test`.
3. Update `FridaBridge`, `MockFridaBridge`, `RpcModels` and `RpcHandler`.
4. Verify pass.

### Language & Documentation
**All code comments, commit messages, and documentation MUST be in English.**

### Frida CInterop
- Definition file: `src/nativeInterop/cinterop/frida.def`.
- Static link: `-lfrida-core -lresolv -lpthread`.
- SDK managed by: `scripts/download_frida_devkit.sh`.

---

## API Contract

Refer to `web/openapi.yaml` for the full JSON-RPC method list and schemas.
