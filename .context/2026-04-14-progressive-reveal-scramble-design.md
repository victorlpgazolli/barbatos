# Design Spec: Progressive Reveal Scramble Effect

## Overview
Implement a "Progressive Reveal" transition effect for class names in the `DEBUG_CLASS_FILTER` and potentially other list views. The effect features a 3-character "scramble window" that moves from left to right, transitioning old text to new text over a 300ms duration.

## Requirements
- **Duration:** 300ms for a full transition.
- **Window Size:** 3 characters of "noise" (random letters/numbers/symbols).
- **Scope:** Applies to the full display string (ClassName + Package).
- **Smoothness:** Transition must feel fluid, requiring a higher refresh rate during animation.
- **Persistence:** If the user scrolls or the list updates rapidly, animations should be handled gracefully (either reset or completed instantly).

## Proposed Architecture

### 1. State Management (`AppState.kt`)
Add a map to track active animations per visual row index.

```kotlin
data class ClassAnimationState(
    val oldText: String,
    val newText: String,
    val startTime: Long,
    var isFinished: Boolean = false
)

// In AppState:
var classAnimations: MutableMap<Int, ClassAnimationState> = mutableMapOf()
```

### 2. Transition Logic
A utility function (likely in `StringUtils.kt` or `Renderer.kt`) will calculate the interleaved string:

```kotlin
fun getScrambledText(old: String, new: String, progress: Double): String {
    val maxLen = maxOf(old.length, new.length)
    val pivot = (progress * (maxLen + 3)).toInt() - 1 // +3 to allow window to slide out
    
    return buildString {
        for (i in 0 until maxLen) {
            when {
                i < pivot - 1 -> append(if (i < new.length) new[i] else ' ')
                i > pivot + 1 -> append(if (i < old.length) old[i] else ' ')
                else -> append(getRandomScrambleChar())
            }
        }
    }
}
```

### 3. TUI Loop Integration (`Main.kt`)
Modify the main loop to handle sub-100ms ticks when animations are active.

- **Normal state:** 100ms timeout.
- **Animating state:** 30ms timeout.

### 4. Rendering (`Renderer.kt`)
In `renderClassList`, check if `classAnimations[visualIndex]` exists:
- If yes: Calculate progress and call `getScrambledText`.
- If progress >= 1.0: Mark as finished and cleanup the state.
- If no animation: Render the `newText` directly.

## Testing Strategy
- **Visual Verification:** Ensure the "wave" travels smoothly from left to right.
- **Edge Cases:**
    - Rapid typing: Verify animations don't "stack" or cause flicker.
    - Long names vs short names: Ensure the wave covers the full length of the longer string.
    - Scrolling: Ensure animations stay pinned to the visual row or cancel correctly.

## Success Criteria
- Smooth 300ms transition.
- Clear 3-character scramble window.
- No performance degradation in the TUI loop.
