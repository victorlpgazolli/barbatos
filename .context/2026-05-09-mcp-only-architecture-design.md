# Transition to Native KMP Architecture

## 1. Context & Goal
The barbatos project is shifting from a dual technology stack (Kotlin TUI and Python Bridge) to a unified **Kotlin Multiplatform (KMP) Native** architecture. The original Python bridge and the TUI have been consolidated into a single, high-performance nativo binary that exposes a JSON-RPC 2.0 API.

## 2. Core Architecture
- **KMP Bridge:** A nativo binary that implements the JSON-RPC contract and interacts directly with Frida Core via CInterop.
- **Unified Codebase:** All logic resides in `src/`, managed by Gradle, eliminating the need for a separate Python environment.
- **MCP Integration:** Future phases will embed the MCP server directly into the KMP binary.

## 3. CI/CD Pipeline
The CI pipelines are optimized for Kotlin/Native:
- **Builds:** Automated for macOS ARM64, Linux x64, and Linux ARM64.
- **Validation:** Every PR runs the full suite of KMP unit tests (using mocks) and performs cross-compilation checks.
- **Frida SDK:** Managed automatically by `scripts/download_frida_devkit.sh` during the build process.

## 4. Maintenance
- **English Only:** All code, comments, and documentation must be in English.
- **TDD:** New features and endpoints MUST be validated via unit tests in `src/commonTest`.
- **Cleanliness:** No obsolete Python or Node.js files should remain in the repository.
