# GEMINI.md — barbatos TUI Debugger

## Project Overview

`barbatos` (Interactive Debug Kit) is a **Kotlin Native TUI debugger** for Android apps, running on macOS ARM64. It provides an interactive terminal UI for live debugging via Frida, with no external TUI libraries — everything is built from scratch using POSIX `termios` and ANSI escape codes.

The tool bridges:
- **Kotlin Native binary** (`src/nativeMain/kotlin/`) — the TUI shell and state machine.
- **Python bridge** (`bridge/bridge.py`) — Frida host-side, exposes a JSON-RPC HTTP server.
- **Frida JS agent** (`bridge/agent.js`) — injected into the Android process, exports RPC functions.

tmux is used to manage debug sessions and side-by-side inspection panels.

---

## Architecture

```
Main.kt → AppState.kt (state machine)
       → InputHandler.kt (key events & modifier parsing)
       → Renderer.kt (ANSI output & layout)
       → ListRenderer.kt (Shared viewport & selection utilities)
       → CommandExecutor.kt (command dispatch & async orchestration)
       → RpcClient.kt (Ktor HTTP → bridge.py → agent.js → Frida → Android)
       → TmuxManager.kt (tmux session/window/pane management)
       → HistoryStore.kt (~/.cache/barbatos/history.txt)
       → SessionStore.kt (~/.cache/barbatos/sessions.toml)
       → CacheManager.kt (~/.cache/barbatos/)
```

### AppMode state machine

Modes are defined in `AppState.kt` as an `AppMode` enum:
- `DEFAULT` — command input with autocomplete and persistent history navigation.
- `DEBUG_ENTRYPOINT` — menu after gadget install (inspect classes vs hook methods).
- `DEBUG_CLASS_FILTER` — filterable class list from Frida with autofill package name.
- `DEBUG_INSPECT_CLASS` — tree-based inspection of fields/methods and live instances.

---

## Build & Run

```bash
# 1. Start the bridge (runs on localhost:8080 by default)
# Ensure frida-java-bridge is installed: cd bridge && npm install
python3 ./bridge/bridge.py

# 2. Build native binary
./gradlew linkDebugExecutableMacosArm64

# 3. Run
./build/bin/macosArm64/debugExecutable/barbatos.kexe
```

Target: `macosArm64`. Entry point: `main` in `Main.kt`. Binary base name: `barbatos`.

---

## Key Features

- **Editor-like Navigation:** CMD+Arrows, Ctrl+A/E, and Option+Backspace work across all inputs.
- **Persistent Command History:** Up/Down arrows in `DEFAULT` mode navigate history stored in `~/.cache/barbatos/history.txt`.
- **Tree-based Inspection:** `DEBUG_INSPECT_CLASS` uses ASCII tree guidelines (`├──`, `└──`) for clear object hierarchies.
- **Async Gadget Installation:** Step-by-step checklist UI for ADB preparation, gadget deployment, and JDWP injection.
- **Flicker-free Polish:** Deterministic line counting and strict string truncation prevent implicit terminal scrolling.
- **Sticky Footer:** Mode-specific keybinding shortcuts displayed at the bottom of the terminal.

---

## Development Conventions

### Async / non-blocking UI
The main loop runs on the main thread. Network calls via Ktor must be launched in a background coroutine using `CoroutineScope(Dispatchers.Default)`. Results are passed back via `AtomicReference` fields on `AppState`, polled on `KeyEvent.Timeout` ticks (~100ms). **Never block the main loop.**

### Input Handling
Always use the `onInputChanged(state)` helper in `Main.kt` when modifying `inputBuffer`. This ensures suggestions, debounced searches, and "Ctrl+C" reset logic are handled consistently.

### List Rendering
Use `ListRenderer.computeViewport` for scrollable lists and `ListRenderer.selectionPrefix` for the standardized green `> ` marker. In tree views, ensure every logic row equals exactly one visual line to prevent "jumping" headers.

### Frida RPC
**Always use lowercase function names in `agent.js`** (e.g., `listclasses`, `countinstances`).
Frida requests must have `encodeDefaults = true` in the Kotlin JSON configuration to ensure the `method` field is always serialized.

---

## Known Platform Gotchas (macOS ARM64)

| Problem | Solution |
|---|---------|
| `TIOCGWINSZ` value | macOS (0x40087468u) vs Linux (0x5413u) handled in `Terminal.getSize()`. |
| `Runtime.getRuntime()` | JVM-only API — use POSIX equivalents or Frida `Java.perform`. |
| Cursor flickering | Use `CLEAR_SCREEN` only at the start of frame and ensure total output height ≤ terminal height. |
| Duplicate instances | Frida's `Java.choose` can yield same object multiple times; deduplicate via `$handle` in `agent.js`. |
