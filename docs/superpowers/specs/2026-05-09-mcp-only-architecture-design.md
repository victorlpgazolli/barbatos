# Transition to 100% MCP Architecture

## 1. Context & Goal
The barbatos project is shifting from a dual architecture (Kotlin-based TUI and Python-based MCP server) to an exclusively MCP-based architecture. The TUI will be completely removed, and the Python-based MCP server (currently referred to as the bridge) will become the sole primary artifact, renamed to `barbatos`. All changes must be made in an isolated git worktree.

## 2. Codebase Cleanup (TUI Removal)
The following files and directories related to the Kotlin TUI will be permanently removed from the repository:
- `src/` (Entire Kotlin source code)
- `gradle/`, `gradlew`, `gradlew.bat` (Gradle wrapper)
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` (Gradle configuration)

## 3. CI/CD Modifications (.github/**)
The CI pipelines will be simplified to build only the Python MCP application.
- **ci-validation.yml:** Remove the `kotlin-test-and-linux-build` and `kotlin-macos-build` jobs. The workflow will only execute `bridge-validation` (Python tests and syntax checks).
- **Actions (build-linux, build-macos, setup-build-env):** Remove JDK installation, Gradle caching, QEMU/cross-compilation steps specific to Kotlin, and Kotlin compilation steps. The build process will only trigger `make compile_bridge`.
- **Artifact Naming:** The CI scripts and `publish-to-platform` action will be updated to output a single artifact named `barbatos` (e.g., `barbatos-linux-x64.zip`, containing the `barbatos` executable), discarding the `-tui` and `-bridge` suffixes.

## 4. Makefile Adjustments
The `Makefile` will be updated to:
- Remove Kotlin compilation targets (`compile_binary`, `GRADLE_TARGET`).
- Modify the `prepare_release` target to copy the PyInstaller output (`bridge/dist/barbatos-bridge`) directly to `dist/barbatos`.
- Remove references to `TUI_BIN`.

## 5. Constraints
- The contents of `bridge/**` will not be modified during this phase.
- All work will be performed inside a dedicated git worktree.
- All documentation, commit messages, and code must be in English.
- No emojis are allowed in commits or documentation.
