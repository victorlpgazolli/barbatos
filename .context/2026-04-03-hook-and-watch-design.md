# Design: Hook Methods & Watch Changes

## Context
The user wants a way to monitor specific Java methods and fields in real-time. This involves a two-step workflow: selecting targets while inspecting a class structure, and then viewing a live stream of calls and changes in a dedicated monitoring mode.

## Architecture

### 1. Data Model (`AppState.kt`)
- `HookTarget`: A data class or sealed class containing `className`, `memberSignature` (method or field), and `type` (METHOD vs FIELD).
- `AppState.activeHooks`: A `Set<HookTarget>` to track what the user has pinned.
- `HookEvent`: A data class for events received from the bridge:
    - `timestamp: Long`
    - `target: HookTarget`
    - `data: Map<String, String>` (e.g., args, return value, or new field value)
    - `count: Int` (for throttled events)
- `AppState.hookEvents`: A `List<HookEvent>` acting as the live log buffer.

### 2. The Bridge Integration (`bridge.py` & `agent.js`)
- **Orchestration:** Kotlin will notify the bridge when a hook is added/removed via `RpcClient`.
- **Method Hooking:** Use Frida's `Interceptor.attach` or `Java.use(className).methodName.implementation = ...` to intercept calls, capture arguments, and the return value.
- **Field Watching:** 
    - Attempt to hook setters if they exist.
    - Fallback: A background task in `agent.js` that snapshots the value of monitored fields every ~500ms and pushes an event if the value changes.
- **Buffering:** The bridge will maintain an internal queue of events.
- **Polling:** The Kotlin TUI will poll `RpcClient.getHookEvents()` every 250ms to pull new events into the `AppState`.

### 3. Throttling Logic
- In `AppState`, when adding a new event:
    - If the last event in the log is for the same `HookTarget` and arrived within a short window, increment the `count` of the existing entry instead of adding a new one.
    - This prevents the TUI from being overwhelmed by high-frequency calls.

## User Interface

### Phase 1: Selection (Inspect Mode)
- **Visuals:** 
    - Yellow `[H]` prefix next to methods/fields that are hooked.
    - Header line: `Hooks: [ N methods, M fields ]` below the class title.
- **Interaction:**
    - Press `H` on a selected row to toggle its presence in `AppState.activeHooks`.
    - Automatically trigger the RPC call to the bridge to start/stop the Frida hook.

### Phase 2: Monitoring (Hook & Watch Mode)
- **Layout:** Split screen (Option A).
    - **Left (40%):** "Monitored Items" list. Flat list of all `HookTarget`s.
    - **Right (60%):** "Live Event Log". Scrolling list of the most recent ~100 events.
- **Visuals:** 
    - Methods highlighted in Yellow.
    - Fields highlighted in Blue.
- **Interaction:**
    - `[↑/↓]`: Navigate the Monitored Items list.
    - `[Space]`: Disable/Enable a specific hook (stop receiving events but keep it pinned).
    - `[C]`: Clear the Live Event Log.
    - `[Esc]`: Return to previous menu.

## Success Criteria
- User can toggle hooks on/off while inspecting any class.
- Switching to "Hook & Watch" mode shows a live stream of data from the Android app.
- Frequent calls are grouped/throttled to keep the TUI readable and responsive.
- Field changes are captured even if changed directly (without a setter) via polling fallback.

## Ambiguity & Scope Decisions
- **Scope:** Initial implementation will focus on `static` methods and fields as requested, with support for instance members planned for a follow-up if needed.
- **Persistence:** Pinned hooks will persist for the duration of the `barbatos` session but won't be saved to disk yet (can be added later via `CacheManager`).
