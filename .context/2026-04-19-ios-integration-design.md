# iOS Integration Design Specification

## 1. Objective
Integrate iOS debugging support into the Barbatos Kotlin Terminal UI (TUI), automating the injection of the Frida Gadget and the connection of the `idevicedebug` process, while maintaining the existing user experience established for Android.

## 2. Context & Current State
- The Python bridge (`bridge/bridge.py` and `bridge/ios_repacker.py`) has been updated to support a "Hijack" workflow:
  1. `patchAndInstallIosApp(appPath)`: Copies the Frida Gadget into the Xcode `.app` bundle.
  2. `checkIosDeployStatus()`: Background thread monitors for the Xcode deployment (`xcodebuild` running) and the subsequent app launch on the device (`frida-ps`). Once detected, it force-quits the Xcode-launched app and relaunches it via `idevicedebug` with `DYLD_INSERT_LIBRARIES`.
- The Kotlin TUI currently assumes Android. A separate branch (`feature/device-selection`) introduces a device selection dropdown, but only populates it via `adb devices`.

## 3. UI/UX Flow (Kotlin TUI)

### 3.1 Device Selection (Hybrid Discovery)
The initial `AppMode.DEFAULT` or startup screen will display a unified dropdown of available devices.
- **Android Discovery:** Continue using `adb devices`.
- **iOS Discovery:** Execute `idevice_id -l` to get the UDID, followed by `ideviceinfo -u <udid> -k DeviceName` to retrieve the user-friendly name.
- **Display Format:** `[Android] emulator-5554` or `[iOS] iPhone do Victor (17054...)`.

### 3.2 iOS App Path Selection
If the user selects an iOS device, the TUI will prompt for the target application.
- **Automatic Discovery:** The TUI will scan `~/Library/Developer/Xcode/DerivedData/*/Build/Products/*-iphoneos/*.app`.
- **Filtering:** Only directories modified within the last 48 hours will be listed.
- **Display:** A searchable dropdown menu presenting the available `.app` directories.

### 3.3 Injection & Polling (The Waiting Room)
Once the app path is selected, the TUI enters `AppMode.IOS_REPACKAGE_SETUP` (or reuses the existing `DEBUG_ENTRYPOINT` rendering logic).
- **RPC Call:** Execute `patchAndInstallIosApp(appPath)`.
- **Polling Loop:** Every 1 second, call `checkIosDeployStatus()`.
- **Rendering:** Parse the returned JSON `steps` array (identical structure to the Android JDWP injection) and render the progress in the terminal with checkmarks `[✓]` for "completed" and spinners `[ / ]` for "running".
  - *Expected Steps from Bridge:*
    1. `Inject Frida Gadget`
    2. `Waiting for Xcode build & deploy...` (Requires user action in Xcode)
    3. `Hijack process for debugging`
    4. `Load Frida instrumentation agent`

### 3.4 Transition to Debugging
When `checkIosDeployStatus()` returns a global `"status": "completed"` (or `"success"`), the TUI will automatically transition to `AppMode.DEBUG_CLASS_FILTER`, indicating a successful connection to the Frida session.

## 4. Error Handling
- If the bridge returns `"status": "error"`, the polling stops, and the TUI displays the `error_message` provided by the JSON response, offering an option to abort or retry.
- If device discovery commands (`idevice_id` or `ideviceinfo`) fail, iOS options will not be populated, and a warning should be logged (not necessarily blocking Android usage).

## 5. Implementation Notes
- The Python bridge implementation is already complete and verified on the `feature/ios-repackaging` branch.
- This specification guides the Kotlin implementation, which will be merged with the `feature/device-selection` branch to provide the unified starting point.