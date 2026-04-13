# Design Spec: Navigation Backstack for AppMode

## Problem
Currently, navigation between different application modes (e.g., `DEBUG_INSPECT_CLASS` to `DEBUG_HOOK_WATCH`) uses hardcoded logic for the `ESC` key. This leads to inconsistent behavior, such as jumping back to the entrypoint instead of the previous screen.

## Proposed Solution: Navigation Backstack (Approach 2)
Implement a robust navigation history using a stack-based approach. This ensures that the `ESC` key always takes the user back to the exact screen they came from.

### 1. App State (`AppState.kt`)
Add a new property to track the history of modes:
- `var modeStack: MutableList<AppMode> = mutableListOf()`

### 2. Navigation Helper (`AppState.kt`)
Add helper methods to manage navigation safely:
- `pushMode(newMode: AppMode)`: Saves the current mode to the stack before switching to the new mode.
- `popMode()`: Pops the last mode from the stack and sets it as the current mode. If the stack is empty, it can default to a safe mode or exit (depending on the context).

### 3. State Transitions (`Main.kt` & `CommandExecutor.kt`)
Update all locations where `state.mode = AppMode.XYZ` is called:
- Use `state.pushMode(AppMode.XYZ)` instead of direct assignment to ensure the previous state is preserved.
- Specifically, the transition from `DEBUG_INSPECT_CLASS` to `DEBUG_HOOK_WATCH` (triggered by 'W') must be tracked.

### 4. ESC Key Handling (`Main.kt`)
Refactor the `KeyEvent.Esc` handler:
- Instead of complex `if/else` blocks checking the current mode, use `state.popMode()`.
- Special handling for `DEBUG_INSPECT_CLASS` (backstack of classes) should be integrated with the global mode stack or maintained as is if it's strictly internal to that mode.

## Success Criteria
- Pressing 'W' in `Inspect` takes the user to `Watch`.
- Pressing `ESC` in `Watch` (if entered from `Inspect`) takes the user back to `Inspect`.
- Navigation remains consistent across all modes (`Classes`, `Inspect`, `Watch`).
- No "jumps" to the entrypoint unless it was the actual previous screen.
