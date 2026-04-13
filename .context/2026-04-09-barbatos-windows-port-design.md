# Barbatos Windows Port (WSL / Linux X64 Support) Design

**Goal:** Enable `barbatos` to run on Windows via WSL by supporting the `linuxX64` architecture and refactoring common Unix/Linux logic to avoid code duplication.

## Architecture

The project will be refactored into a cleaner source set hierarchy:

1.  **`commonMain`**: Shared between all platforms (Kotlin core logic).
2.  **`unixMain`** (New, replaces `unixArm64Main`):
    *   Shared by: `macosArm64`, `linuxArm64`, `linuxX64`.
    *   Responsibilities: POSIX-compliant code, ANSI Rendering, TUI Main loop, POSIX Time utilities.
3.  **`linuxMain`** (New):
    *   Shared by: `linuxArm64`, `linuxX64`.
    *   Responsibilities: Linux-specific terminal ioctl constants (`TIOCGWINSZ = 0x5413u`), Linux network engine (`Curl`).
4.  **Target-Specific Sources**:
    *   `macosArm64Main`: macOS-specific binary paths, `Darwin` network engine.
    *   `linuxArm64Main`: Linux ARM64 binary path.
    *   `linuxX64Main`: Linux X64 binary path.

## Components & Data Flow

### Terminal Size (TIOCGWINSZ)
- **macOS (ARM64)**: `0x40087468u`
- **Linux (ARM64/X64)**: `0x5413u`
These values will be moved to their respective `actual` declarations in `linuxMain` and `macosArm64Main`.

### Network Engine (Ktor)
- **macOS**: `io.ktor:ktor-client-darwin`
- **Linux**: `io.ktor:ktor-client-curl` (shared in `linuxMain`)

### Binary Path (Tmux)
- Each architecture will define its own `actual val barbatosBinaryPath` to point to the correct build output folder (`macosArm64`, `linuxArm64`, or `linuxX64`).

## Testing Strategy

1.  **Unit Tests**: Existing tests in `commonTest` should continue passing on all targets.
2.  **Build Validation**: Verify that `./gradlew linkDebugExecutableLinuxX64` succeeds.
3.  **TUI Validation**: Run the binary inside a WSL environment to confirm that:
    *   Terminal resizing works correctly (TIOCGWINSZ).
    *   Tmux splits work correctly (Binary path).
    *   Input handling (Enter/Backspace) works as expected.

## Error Handling

- **Missing Curl**: On Linux/WSL, ensure `libcurl` is mentioned as a dependency in the README (already common for Ktor/C-Interop).
- **Tmux presence**: The existing `TmuxManager.checkTmux()` will already handle cases where tmux is not installed in the WSL instance.
