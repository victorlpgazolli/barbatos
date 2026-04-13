# Integrate `installGadget` RPC into Debug Command Flow

When the user sends the `debug` command, we need to call the `installGadget` RPC endpoint **before** starting the tmux session. While the request is in progress, we show animated status messages with a terminal spinner below the `> debug` history entry.

## Proposed Changes

### AppState — Track gadget install status

#### [MODIFY] [AppState.kt](./barbatos/src/nativeMain/kotlin/AppState.kt)

- Add an enum `GadgetInstallStatus` with values: `IDLE`, `VALIDATING`, `RUNNING_CHECKS`, `SUCCESS`, `ERROR`
- Add new fields to `AppState`:
  - `gadgetInstallStatus: GadgetInstallStatus = IDLE` — current phase status
  - `gadgetErrorMessage: String? = null` — error message from RPC if any
  - `sharedGadgetResult: AtomicReference<Pair<GadgetInstallStatus, String?>?>` — for cross-thread communication from coroutine
  - `gadgetSpinnerFrame: Int = 0` — current frame index of the spinner animation

---

### RpcClient — Add `installGadget()` method

#### [MODIFY] [RpcClient.kt](./barbatos/src/nativeMain/kotlin/RpcClient.kt)

- Add `@Serializable data class JsonRpcRequestSimple(...)` for parameter-less RPC calls
- Add `@Serializable data class InstallGadgetResult(val status: String)` to parse the `result` field
- Add `@Serializable data class InstallGadgetErrorResult(val status: String, val error_message: String)` to parse the `error` field
- Add a new response type `@Serializable data class JsonRpcInstallGadgetResponse(...)` that can hold either result or error
- Add `suspend fun installGadget(): Pair<String, String?>` method that:
  - POSTs `{"jsonrpc":"2.0","id":1,"method":"installGadget"}` to `/rpc`
  - Returns `Pair(status, errorMessage?)` — e.g. `Pair("completed", null)` or `Pair("unknown_error", "device not found")`
  - On HTTP/network errors, returns a pair with error info

> [!NOTE]
> The bridge returns errors in the JSON body with HTTP 500, so we need to handle both HTTP 200 (`result`) and HTTP 500 (`error`) response bodies. The `error` object has `status` and `error_message` fields (not the standard JSON-RPC `code`/`message` format).

---

### Renderer — Show status + spinner below `> debug`

#### [MODIFY] [Renderer.kt](./barbatos/src/nativeMain/kotlin/Renderer.kt)

- Add a `LIGHT_GRAY` ANSI constant (`\u001b[38;5;250m`) for status text
- Add spinner frames constant: `private val SPINNER_FRAMES = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")` (Braille-dot spinner like Docker/Gemini CLI)
- Modify `renderHistory()` to check `state.gadgetInstallStatus` after rendering each history command:
  - If the last rendered command is `"debug"` and status is not `IDLE` and not `SUCCESS`, render the status line below it
  - `VALIDATING` → show spinner frame + "Validating debug status" in light gray
  - `RUNNING_CHECKS` → show spinner frame + "Running checks to know if debugger is up and running" in light gray
  - `ERROR` → show "Caught an error: {message}" in red (no spinner)
  - `SUCCESS` → render nothing (status line removed)

---

### CommandExecutor — Trigger async installGadget before tmux

#### [MODIFY] [CommandExecutor.kt](./barbatos/src/nativeMain/kotlin/CommandExecutor.kt)

- Import `CoroutineScope`, `Dispatchers`, `launch`
- Modify `handleDebug()`:
  1. Set `state.gadgetInstallStatus = VALIDATING` and return immediately (no longer blocking for tmux)
  2. Launch a coroutine that:
     - Calls `RpcClient.ping()` first; if it fails, set error
     - Updates status to `RUNNING_CHECKS` via `sharedGadgetResult`
     - Calls `RpcClient.installGadget()`
     - On success (`completed` or `gadget_detected`): set `SUCCESS` via `sharedGadgetResult`
     - On error: set `ERROR` + error message via `sharedGadgetResult`
- Accept a `CoroutineScope` parameter in `execute()` and `handleDebug()` so coroutines can be launched

---

### Main.kt — Poll gadget status + animate spinner + proceed to tmux

#### [MODIFY] [Main.kt](./barbatos/src/nativeMain/kotlin/Main.kt)

- Pass `scope` to `CommandExecutor.execute()`
- In the `KeyEvent.Timeout` block, add a section to poll `state.sharedGadgetResult`:
  - When a new value is available, update `state.gadgetInstallStatus` and `state.gadgetErrorMessage`
  - If status == `SUCCESS`:
    - Reset gadget state to `IDLE`
    - Proceed with the tmux session creation flow (call `TmuxManager.createSession()`, `SessionStore.addSession()`, `TmuxManager.attachSession()`)
  - Increment `state.gadgetSpinnerFrame` on every timeout tick if status is `VALIDATING` or `RUNNING_CHECKS` (to animate the spinner)
  - Set `needsRender = true` when spinner is active

## Open Questions

> [!IMPORTANT]
> The `installGadget` can take a while (JDWP handshake, adb forward, etc.). The current RPC timeout is 5s. Should we increase it for this specific call? The bridge already has `socket.settimeout(120)` on the JDWP side. I suggest using a 60s timeout for `installGadget`.

## Verification Plan

### Automated Tests
- Build the project: `./gradlew build` (or equivalent) to ensure compilation succeeds

### Manual Verification
- Run the bridge server (`python3 ./bridge/bridge.py`)
- Run the app and type `debug` → observe the spinner + status transitions
- Test without a device → should show error in red
- Test with gadget already installed → should show "Running checks..." briefly then proceed to tmux
