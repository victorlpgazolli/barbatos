# Design Spec: Navigation Backstack (Approach B)

## Problem
Navigation between different modes (`AppMode`) in `barbatos` is currently hardcoded and inconsistent. For example, pressing `ESC` in `DEBUG_HOOK_WATCH` (Watch/Hook) always returns to `DEBUG_ENTRYPOINT`, even if the user came from `DEBUG_INSPECT_CLASS` (Inspect).

## Proposed Solution: Global Navigation Stack
We will implement a unified `navigationStack` that tracks `AppMode` transitions. This allows the `ESC` key to always return the user to the previous screen.

### 1. App State (`AppState.kt`)
- Add `var navigationStack: MutableList<AppMode> = mutableListOf()` to track history.
- **Note:** The current `inspectBackStack: MutableList<String>` will be kept ONLY for class-to-class navigation within `Inspect` mode, OR merged if possible. Since the user chose Approach B (Intent), we will simplify by making `AppMode` the primary stack unit.

### 2. Navigation Helper (`AppState.kt`)
Add a helper method to handle forward navigation:
- `fun pushMode(newMode: AppMode)`: Adds current mode to `navigationStack` and switches to `newMode`.

### 3. Implementation Plan

#### Forward Navigation Transitions:
1. **Entrypoint -> Classes:** `pushMode(DEBUG_CLASS_FILTER)`
2. **Entrypoint -> Watch (Direct):** `pushMode(DEBUG_HOOK_WATCH)`
3. **Classes -> Inspect:** `pushMode(DEBUG_INSPECT_CLASS)`
4. **Inspect -> Watch ('W'):** `pushMode(DEBUG_HOOK_WATCH)`
5. **Inspect -> Inspect ('I'):** `pushMode(DEBUG_INSPECT_CLASS)` (This handles recursive inspection while reusing the same logic).

#### Backward Navigation (`ESC` in `Main.kt`):
Instead of:
```kotlin
if (mode == HOOK_WATCH) mode = ENTRYPOINT
else if (mode == CLASS_FILTER) mode = ENTRYPOINT
...
```
We will use:
```kotlin
if (state.navigationStack.isNotEmpty()) {
    val previousMode = state.navigationStack.removeAt(state.navigationStack.size - 1)
    state.mode = previousMode
    // Restore mode-specific context if needed (e.g. if returning to Inspect, re-fetch class if changed)
} else {
    // Default fallback (e.g. exit or back to entrypoint)
}
```

### 4. Handling Recursive Inspection ('I' and 'ESC' in Inspect)
When navigating from Class A to Class B:
- `pushMode(DEBUG_INSPECT_CLASS)`
- Save `Class A` into `inspectBackStack`.
- When `ESC` pops `DEBUG_INSPECT_CLASS`, we also pop from `inspectBackStack` to restore the class name.

## Success Criteria
- Pressing 'W' from `Inspect` and then `ESC` returns to `Inspect`.
- Pressing `ESC` from `Inspect` (first class) returns to `Classes`.
- Pressing `ESC` from `Classes` returns to `Entrypoint`.
- Recursive inspection ('I') remains functional and returns to the parent class on `ESC`.
