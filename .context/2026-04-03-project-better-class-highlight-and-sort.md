# Class Filter UI and Debug Entrypoint Improvements

This plan outlines the changes to improve the `barbatos` TUI experience by adding a new debug entrypoint, enhancing the sorting of classes by package similarity, improving the coloring of class names, and adding static attribute/method text highlighting.

## User Review Required

> [!IMPORTANT]
> - Since we need to know the application's package name for prioritizing classes, I plan to add a new `getpackagename` RPC method in `bridge/agent.js` and call it from Kotlin when we first enter the `DEBUG_CLASS_FILTER` mode.
> - The new `DEBUG_ENTRYPOINT` menu will intercept the `debug` command immediately after gadget installation success. Option 1 will trigger the previous behavior (start tmux session). Option 2 will do nothing for now, as requested.

## Proposed Changes

---

### Bridge and RPC Layer

#### [MODIFY] [agent.js](./barbatos/bridge/agent.js)
- Add a new RPC endpoint `getpackagename` that executes `Java.use('android.app.ActivityThread').currentApplication().getPackageName()` to retrieve the dynamic package name.

#### [MODIFY] [RpcClient.kt](./barbatos/src/nativeMain/kotlin/RpcClient.kt)
- Add a new suspending method `getPackageName(): Pair<String?, String?>` returning the parsed application package name from the Bridge.

---

### State and Main Loop

#### [MODIFY] [AppState.kt](./barbatos/src/nativeMain/kotlin/AppState.kt)
- Add `DEBUG_ENTRYPOINT` to `AppMode` enum.
- Add `var debugEntrypointIndex: Int = 0` to track the selected menu item in the entrypoint.
- Add `var appPackageName: String = ""` and the corresponding `AtomicReference` to `AppState` to cache the app's package name for sorting priority computations.

#### [MODIFY] [Main.kt](./barbatos/src/nativeMain/kotlin/Main.kt)
- In the `gadgetUpdate.first == GadgetInstallStatus.SUCCESS` block, instead of triggering `CommandExecutor.proceedWithTmux(state)`, transition the state to `AppMode.DEBUG_ENTRYPOINT` and reset the `debugEntrypointIndex`.
- Handle `DEBUG_ENTRYPOINT` in the ArrowUp and ArrowDown event listeners.
- On `Enter`, if state is `DEBUG_ENTRYPOINT` and index is `0`, run `CommandExecutor.proceedWithTmux(state)` and restore the mode to `DEFAULT`. If index is `1`, do nothing.
- When initializing `DEBUG_CLASS_FILTER`, concurrently launch the `getPackageName()` RPC call the first time if the cached value is missing.

#### [MODIFY] [CommandExecutor.kt](./barbatos/src/nativeMain/kotlin/CommandExecutor.kt)
- Create the sorting heuristic helper so that when classes arrive from the RPC call (`sharedFetchedClasses`), they are sorted into 3 buckets:
  1. Full package prefix match.
  2. Partial package prefix match (e.g. first two package segments).
  3. No significant match / Alphabetically fallback.

---

### Rendering Enhancements

#### [MODIFY] [Renderer.kt](./barbatos/src/nativeMain/kotlin/Renderer.kt)
- Create `renderDebugEntrypoint` function to render the "Search & inspect classes instances" and "Hook methods & watch changes" menu in the white/selected standard style.
- Adjust `renderClassList` so the last segment (class name itself) is visually parsed and colored `WHITE`, while the package segments remain `DIM_GRAY`. Matches with the text filter will combine with red coloring in exactly the required positions.
- In `renderInspectClassList`, introduce Java signature parsing. Colors applied will mimic Android Studio/VSCode:
  - Modifiers (`public`, `private`, `static`, etc) in distinct highlighting.
  - Basic types/return types highlighted differently.
  - Class structure (like `android.app.ActivityManager`) maintaining package vs class separation highlighting.

## Open Questions

- Does the implementation plan accurately identify the colors you want for Java syntax in inspect mode, or would you prefer a specific predefined mapping (e.g., Modifiers: Orange, Types: Cyan, Methods: Yellow, Package: Gray)? For now, I'll use standard VS Code-like colors based on 256-color ANSI codes.
- Do you want `getpackagename` to fail gracefully and just sort alphabetically if we can't extract the process name, such as in system processes that lack a `currentApplication()` context?

## Verification Plan

### Automated Tests
- This project lacks extensive unit testing, we will rely on manual testing in the debugger.

### Manual Verification
- Start the app and execute the `debug` command. Verify we halt at the entrypoint menu instead of jumping straight into Tmux.
- Enter the class filter mode. Validate the sort behavior manually by observing class hierarchy orders relative to our target app.
- Check that the base colors class lists apply white only to the class name component.
- Inspect a class to view signature syntax highlighting on rows.
