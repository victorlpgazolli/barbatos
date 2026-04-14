# Progressive Reveal Scramble Effect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a smooth 300ms "scramble wave" transition for class names in the search list.

**Architecture:** Use a `Map<Int, ClassAnimationState>` in `AppState` to track per-row animation progress. The main loop will adjust its tick rate to 30ms during active animations to ensure fluid 30fps rendering.

**Tech Stack:** Kotlin Native, POSIX termios, ANSI Escape Codes.

---

### Task 1: Animation State Infrastructure

**Files:**
- Modify: `src/commonMain/kotlin/AppState.kt`
- Modify: `src/unixMain/kotlin/Main.kt`

- [ ] **Step 1: Define `ClassAnimationState` in `AppState.kt`**

```kotlin
data class ClassAnimationState(
    val oldText: String,
    val newText: String,
    val startTime: Long
) {
    fun getProgress(now: Long): Double = 
        ((now - startTime).toDouble() / 300.0).coerceIn(0.0, 1.0)
    
    fun isFinished(now: Long): Boolean = (now - startTime) >= 300
}
```

- [ ] **Step 2: Add `classAnimations` map to `AppState`**

```kotlin
// Inside AppState class:
var classAnimations: MutableMap<Int, ClassAnimationState> = mutableMapOf()
```

- [ ] **Step 3: Update `Main.kt` to handle dynamic tick rate**

Modify the `main` loop to check for active animations:

```kotlin
// Inside main loop:
val hasActiveAnimations = state.classAnimations.isNotEmpty()
val timeout = if (hasActiveAnimations) 30 else 100
val keyEvent = inputHandler.readKey(timeout)
```

- [ ] **Step 4: Commit state changes**

```bash
git add src/commonMain/kotlin/AppState.kt src/unixMain/kotlin/Main.kt
git commit -m "chore: add animation state infrastructure and dynamic tick rate"
```

---

### Task 2: Scramble Wave Logic (TDD)

**Files:**
- Modify: `src/commonMain/kotlin/StringUtils.kt`
- Create: `src/commonTest/kotlin/StringUtilsTest.kt` (if not exists)

- [ ] **Step 1: Write failing test for `getScrambledText`**

```kotlin
@Test
fun testScrambleWaveMiddle() {
    val old = "UiState"
    val new = "LoginState"
    val progress = 0.5
    val result = StringUtils.getScrambledText(old, new, progress)
    // At 0.5 progress, it should be a mix of new text, scramble, and old text
    assertTrue(result.length >= maxOf(old.length, new.length))
}
```

- [ ] **Step 2: Implement `getScrambledText` in `StringUtils.kt`**

```kotlin
fun getScrambledText(old: String, new: String, progress: Double): String {
    val maxLen = maxOf(old.length, new.length)
    val pivot = (progress * (maxLen + 3)).toInt() - 1
    val chars = "!@#$%^&*()1234567890ABCDEF"
    
    return buildString {
        for (i in 0 until maxLen) {
            when {
                i < pivot - 1 -> append(if (i < new.length) new[i] else ' ')
                i > pivot + 1 -> append(if (i < old.length) old[i] else ' ')
                else -> append(chars.random())
            }
        }
    }
}
```

- [ ] **Step 3: Run tests to verify logic**

Run: `./gradlew cleanTest commonTest`
Expected: PASS

- [ ] **Step 4: Commit logic changes**

```bash
git add src/commonMain/kotlin/StringUtils.kt src/commonTest/kotlin/StringUtilsTest.kt
git commit -m "feat: implement scramble wave string interpolation"
```

---

### Task 3: Triggering Animations

**Files:**
- Modify: `src/unixMain/kotlin/Main.kt:onInputChanged` (or equivalent list update logic)

- [ ] **Step 1: Detect list changes and trigger animations**

In the logic that updates `state.displayedClasses`, compare with previous state:

```kotlin
fun triggerClassAnimations(state: AppState, oldList: List<String>, newList: List<String>, now: Long) {
    newList.forEachIndexed { index, newName ->
        val oldName = oldList.getOrNull(index) ?: ""
        if (oldName != newName) {
            state.classAnimations[index] = ClassAnimationState(oldName, newName, now)
        }
    }
    // Cleanup animations for rows that are now empty
    val keysToRemove = state.classAnimations.keys.filter { it >= newList.size }
    keysToRemove.forEach { state.classAnimations.remove(it) }
}
```

- [ ] **Step 2: Integrate `triggerClassAnimations` into the update flow**

Ensure it's called whenever `displayedClasses` or `startIdx/endIdx` changes.

- [ ] **Step 3: Commit trigger logic**

```bash
git add src/unixMain/kotlin/Main.kt
git commit -m "feat: trigger class animations on list updates"
```

---

### Task 4: Renderer Integration

**Files:**
- Modify: `src/unixMain/kotlin/Renderer.kt`

- [ ] **Step 1: Update `renderClassList` to use animated text**

```kotlin
// Inside renderClassList loop (for i in startIdx until endIdx):
val visualRowIndex = i - startIdx
val animation = state.classAnimations[visualRowIndex]
val now = TimeUtils.currentTimeMillis()

val displayFullName = if (animation != null) {
    val progress = animation.getProgress(now)
    if (animation.isFinished(now)) {
        state.classAnimations.remove(visualRowIndex)
        state.displayedClasses[i]
    } else {
        StringUtils.getScrambledText(animation.oldText, animation.newText, progress)
    }
} else {
    state.displayedClasses[i]
}
```

- [ ] **Step 2: Visual verification of the "Wave"**

Build and run: `./gradlew linkDebugExecutableMacosArm64 && ./build/bin/macosArm64/debugExecutable/barbatos.kexe`
Test by typing in the class search box.

- [ ] **Step 3: Final Commit**

```bash
git add src/unixMain/kotlin/Renderer.kt
git commit -m "feat: integrate scramble wave effect into Renderer"
```
