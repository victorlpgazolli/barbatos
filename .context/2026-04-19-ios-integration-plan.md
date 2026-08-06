# iOS Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement hybrid device discovery (Android+iOS), automatic Xcode `.app` path discovery, and the background injection/hijack polling UI in Kotlin Native.

**Architecture:** Extend the existing Kotlin state machine (`AppState.kt`) and renderer (`Renderer.kt`) to support iOS selection and deployment statuses, wrapping Python RPC calls to manage the Frida injection lifecycle.

**Tech Stack:** Kotlin Native, POSIX termios, Ktor HTTP Client.

---

### Task 1: Resolve Git Merge Conflicts

**Files:**
- Modify: `.worktrees/feature-ios-integration/src/commonMain/kotlin/AppState.kt`
- Modify: `.worktrees/feature-ios-integration/src/unixMain/kotlin/Main.kt`

- [ ] **Step 1: Fix AppState.kt Enums**

Remove the `<<<<<<<` markers and ensure both modes exist in `AppMode`:
```kotlin
enum class AppMode {
    DEFAULT,
    DEBUG_ENTRYPOINT,
    DEBUG_CLASS_FILTER,
    DEBUG_INSPECT_CLASS,
    DEBUG_HOOK_WATCH,
    DEBUG_EDIT_ATTRIBUTE,
    DEBUG_DEVICE_SELECTION,
    IOS_REPACKAGE_SETUP
}
```

- [ ] **Step 2: Fix Main.kt Routing**

Ensure the `when (state.mode)` block in `Main.kt` handles both `AppMode.DEBUG_DEVICE_SELECTION` and `AppMode.IOS_REPACKAGE_SETUP` without conflict markers.

- [ ] **Step 3: Commit Conflict Resolution**

```bash
cd .worktrees/feature-ios-integration
git add src/commonMain/kotlin/AppState.kt src/unixMain/kotlin/Main.kt
git commit -m "chore: resolve merge conflicts for iOS integration"
```

### Task 2: Implement Hybrid Device Discovery

**Files:**
- Modify: `.worktrees/feature-ios-integration/src/commonMain/kotlin/AppState.kt`
- Modify: `.worktrees/feature-ios-integration/src/unixMain/kotlin/Main.kt`

- [ ] **Step 1: Add iOS Device Discovery Command**

In the routine that fetches devices (likely inside `DEBUG_DEVICE_SELECTION` handler), add a background coroutine execution for `idevice_id -l` and `ideviceinfo`:

```kotlin
// Pseudocode for the CoroutineScope in Main.kt
val iosResult = CommandExecutor.executeCommand("idevice_id -l")
if (iosResult.exitCode == 0 && iosResult.output.isNotBlank()) {
    val udid = iosResult.output.trim().lines().first()
    val nameResult = CommandExecutor.executeCommand("ideviceinfo -u $udid -k DeviceName")
    val name = if (nameResult.exitCode == 0) nameResult.output.trim() else "iOS Device"
    val iosDevice = DeviceInfo(serial = udid, model = name, status = "iOS")
    // Merge this into state.deviceInfoList
}
```

- [ ] **Step 2: Commit Device Discovery**

```bash
git commit -am "feat(tui): implement iOS device discovery via idevice_id"
```

### Task 3: Automatic .app Path Discovery

**Files:**
- Modify: `.worktrees/feature-ios-integration/src/commonMain/kotlin/AppState.kt`
- Modify: `.worktrees/feature-ios-integration/src/unixMain/kotlin/InputHandler.kt`
- Modify: `.worktrees/feature-ios-integration/src/unixMain/kotlin/Renderer.kt`

- [ ] **Step 1: Add App Path State**

Add state variables to `AppState.kt` for the iOS app list:
```kotlin
var iosAppPaths: List<String> = emptyList()
var selectedIosAppIndex: Int = 0
```

- [ ] **Step 2: Implement Discovery Logic**

When the user selects an iOS device in `DEBUG_DEVICE_SELECTION`, transition to a new mode (e.g., `IOS_APP_SELECTION`) and run a shell command to find recent apps:
```kotlin
// In Main.kt or CommandExecutor:
val derivedDataDir = "~/Library/Developer/Xcode/DerivedData"
val cmd = "find $derivedDataDir -type d -name '*.app' -mtime -2 2>/dev/null"
```

- [ ] **Step 3: Render App List**

In `Renderer.kt`, create a view to display `iosAppPaths` using `ListRenderer`, similar to how `iosCertList` was handled.

- [ ] **Step 4: Commit Path Discovery**

```bash
git commit -am "feat(tui): implement automatic 48h discovery of Xcode .app paths"
```

### Task 4: Injection Polling and UI Rendering

**Files:**
- Modify: `.worktrees/feature-ios-integration/src/commonMain/kotlin/RpcClient.kt`
- Modify: `.worktrees/feature-ios-integration/src/unixMain/kotlin/Main.kt`
- Modify: `.worktrees/feature-ios-integration/src/unixMain/kotlin/Renderer.kt`

- [ ] **Step 1: Add checkIosDeployStatus RPC Call**

```kotlin
suspend fun checkIosDeployStatus(): Pair<GadgetInstallStatus, List<InjectionStep>?> {
    // Map the JSON response from bridge to the GadgetInstallStatus enum
    // and parse the 'steps' array.
}
```

- [ ] **Step 2: Implement Polling Loop**

In `Main.kt`, when `IOS_REPACKAGE_SETUP` is active, poll `checkIosDeployStatus` every 1000ms using coroutines and update `state.gadgetInjectionSteps` and `state.gadgetInstallStatus`.

- [ ] **Step 3: Render Steps**

Ensure `Renderer.kt` handles `AppMode.IOS_REPACKAGE_SETUP` by displaying `state.gadgetInjectionSteps` exactly like it does for Android.

- [ ] **Step 4: Handle Success Transition**

When `status` becomes `SUCCESS`, transition `state.mode` to `DEBUG_CLASS_FILTER`.

- [ ] **Step 5: Commit Polling & UI**

```bash
git commit -am "feat(tui): implement iOS deploy status polling and unified step rendering"
```
