# Navigation Backstack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a global navigation stack for `AppMode` to ensure consistent `ESC` behavior across all screens.

**Architecture:** Add `navigationStack` to `AppState`. Create `pushMode(newMode)` and `popMode()` helpers in `AppState` to manage transitions. Update all navigation points to use these helpers and refactor `KeyEvent.Esc` in `Main.kt` to use the stack for backward navigation.

**Tech Stack:** Kotlin Native.

---

### Task 1: Update AppState.kt

**Files:**
- Modify: `src/nativeMain/kotlin/AppState.kt`

- [ ] **Step 1: Add `navigationStack` and helpers to `AppState`**

Add these to the `AppState` class:
```kotlin
    var navigationStack: MutableList<AppMode> = mutableListOf(),
    
    // ...
) {
    fun pushMode(newMode: AppMode) {
        if (mode != newMode) {
            navigationStack.add(mode)
            mode = newMode
        }
    }

    fun popMode(): AppMode? {
        if (navigationStack.isNotEmpty()) {
            val prev = navigationStack.removeAt(navigationStack.size - 1)
            mode = prev
            return mode
        }
        return null
    }
```

- [ ] **Step 2: Commit**

```bash
git add src/nativeMain/kotlin/AppState.kt
git commit -m "state: add navigationStack and push/pop helpers to AppState"
```

---

### Task 2: Update Transitions in CommandExecutor.kt

**Files:**
- Modify: `src/nativeMain/kotlin/CommandExecutor.kt`

- [ ] **Step 1: Update `initDebugClassFilter` and `handleDebugEntrypoint`**

Replace direct `state.mode = ...` with `state.pushMode(...)`.

```kotlin
// In initDebugClassFilter:
state.pushMode(AppMode.DEBUG_CLASS_FILTER)

// In handleDebugEntrypoint:
state.pushMode(AppMode.DEBUG_HOOK_WATCH)
```

- [ ] **Step 2: Commit**

```bash
git add src/nativeMain/kotlin/CommandExecutor.kt
git commit -m "refactor: use pushMode in CommandExecutor"
```

---

### Task 3: Update Forward Transitions in Main.kt

**Files:**
- Modify: `src/nativeMain/kotlin/Main.kt`

- [ ] **Step 1: Update 'W' (Watch) transition in Inspect mode**

Find: `state.mode = AppMode.DEBUG_HOOK_WATCH` (around line 247).
Replace with: `state.pushMode(AppMode.DEBUG_HOOK_WATCH)`.

- [ ] **Step 2: Update 'I' (Inspect) transition for subclasses**

Find: `state.inspectBackStack.add(state.inspectTargetClassName)` (around line 289).
Update to also push the mode (even if it's the same, it helps the ESC logic):
```kotlin
state.inspectBackStack.add(state.inspectTargetClassName)
state.pushMode(AppMode.DEBUG_INSPECT_CLASS) // Push current mode to stack for return
state.inspectTargetClassName = targetClassName
// ... reset state ...
```

- [ ] **Step 3: Update transitions in other modes if found**

Search for any other `state.mode = ` assignments and convert them to `pushMode` if they represent a screen transition (e.g. from Class Filter to Inspect).

- [ ] **Step 4: Commit**

```bash
git add src/nativeMain/kotlin/Main.kt
git commit -m "refactor: use pushMode for all forward transitions in Main.kt"
```

---

### Task 4: Refactor ESC Handling in Main.kt

**Files:**
- Modify: `src/nativeMain/kotlin/Main.kt`

- [ ] **Step 1: Rewrite `KeyEvent.Esc` handler**

Replace the current `KeyEvent.Esc` logic with a stack-aware implementation.

```kotlin
            is KeyEvent.Esc -> {
                if (state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) {
                    state.inputBuffer = ""
                    state.cursorPosition = 0
                    state.popMode()
                    Renderer.render(state)
                } else if (state.mode == AppMode.DEBUG_INSPECT_CLASS && state.inspectBackStack.isNotEmpty()) {
                    // Internal backstack for classes
                    val prevClass = state.inspectBackStack.removeAt(state.inspectBackStack.size - 1)
                    state.inspectTargetClassName = prevClass
                    state.popMode() // Pop the mode we pushed during 'I'
                    
                    state.isFetchingInspection = true
                    state.inspectStaticAttributes = emptyList()
                    state.inspectInstanceAttributes = emptyList()
                    state.inspectMethods = emptyList()
                    state.inspectExpandedInstances.clear()
                    state.inspectExpandedInstancesError.clear()
                    state.inspectInstancesList = null
                    state.inspectInstancesTotalCount = 0
                    state.inspectInstancesExpanded = false
                    state.selectedClassIndex = 0
                    state.rpcError = null
                    Renderer.render(state)
                    scope.launch {
                        val (result, error) = RpcClient.inspectClass(prevClass)
                        state.sharedInspectResult.value = result
                        state.sharedRpcError.value = error
                        state.isFetchingInspection = false
                    }
                } else {
                    val prevMode = state.popMode()
                    if (prevMode == null) {
                        if (state.startedAsInspectPane) {
                            state.running = false
                        } else {
                            // Fallback if stack empty
                            state.mode = AppMode.DEBUG_ENTRYPOINT
                        }
                    }
                    Renderer.render(state)
                }
            }
```

- [ ] **Step 2: Commit**

```bash
git add src/nativeMain/kotlin/Main.kt
git commit -m "feat: implement stack-based back navigation for ESC key"
```

---

### Task 5: Final Validation

- [ ] **Step 1: Build the project**

Run: `./gradlew linkDebugExecutableMacosArm64`

- [ ] **Step 2: Manual Verification**
1. Classes -> ESC -> Entrypoint (OK)
2. Classes -> Inspect -> ESC -> Classes (OK)
3. Inspect -> Watch -> ESC -> Inspect (NEW/FIXED)
4. Inspect -> Subclass (I) -> ESC -> Parent Class (OK)
5. Inspect -> Subclass (I) -> Watch (W) -> ESC -> Subclass (NEW/FIXED)

- [ ] **Step 3: Commit final verification**
