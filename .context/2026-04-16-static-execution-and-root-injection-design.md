# Design Spec — Arbitrary Method Execution & Root Injection Fallback

## 1. Overview
This spec outlines two major enhancements to Barbatos:
1. **Feature 1: Manual Arbitrary Execution ('X')** — Ability to execute arbitrary code within the context of an active hook directly from the Hook Watch view.
2. **Feature 2: Smart Injection Fallback** — Automatic detection of Root devices to allow debugging non-debuggable (release) apps using a managed `frida-server`.

---

## 2. Feature 1: Manual Arbitrary Execution ('X')

### 2.1 User Workflow
1. Navigate to `DEBUG_HOOK_WATCH` (Hook Watch view).
2. Select an active (checked) hook.
3. Press **'X'** to trigger execution.
4. If the hook is active, the system opens `$EDITOR` (defaulting to `vi`) with a TypeScript template.
5. User writes the execution logic (e.g., executing the method with custom parameters using `context.Java`).
6. Upon saving and exiting, Barbatos:
    - Sends the code to the Bridge.
    - Discards the local code (it is ephemeral and not permanently saved to the hook's implementation).
    - Remains in the `DEBUG_HOOK_WATCH` view with no visual layout changes.
    - The Bridge executes the code once and logs the result/errors with a `[EXEC]` tag, which then appears in the standard event stream.

### 2.2 Technical Implementation

#### Bridge (agent.js)
- New RPC export: `runonce(className, methodSig, code)`.
- It will use `new Function('context', transpiledCode)` to execute the logic.
- The `context` will provide `Java`, `args` (empty for static call usually, but available), `log(msg)`.
- Execution results or errors will be pushed to `hookEvents` with `type: "EXEC"`.

#### Kotlin Native (CommandExecutor & Main/Renderer)
- `Main.kt`: Handle `'x'` key in `DEBUG_HOOK_WATCH`. Ensure the target hook is active (has a check).
- `CommandExecutor.kt`:
    - `executeWatchedHook` function logic to create a temporary `.ts` file with a template.
    - Logic to spawn the editor.
    - Logic to call `RpcClient.runOnce`.
    - No changes to permanent `HookStore` implementations for this hook.
- `Renderer.kt`:
    - Render events with `type == "EXEC"`.
    - Display `[EXEC]` badge on these events in the Watch view.

---

## 3. Feature 2: Smart Injection (Root Fallback)

### 3.1 Logic & Detection
When the user triggers the `debug` command:
1. **Env Check:** Bridge checks if the device is Root (`which su`) and if the target app is `debuggable=true`.
2. **Path Selection:**
    - **App Debuggable:** Follows existing JDWP + Gadget injection.
    - **App Release + Root:** Follows new **Root Path**.
    - **App Release + No Root:** Errors out (current behavior).

### 3.2 The Root Path
The "checklist" UI will dynamically update to reflect these steps:
1. **Identify Target:** Get package/PID via ADB.
2. **Prepare Server:**
    - Check if `frida-server` is in `~/.cache/barbatos/`.
    - If missing/wrong version, download the correct architecture (arm64/arm/x86).
    - Push to `/data/local/tmp/barbatos-server`.
    - `chmod 755` and start it as root via `su -c`.
3. **Connect:** Use `frida.get_usb_device().attach(package)`.

### 3.3 Technical Implementation

#### Bridge (bridge.py)
- New methods to handle `frida-server` download and deployment.
- Update `inject_gadget_from_scratch` to branch based on environment detection.
- Add architecture detection logic for the server binary.

#### Kotlin Native (AppState & RpcClient)
- `AppState.kt`: Track if the current session is using "Root Mode" or "Gadget Mode".
- `RpcClient.kt`: Update to handle any new status/steps returned by the Bridge during the "Root Path" injection.

---

## 4. Testing Plan
1. **Feature 1:** From Hook Watch, select an active hook, press 'X', and execute `context.log("Hello from Exec"); return context.original()`. Verify the EXEC and LOG badges appear.
2. **Feature 2:**
    - Test on a debuggable app: Ensure Gadget path still works.
    - Test on a non-debuggable app (e.g., Play Store) on a Rooted device: Ensure Barbatos deploys `frida-server` and attaches successfully.
    - Test on a non-debuggable app on a non-root device: Ensure clear error message.
