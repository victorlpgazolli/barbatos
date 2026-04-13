# Plan: Command History, Exit Command, Editor-like Navigation & Standardized Selectors

## Context

The barbatos TUI debugger has several UX gaps: no command history navigation, no "exit" command, incomplete modifier key handling (CMD+arrows don't work), and inconsistent selector rendering across modes. These changes improve the editor-like feel of the input system and clean up duplicated rendering code.

## Approach

Four improvements delivered in 3 phases. Phase A adds new key events and the exit command (small, self-contained). Phase B adds persistent command history with up/down navigation. Phase C extracts duplicated rendering patterns into a `ListRenderer.kt` utility and standardizes selectors across all modes.

---

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/nativeMain/kotlin/InputHandler.kt` | **Modify** | Add 5 new KeyEvent types + parse CMD/Home/End/Ctrl sequences |
| `src/nativeMain/kotlin/AppState.kt` | **Modify** | Add history navigation state fields |
| `src/nativeMain/kotlin/CommandRegistry.kt` | **Modify** | Add "exit" command |
| `src/nativeMain/kotlin/CommandExecutor.kt` | **Modify** | Handle "exit" command |
| `src/nativeMain/kotlin/Main.kt` | **Modify** | Handle new key events, wire history navigation, load/save history |
| `src/nativeMain/kotlin/HistoryStore.kt` | **Create** | Persistent history file I/O (~/.cache/barbatos/history.txt) |
| `src/nativeMain/kotlin/ListRenderer.kt` | **Create** | Shared viewport, selection marker, spinner, overflow utilities |
| `src/nativeMain/kotlin/Renderer.kt` | **Modify** | Refactor to use ListRenderer, standardize all selectors |

---

## Implementation Steps

### Phase A: New Key Events + Exit Command

**Task 1: Add KeyEvent types** (`InputHandler.kt`)

Add 5 new variants to the KeyEvent sealed class (after line 19):
- `CmdLeft` — Home / beginning of line
- `CmdRight` — End / end of line
- `CmdBackspace` — Delete to beginning of line
- `CtrlA` — Beginning of line (readline)
- `CtrlE` — End of line (readline)

Extend readKey() parsing:
- Byte 1 → CtrlA, byte 5 → CtrlE, byte 21 → CmdBackspace
- CSI arrow C/D: check modifier "9" (CMD) before "3" (Option)
- CSI H/F → CmdLeft/CmdRight (Home/End)
- `\x1bO` sequences: OH → CmdLeft, OF → CmdRight

**Task 2: Handle new key events in main loop** (`Main.kt`)

- CmdLeft/CtrlA → `cursorPosition = 0`
- CmdRight/CtrlE → `cursorPosition = inputBuffer.length`
- CmdBackspace → delete from 0 to cursorPosition, reset cursor

**Task 3: Add exit command** (`CommandRegistry.kt`, `CommandExecutor.kt`)

- Add `Command("exit", "quit the application")` to registry
- Add `"exit" -> { state.running = false }` to executor

### Phase B: Persistent Command History

**Task 4: Create HistoryStore** (`HistoryStore.kt` — new file)

POSIX file I/O matching SessionStore patterns:
- `load()` → reads ~/.cache/barbatos/history.txt, returns lines
- `append(command)` → appends single line (fopen "a" mode)
- `save(commands)` → full overwrite for trimming
- Max 500 entries

**Task 5: Add history navigation state** (`AppState.kt`)

- `historyNavigationIndex: Int = -1` (-1 = not navigating)
- `savedInputBeforeHistory: String = ""` (preserve current input)

**Task 6: Wire history navigation** (`Main.kt`)

- Load history at startup into state.commandHistory
- ArrowUp in DEFAULT (when no suggestions): navigate backward through history
- ArrowDown in DEFAULT (when navigating): navigate forward, restore original input past end
- Reset historyNavigationIndex on any text modification
- Persist on Enter via HistoryStore.append()

### Phase C: Standardize Selectors & Extract Utilities

**Task 7: Create ListRenderer** (`ListRenderer.kt` — new file)

- `computeViewport(totalItems, selectedIndex, maxVisible)` → (startIdx, endIdx) with bottom-clamp
- `selectionPrefix(isSelected, indent)` → consistent `"  > "` GREEN marker
- `spinnerFrame(frameCount)` → current Braille dot character
- `appendOverflow(buf, totalItems, endIdx, indent)` → "... and N more"

**Task 8: Refactor Renderer** (`Renderer.kt`)

- Remove SPINNER_FRAMES (now in ListRenderer)
- renderSuggestions: add `> ` marker via selectionPrefix
- renderDebugEntrypoint: use selectionPrefix
- renderClassList: use computeViewport, selectionPrefix, appendOverflow
- renderInspectClassList: use computeViewport, selectionPrefix, appendOverflow
- All spinner calls: use ListRenderer.spinnerFrame()

## Acceptance Criteria

1. In DEFAULT mode with empty suggestions, Up/Down arrows navigate command history
2. Commands persist across restarts in ~/.cache/barbatos/history.txt (max 500)
3. "exit" command exits the program gracefully
4. CMD+Left / Home / Ctrl+A moves cursor to position 0
5. CMD+Right / End / Ctrl+E moves cursor to end
6. CMD+Backspace / Ctrl+U deletes text from cursor to line start
7. All modes use consistent `"  > "` GREEN marker for selection
8. computeViewport() replaces duplicated scroll logic
9. ListRenderer.spinnerFrame() replaces all inline spinner code

## Verification Steps

1. `./gradlew build` — must compile without errors
2. Run binary, type "exit" + Enter → exits
3. Type "debug" + Enter, exit, relaunch → Up arrow shows "debug"
4. Type text, CMD+Left → cursor at start; CMD+Right → at end
5. Ctrl+U → deletes to start of line
6. Visual: selection markers consistent across all modes