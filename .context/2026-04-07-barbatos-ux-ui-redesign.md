# Barbatos UX/UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign all three debug screens (Classes, Inspect, Watch) with a consistent htop-style layout: fixed header + breadcrumb + scrollable content + htop-style footer, and a global color language (orange = field, purple = method, blue = object ref, green = active).

**Architecture:** All changes live in `Renderer.kt`. New shared primitives (`renderHeader`, `renderBreadcrumb`) replace the logo/welcome block in debug modes. Each screen's render function is rewritten in place — no new files, no changes to `AppState.kt`, `RpcClient.kt`, or `bridge/bridge.py`.

**Tech Stack:** Kotlin Native, ANSI escape codes, Gradle (`./gradlew linkDebugExecutableMacosArm64`)

---

## File Map

| File | Change |
|------|--------|
| `src/nativeMain/kotlin/Renderer.kt` | Major — all 4 tasks |
| `src/nativeMain/kotlin/ListRenderer.kt` | Minor — update selection prefix color |

---

## Task 1: Theme constants + shared layout primitives

**Files:**
- Modify: `src/nativeMain/kotlin/Renderer.kt` (top of `Renderer` object, `render()` function, `renderFooter`)
- Modify: `src/nativeMain/kotlin/ListRenderer.kt` (`selectionPrefix`)

### Context

`Renderer.kt` opens with an `Ansi` object (shared escape codes) followed by the `Renderer` object which owns all private color constants and render functions. We add new color constants inside `Renderer`, then add `renderHeader` and `renderBreadcrumb`, then rewrite `renderFooter` and update the `render()` dispatcher.

- [ ] **Step 1: Add new color constants inside `Renderer` object**

Replace the existing private color constants block (lines ~21–43 in `Renderer.kt`) with the following expanded set. Keep the old constants for `K_PURPLE`/`K_VIOLET`/`K_MAGENTA`/`K_PINK` (still used by the logo in DEFAULT mode).

```kotlin
// Design system — color language
private const val C_ORANGE   = "\u001b[38;5;208m"  // field / attribute
private const val C_PURPLE   = "\u001b[38;5;135m"  // method
private const val C_BLUE     = "\u001b[38;5;75m"   // object reference / Barbatos brand
private const val C_GREEN    = "\u001b[38;5;71m"   // active instance / live count
private const val C_DARK_GRAY = "\u001b[38;5;238m" // destroyed instances
private const val C_MID_GRAY  = "\u001b[38;5;244m" // secondary text
private const val C_SEP      = "\u001b[38;5;237m"  // separator lines

// Header background (simulated via bold separator)
private const val C_HEADER_BG = "\u001b[48;5;235m" // dark bg for header bar

// Keep existing (used elsewhere)
private const val DIM_GRAY     = "\u001b[90m"
private const val WHITE        = "\u001b[97m"
private const val LIGHT_GRAY   = "\u001b[38;5;250m"
private const val PROPERTY_NAME = "\u001b[38;5;252m"
private const val TYPE_BOOLEAN  = "\u001b[38;5;39m"
private const val TYPE_NUMBER   = "\u001b[38;5;114m"
private const val TYPE_STRING   = "\u001b[38;5;173m"
private const val TYPE_OTHER    = "\u001b[38;5;43m"
private const val RESET        = "\u001b[0m"
private const val J_MODIFIER   = "\u001b[38;5;208m"
private const val J_TYPE       = "\u001b[38;5;114m"
private const val J_PACKAGE    = "\u001b[90m"
private const val J_CLASS      = "\u001b[97m"
private const val J_METHOD     = "\u001b[38;5;220m"
private const val J_NUMBER     = "\u001b[38;5;173m"
```

- [ ] **Step 2: Add `renderHeader` function**

Add this private function inside `Renderer` object, after the existing color constants:

```kotlin
private fun renderHeader(buf: StringBuilder, state: AppState, termWidth: Int) {
    val pkg = if (state.appPackageName.isNotEmpty()) state.appPackageName else "no package"
    val statusText = "● connected"
    val leftText = " Barbatos"

    // Visible character counts (no escape codes)
    val leftLen  = leftText.length          // 4
    val midLen   = 5 + pkg.length           // " · " + pkg + " · " = 3+pkg+3 → simplified
    val rightLen = statusText.length + 1    // + trailing space

    buf.append(C_HEADER_BG)
    buf.append(C_BLUE).append(leftText).append(RESET).append(C_HEADER_BG)
    buf.append(C_MID_GRAY).append("  ·  ").append(RESET).append(C_HEADER_BG)
    buf.append(C_MID_GRAY).append(pkg).append(RESET).append(C_HEADER_BG)
    buf.append(C_MID_GRAY).append("  ·  ").append(RESET).append(C_HEADER_BG)

    // Pad to push status to the right
    val visibleSoFar = leftLen + 5 + pkg.length + 5
    val padding = maxOf(1, termWidth - visibleSoFar - rightLen)
    buf.append(" ".repeat(padding))

    buf.append(C_GREEN).append(statusText).append(RESET).append(C_HEADER_BG)
    buf.append(" ")
    buf.append(RESET).append("\n")

    // Separator
    buf.append(C_SEP).append("─".repeat(termWidth)).append(RESET).append("\n")
}
```

- [ ] **Step 3: Add `renderBreadcrumb` function**

Add this private function after `renderHeader`:

```kotlin
private fun renderBreadcrumb(buf: StringBuilder, state: AppState, termWidth: Int) {
    val currentStage = when (state.mode) {
        AppMode.DEBUG_CLASS_FILTER -> 0
        AppMode.DEBUG_INSPECT_CLASS, AppMode.DEBUG_EDIT_ATTRIBUTE -> 1
        AppMode.DEBUG_HOOK_WATCH -> 2
        else -> -1
    }

    val stages = listOf("Classes", "Inspect", "Watch")
    val sep = " › "

    buf.append(" ")
    stages.forEachIndexed { index, name ->
        if (index > 0) buf.append(C_SEP).append(sep).append(RESET)
        if (index == currentStage) {
            buf.append(WHITE).append(name).append(RESET)
        } else {
            buf.append(DIM_GRAY).append(name).append(RESET)
        }
    }
    buf.append("\n")
    buf.append(C_SEP).append("─".repeat(termWidth)).append(RESET).append("\n")
}
```

- [ ] **Step 4: Rewrite `renderFooter`**

Replace the existing `renderFooter` function entirely:

```kotlin
private fun renderFooter(buf: StringBuilder, state: AppState, termWidth: Int, termHeight: Int) {
    data class FooterKey(val key: String, val label: String)

    val keys: List<FooterKey> = when (state.mode) {
        AppMode.DEFAULT -> listOf(
            FooterKey("↑↓", "History"),
            FooterKey("Tab", "Autocomplete"),
            FooterKey("Enter", "Execute"),
            FooterKey("Ctrl+C", "Quit")
        )
        AppMode.DEBUG_ENTRYPOINT -> listOf(
            FooterKey("↑↓", "Navigate"),
            FooterKey("Enter", "Select"),
            FooterKey("Ctrl+C", "Quit")
        )
        AppMode.DEBUG_CLASS_FILTER -> listOf(
            FooterKey("↑↓", "Navigate"),
            FooterKey("Enter", "Inspect"),
            FooterKey("\\", "Count"),
            FooterKey("Esc", "Back"),
            FooterKey("Ctrl+C", "Quit")
        )
        AppMode.DEBUG_INSPECT_CLASS -> listOf(
            FooterKey("H", "Hook"),
            FooterKey("I", "Inspect child"),
            FooterKey("W", "Watch"),
            FooterKey("Esc", "Back"),
            FooterKey("Ctrl+C", "Quit")
        )
        AppMode.DEBUG_HOOK_WATCH -> listOf(
            FooterKey("D", "Remove hook"),
            FooterKey("C", "Clear log"),
            FooterKey("Esc", "Back"),
            FooterKey("Ctrl+C", "Quit")
        )
        AppMode.DEBUG_EDIT_ATTRIBUTE -> listOf(
            FooterKey("Enter", "Save"),
            FooterKey("Esc", "Cancel"),
            FooterKey("Ctrl+C", "Quit")
        )
    }

    buf.append(Ansi.moveTo(termHeight, 1))
    buf.append(C_HEADER_BG)
    buf.append(" ")
    var visibleLen = 1
    for (k in keys) {
        // Colored key label + dim description
        buf.append(WHITE).append(k.key).append(RESET).append(C_HEADER_BG)
        buf.append(C_MID_GRAY).append(" ${k.label}").append(RESET).append(C_HEADER_BG)
        buf.append("   ")
        visibleLen += k.key.length + 1 + k.label.length + 3
    }
    buf.append(" ".repeat(maxOf(0, termWidth - visibleLen)))
    buf.append(RESET)
}
```

- [ ] **Step 5: Update `render()` dispatcher for debug modes**

In the `render()` function, replace the `DEBUG_CLASS_FILTER` branch:

```kotlin
} else if (state.mode == AppMode.DEBUG_CLASS_FILTER) {
    renderHeader(buf, state, termWidth)
    renderBreadcrumb(buf, state, termWidth)
    renderClassFetchStatus(buf, state)
    renderInputBox(buf, state, termWidth - 2)
    renderClassList(buf, state, termWidth, termHeight)
    buf.append(Ansi.RESTORE_CURSOR)
```

Replace the `DEBUG_INSPECT_CLASS / DEBUG_EDIT_ATTRIBUTE` branch:

```kotlin
} else if (state.mode == AppMode.DEBUG_INSPECT_CLASS || state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) {
    renderHeader(buf, state, termWidth)
    renderBreadcrumb(buf, state, termWidth)
    renderCtrlCWarning(buf, state)
    if (state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) {
        renderInputBox(buf, state, termWidth - 2)
    }
    renderInspectClassList(buf, state, termWidth, termHeight)
    if (state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) {
        buf.append(Ansi.RESTORE_CURSOR)
    }
```

Replace the `DEBUG_HOOK_WATCH` branch:

```kotlin
} else if (state.mode == AppMode.DEBUG_HOOK_WATCH) {
    renderHeader(buf, state, termWidth)
    renderBreadcrumb(buf, state, termWidth)
    renderHookWatchMode(buf, state, termWidth, termHeight)
```

Keep the `DEFAULT` and `DEBUG_ENTRYPOINT` branches unchanged.

- [ ] **Step 6: Update `selectionPrefix` in `ListRenderer.kt`**

Change the selection color from `Ansi.GREEN` to blue to match the new design:

```kotlin
fun selectionPrefix(isSelected: Boolean, indent: String = ""): String {
    return if (isSelected) "$indent\u001b[38;5;75m› ${Ansi.RESET}" else "$indent  "
}
```

- [ ] **Step 7: Build to verify compilation**

```bash
cd ./barbatos
./gradlew linkDebugExecutableMacosArm64
```

Expected: `BUILD SUCCESSFUL`. Fix any compilation errors before proceeding.

- [ ] **Step 8: Commit**

```bash
git add src/nativeMain/kotlin/Renderer.kt src/nativeMain/kotlin/ListRenderer.kt
git commit -m "feat: add theme constants, shared header/breadcrumb/footer layout primitives"
```

---

## Task 2: Redesign Screen 1 — Classes

**Files:**
- Modify: `src/nativeMain/kotlin/Renderer.kt` (`renderClassList`, `renderInputBox`)

### Context

The Classes screen currently shows the logo + welcome text above the search box. After Task 1 those are removed from the DEBUG_CLASS_FILTER path. Now we redesign `renderClassList` to:
- Show package dimmed below name **only for the selected row**
- Show instance count green (if > 0) or dim (if 0) on the right
- Adjust `fixedLines` to match the new compact header (header=2, breadcrumb=2, inputbox=3, loading=1, footer=1 → 9 fixed lines)

- [ ] **Step 1: Rewrite `renderClassList`**

Replace the entire `renderClassList` function:

```kotlin
private fun renderClassList(buf: StringBuilder, state: AppState, termWidth: Int, termHeight: Int) {
    if (state.rpcError != null) {
        buf.append(Ansi.RED).append("  Error: ${state.rpcError}").append(RESET).append("\n")
        return
    }

    if (state.isFetchingClasses && state.displayedClasses.isEmpty()) {
        return // spinner shown by renderClassFetchStatus above
    }

    if (state.displayedClasses.isEmpty() && state.inputBuffer.isNotEmpty() && !state.isFetchingClasses) {
        buf.append(DIM_GRAY).append("  No matches found.\n").append(RESET)
        return
    }

    // Each selected row takes 2 lines (name + package); others take 1 line.
    // Conservative: reserve enough room assuming selected row is always visible.
    val fixedLines = 9
    val maxItems = maxOf(3, termHeight - fixedLines - 1)

    val (startIdx, endIdx) = ListRenderer.computeViewport(
        state.displayedClasses.size, state.selectedClassIndex, maxItems
    )

    for (i in startIdx until endIdx) {
        val className  = state.displayedClasses[i]
        val isSelected = i == state.selectedClassIndex
        val prefix     = ListRenderer.selectionPrefix(isSelected, "  ")

        val lastDot     = className.lastIndexOf('.')
        val packagePart = if (lastDot != -1) className.substring(0, lastDot) else ""
        val namePart    = if (lastDot != -1) className.substring(lastDot + 1) else className

        val query           = state.lastSearchedParam
        val nameBaseColor   = if (isSelected) WHITE else LIGHT_GRAY
        val packageBaseColor = DIM_GRAY

        fun highlight(text: String, baseColor: String): String {
            if (query.isEmpty() || !text.contains(query, ignoreCase = true)) return "$baseColor$text"
            val sb  = StringBuilder()
            var pos = 0
            val lo  = text.lowercase()
            val lq  = query.lowercase()
            while (true) {
                val start = lo.indexOf(lq, pos)
                if (start == -1) { sb.append(baseColor).append(text.substring(pos)); break }
                sb.append(baseColor).append(text.substring(pos, start))
                sb.append("\u001b[38;5;220m").append(text.substring(start, start + query.length)) // yellow highlight
                pos = start + query.length
            }
            return sb.toString()
        }

        // Count badge
        val count = state.instanceCounts[className]
        val countBadge = when {
            count == null -> ""
            count > 0     -> "  ${C_GREEN}$count inst${RESET}"
            else          -> "  ${DIM_GRAY}0 inst${RESET}"
        }

        // Name row
        buf.append(prefix)
        buf.append(highlight(namePart, nameBaseColor))
        buf.append(countBadge)
        buf.append(RESET).append("\n")

        // Package row — only for selected item
        if (isSelected && packagePart.isNotEmpty()) {
            buf.append("    ")  // align under name (4 spaces: 2 indent + 2 from prefix)
            buf.append(highlight(packagePart, packageBaseColor))
            buf.append(RESET).append("\n")
        }
    }

    ListRenderer.renderScrollIndicator(buf, startIdx, endIdx, state.displayedClasses.size, termWidth)
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew linkDebugExecutableMacosArm64
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run and visually verify**

```bash
./build/bin/macosArm64/debugExecutable/barbatos.kexe
```

Type `debug`, select "Search & inspect classes". Verify:
- Header bar with Barbatos · package · ● connected
- Breadcrumb: **Classes** › Inspect › Watch
- Search box with cursor
- Class list: name prominent, package dimmed below only when row selected
- Instance counts green/dim on the right
- Footer: white key labels + gray descriptions

- [ ] **Step 4: Commit**

```bash
git add src/nativeMain/kotlin/Renderer.kt
git commit -m "feat: redesign Classes screen with package-on-selection and htop layout"
```

---

## Task 3: Redesign Screen 2 — Inspect Class

**Files:**
- Modify: `src/nativeMain/kotlin/Renderer.kt` (`renderInspectClassList`, add `renderClassPackageSubtitle`, add `extractParams`, update `render()`)

### Context

The Inspect screen currently uses `renderInspectHeader` (a boxed header with class name and hook counts). We replace it with the shared header + breadcrumb from Task 1, add a one-line package subtitle, and redesign the row rendering:

- `SectionStaticRow`: ▾/▸ label with count summary
- `StaticAttributeRow`: orange name, `H` right-aligned
- `StaticMethodRow`: purple name + dim params, `H` right-aligned
- `InstanceRow`: `inst#N · @handle · active` or dimmed `destroyed`
- `InstanceAttributeRow`: orange field names, blue object refs with `→ I`

The `renderInspectHeader` function is no longer called but keep it in the file (it can be removed in a cleanup pass later).

- [ ] **Step 1: Add `renderClassPackageSubtitle` and `extractParams` helpers**

Add these two private functions inside `Renderer`, after `renderBreadcrumb`:

```kotlin
private fun renderClassPackageSubtitle(buf: StringBuilder, state: AppState, termWidth: Int) {
    val fullName = state.inspectTargetClassName
    val lastDot  = fullName.lastIndexOf('.')
    val pkg      = if (lastDot != -1) fullName.substring(0, lastDot) else ""
    if (pkg.isNotEmpty()) {
        buf.append(" ").append(DIM_GRAY).append(pkg).append(RESET).append("\n")
        buf.append(C_SEP).append("─".repeat(termWidth)).append(RESET).append("\n")
    }
}

private fun extractParams(signature: String): String {
    val open  = signature.indexOf('(')
    val close = signature.lastIndexOf(')')
    if (open == -1 || close <= open + 1) return ""
    return signature.substring(open + 1, close)
        .split(',')
        .joinToString(", ") { it.trim().substringAfterLast('.') }
}
```

- [ ] **Step 2: Update `render()` to call `renderClassPackageSubtitle`**

Inside the `DEBUG_INSPECT_CLASS / DEBUG_EDIT_ATTRIBUTE` branch added in Task 1, insert the subtitle call after `renderBreadcrumb`:

```kotlin
} else if (state.mode == AppMode.DEBUG_INSPECT_CLASS || state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) {
    renderHeader(buf, state, termWidth)
    renderBreadcrumb(buf, state, termWidth)
    renderClassPackageSubtitle(buf, state, termWidth)  // ← add this line
    renderCtrlCWarning(buf, state)
    if (state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) {
        renderInputBox(buf, state, termWidth - 2)
    }
    renderInspectClassList(buf, state, termWidth, termHeight)
    if (state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) {
        buf.append(Ansi.RESTORE_CURSOR)
    }
```

- [ ] **Step 3: Rewrite the row-rendering switch inside `renderInspectClassList`**

The function structure stays the same (viewport calculation, row iteration). Replace only the `when (row)` block. Also update `fixedLines` from `21/18` to `10`:

Change:
```kotlin
val fixedLines = if (state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) 21 else 18
```
To:
```kotlin
val fixedLines = if (state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) 13 else 10
```

Then replace the entire `when (row) { ... }` block with:

```kotlin
when (row) {
    is InspectRow.SectionStaticRow -> {
        val arrow  = if (row.isExpanded) "▾" else "▸"
        val detail = if (row.isExpanded) "" else "  ${DIM_GRAY}(press Enter to expand)${RESET}"
        buf.append(prefix)
            .append(C_MID_GRAY).append("$arrow Static members").append(RESET)
            .append(detail).append("\n")
    }
    is InspectRow.SectionInstancesRow -> {
        val arrow     = if (row.isExpanded) "▾" else "▸"
        val countInfo = state.inspectInstancesList?.let { " ${C_GREEN}${state.inspectInstancesTotalCount} found${RESET}" } ?: ""
        buf.append(prefix)
            .append(C_MID_GRAY).append("$arrow Instances").append(RESET)
            .append(countInfo).append("\n")
    }
    is InspectRow.StaticAttributeRow -> {
        val memberName = extractMemberName(row.attribute)
        val isHooked   = state.activeHooks.any {
            it.className == state.inspectTargetClassName && it.memberSignature == row.attribute
        }
        val nameStr    = "${C_ORANGE}$memberName${RESET}"
        val hookedStr  = if (isHooked) " ${C_ORANGE}[H]${RESET}" else " ${DIM_GRAY}H${RESET}"

        // Right-align the H hint: compute visible length
        val visibleLine = "    $memberName H"  // indent + name + " H"
        val pad = maxOf(1, termWidth - prefix.length.coerceAtMost(4) - memberName.length - 5)

        buf.append(prefix).append("  ")
            .append(nameStr)
            .append(" ".repeat(pad))
            .append(hookedStr).append("\n")
    }
    is InspectRow.StaticMethodRow -> {
        val memberName = extractMemberName(row.method)
        val params     = extractParams(row.method)
        val isHooked   = state.activeHooks.any {
            it.className == state.inspectTargetClassName && it.memberSignature == row.method
        }
        val nameStr    = "${C_PURPLE}$memberName${RESET}"
        val paramsStr  = "${DIM_GRAY}($params)${RESET}"
        val hookedStr  = if (isHooked) " ${C_PURPLE}[H]${RESET}" else " ${DIM_GRAY}H${RESET}"

        val visibleLen = memberName.length + 2 + params.length + 3
        val pad = maxOf(1, termWidth - 6 - visibleLen)

        buf.append(prefix).append("  ")
            .append(nameStr).append(paramsStr)
            .append(" ".repeat(pad))
            .append(hookedStr).append("\n")
    }
    is InspectRow.InstanceRow -> {
        // Detect destroyed state from summary heuristic
        val isDestroyed = row.instance.summary.contains("destroyed", ignoreCase = true)
            || row.instance.summary.contains("isDestroyed=true", ignoreCase = true)

        val treeLine   = if (row.isLast) "└ " else "├ "
        val idxLabel   = "inst#${row.instance.id.takeLast(4)}"  // show last 4 chars of id
        val hashLabel  = "@${row.instance.handle.take(8)}"
        val statusStr  = if (isDestroyed) "${C_DARK_GRAY}destroyed${RESET}" else "${C_GREEN}active${RESET}"

        if (isDestroyed) {
            buf.append(prefix)
                .append(C_DARK_GRAY).append(treeLine).append(idxLabel)
                .append(" · ").append(hashLabel)
                .append(" · destroyed").append(RESET).append("\n")
        } else {
            buf.append(prefix)
                .append(DIM_GRAY).append(treeLine).append(RESET)
                .append(WHITE).append(idxLabel).append(RESET)
                .append(DIM_GRAY).append(" · ").append(RESET)
                .append(DIM_GRAY).append(hashLabel).append(RESET)
                .append(DIM_GRAY).append(" · ").append(RESET)
                .append(statusStr).append("\n")
        }
    }
    is InspectRow.InstanceAttributeRow -> {
        if (row.attribute.isPagination) {
            buf.append(prefix).append(DIM_GRAY).append("··· ").append(row.attribute.value).append(RESET).append("\n")
        } else {
            val attrName = row.attribute.name
            val attrType = row.attribute.type
            val attrVal  = row.attribute.value

            // Truncate value if needed
            val maxValLen = maxOf(10, termWidth - visualIndent - attrName.length - attrType.length - 10)
            val displayVal = if (attrVal.length > maxValLen) attrVal.take(maxValLen - 3) + "..." else attrVal

            val isObjectRef = row.attribute.childId != null

            buf.append(prefix)
            if (isObjectRef) {
                // Object reference: blue name + inspect hint
                buf.append(C_ORANGE).append(attrName).append(RESET)
                buf.append(DIM_GRAY).append(": ").append(RESET)
                buf.append(C_BLUE).append(attrType).append(RESET)
                buf.append(DIM_GRAY).append("  → I").append(RESET)
            } else {
                // Primitive / string
                val valColor = when (attrType.lowercase()) {
                    "boolean", "bool"                                    -> TYPE_BOOLEAN
                    "int", "long", "float", "double", "short", "byte"   -> TYPE_NUMBER
                    "string", "char", "charsequence"                    -> TYPE_STRING
                    else                                                 -> TYPE_OTHER
                }
                val valDisplay = if (attrType.lowercase() == "string" && attrVal != "null") "\"$displayVal\"" else displayVal
                val valColorFinal = if (attrVal == "null") Ansi.RED else valColor

                buf.append(C_ORANGE).append(attrName).append(RESET)
                buf.append(DIM_GRAY).append(": ").append(RESET)
                buf.append(valColorFinal).append(valDisplay).append(RESET)
            }
            buf.append("\n")
        }
    }
    is InspectRow.InfoRow -> {
        val color = if (row.isError) Ansi.RED else if (row.isDim) DIM_GRAY else RESET
        val text  = if (row.text.contains("loading", ignoreCase = true)) {
            "${ListRenderer.spinnerFrame(state.gadgetSpinnerFrame)} ${row.text}"
        } else {
            row.text
        }
        buf.append(prefix).append(color).append(text).append(RESET).append("\n")
    }
}
```

- [ ] **Step 4: Build to verify**

```bash
./gradlew linkDebugExecutableMacosArm64
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run and visually verify**

```bash
./build/bin/macosArm64/debugExecutable/barbatos.kexe
```

Navigate: debug → Classes → select a class → Enter. Verify:
- Header + breadcrumb show `Classes › **Inspect** › Watch`
- Package shows dimmed below breadcrumb
- Static members: fields in orange, methods in purple — no "field"/"method" labels
- `H` right-aligned on each static member row (white when not hooked)
- Instances: `inst#XXXX · @HANDLE · active` — destroyed instances fully dim
- Instance attributes: field names orange, object refs blue with `→ I`
- Footer: `H Hook  I Inspect child  W Watch  Esc Back  Ctrl+C Quit`

- [ ] **Step 6: Commit**

```bash
git add src/nativeMain/kotlin/Renderer.kt
git commit -m "feat: redesign Inspect screen with color language and htop layout"
```

---

## Task 4: Redesign Screen 3 — Watch

**Files:**
- Modify: `src/nativeMain/kotlin/Renderer.kt` (`renderHookWatchMode`, add `formatEventLog`)

### Context

The Watch screen currently renders a 40/60 split with a simple one-liner per event. We redesign it to:
- Left panel ≈ 22% of terminal width (narrow HOOKED list)
- Right panel ≈ 78% (rich EVENT LOG)
- Multi-line event blocks: METHOD shows args + return value with nested object expansion; FIELD shows old→new
- Instance hashcode from `event.data["instanceId"]` if present, otherwise omitted
- Nested objects collapse as `{ ··· }` beyond depth 1

The header/breadcrumb are now rendered by the `render()` dispatcher (Task 1), so `renderHookWatchMode` no longer renders its own header line. Adjust `contentHeight` accordingly.

- [ ] **Step 1: Add `formatEventBlock` helper**

Add this private function inside `Renderer`, after `formatTime`:

```kotlin
/**
 * Formats a hook event into a list of display lines (no ANSI in length calculations).
 * Each element is a Pair<String, Int> = (ansi-formatted line, visible character count).
 */
private fun formatEventBlock(event: HookEvent, maxWidth: Int): List<String> {
    val lines = mutableListOf<String>()

    val time       = formatTime(event.timestamp)
    val memberName = extractMemberName(event.target.memberSignature)
    val instanceId = event.data["instanceId"] ?: event.data["handle"] ?: ""
    val hashSuffix = if (instanceId.isNotEmpty()) "  ${DIM_GRAY}@${instanceId.take(7)}${RESET}" else ""
    val countSuffix = if (event.count > 1) " ${DIM_GRAY}×${event.count}${RESET}" else ""

    when (event.target.type) {
        HookType.FIELD -> {
            val value = event.data["value"] ?: ""
            val badgeColor = C_ORANGE
            val badge = "${badgeColor}FIELD${RESET}"
            val header = "${DIM_GRAY}$time${RESET}  $badge  ${WHITE}$memberName${RESET}$hashSuffix$countSuffix"
            lines.add(header)

            // old → new from value string (bridge may send "old|new" or just new)
            val parts = value.split("|")
            if (parts.size == 2) {
                lines.add("  ${Ansi.RED}${parts[0].trim()}${RESET}  ${DIM_GRAY}→${RESET}  ${C_GREEN}${parts[1].trim()}${RESET}")
            } else {
                lines.add("  ${C_GREEN}$value${RESET}")
            }
        }
        HookType.METHOD -> {
            val args   = event.data["args"] ?: ""
            val ret    = event.data["return"] ?: "void"
            val badgeColor = C_PURPLE
            val badge = "${badgeColor}METHOD${RESET}"
            val header = "${DIM_GRAY}$time${RESET}  $badge  ${WHITE}$memberName${RESET}$hashSuffix$countSuffix"
            lines.add(header)

            // Args
            if (args.isNotEmpty()) {
                lines.add("  ${DIM_GRAY}args:${RESET}")
                val argList = args.split(",").map { it.trim() }
                argList.forEach { arg ->
                    val truncated = if (arg.length > maxWidth - 6) arg.take(maxWidth - 9) + "..." else arg
                    lines.add("    ${C_MID_GRAY}$truncated${RESET}")
                }
            }

            // Return value
            lines.add("  ${DIM_GRAY}returned:${RESET}")
            val retLines = formatValue(ret, 4, maxWidth)
            lines.addAll(retLines)
        }
    }

    lines.add("") // blank separator between events
    return lines
}

/**
 * Formats a value string with indentation. Detects ClassName{k=v, ...} patterns
 * and renders them with one field per line. Nested objects beyond depth 1 collapse
 * as `{ ··· }`.
 */
private fun formatValue(value: String, indent: Int, maxWidth: Int): List<String> {
    val pad = " ".repeat(indent)

    // Detect object toString pattern: SomeClass{field=val, ...}  or  SomeClass{...}
    val objectPattern = Regex("""^(\w[\w.${'$'}]*)\{(.*)\}$""", RegexOption.DOT_MATCHES_ALL)
    val match = objectPattern.matchEntire(value.trim())

    if (match != null) {
        val className = match.groupValues[1]
        val body      = match.groupValues[2]
        val lines     = mutableListOf<String>()
        lines.add("$pad${C_BLUE}$className${RESET}${DIM_GRAY} {${RESET}")

        // Split fields by ", " but be careful with nested braces
        val fields = splitTopLevelCommas(body)
        fields.forEach { field ->
            val eqIdx = field.indexOf('=')
            if (eqIdx != -1) {
                val k = field.substring(0, eqIdx).trim()
                val v = field.substring(eqIdx + 1).trim()
                // Collapse any nested objects
                val vDisplay = if (v.contains('{')) "${v.substringBefore('{').trim()}${DIM_GRAY} { ··· }${RESET}" else v
                val truncated = if (vDisplay.length > maxWidth - indent - k.length - 4) vDisplay.take(maxWidth - indent - k.length - 7) + "..." else vDisplay
                lines.add("$pad  ${C_ORANGE}$k${RESET}${DIM_GRAY}: ${RESET}$truncated")
            } else {
                lines.add("$pad  ${C_MID_GRAY}${field.take(maxWidth - indent - 2)}${RESET}")
            }
        }
        lines.add("$pad${DIM_GRAY}}${RESET}")
        return lines
    }

    // Primitive / string
    val truncated = if (value.length > maxWidth - indent) value.take(maxWidth - indent - 3) + "..." else value
    return listOf("$pad${C_MID_GRAY}$truncated${RESET}")
}

/**
 * Splits a comma-separated string while ignoring commas inside braces/brackets.
 */
private fun splitTopLevelCommas(s: String): List<String> {
    val result  = mutableListOf<String>()
    var depth   = 0
    val current = StringBuilder()
    for (ch in s) {
        when (ch) {
            '{', '[', '(' -> { depth++; current.append(ch) }
            '}', ']', ')' -> { depth--; current.append(ch) }
            ',' -> if (depth == 0) {
                result.add(current.toString().trim())
                current.clear()
            } else {
                current.append(ch)
            }
            else -> current.append(ch)
        }
    }
    if (current.isNotBlank()) result.add(current.toString().trim())
    return result
}
```

- [ ] **Step 2: Rewrite `renderHookWatchMode`**

Replace the entire function:

```kotlin
private fun renderHookWatchMode(buf: StringBuilder, state: AppState, termWidth: Int, termHeight: Int) {
    // Header and breadcrumb are rendered by render() before this call.
    // Available height: total height minus header(2) + breadcrumb(2) + footer(1) = 5 lines overhead.
    val contentHeight = termHeight - 5

    val leftWidth  = (termWidth * 0.22).toInt().coerceAtLeast(12)
    val rightWidth = termWidth - leftWidth - 1  // 1 for separator

    // ── Column headers ──────────────────────────────────────────────────────
    val leftHeader  = " HOOKED"
    val rightHeader = " EVENT LOG"
    buf.append(C_MID_GRAY)
    buf.append(leftHeader.padEnd(leftWidth))
    buf.append("│")
    buf.append(rightHeader)
    buf.append(RESET).append("\n")
    buf.append(C_SEP)
    buf.append("─".repeat(leftWidth)).append("┼").append("─".repeat(rightWidth))
    buf.append(RESET).append("\n")

    val availableHeight = contentHeight - 2  // subtract column header rows

    // ── Build event log lines ────────────────────────────────────────────────
    // Events are displayed newest-first (bottom of list = oldest).
    val eventLines = mutableListOf<String>()
    for (event in state.hookEvents.asReversed()) {
        eventLines.addAll(formatEventBlock(event, rightWidth - 2))
    }
    // Apply scroll offset
    val logStart  = state.hookLogScrollOffset.coerceIn(0, maxOf(0, eventLines.size - availableHeight))
    val logSlice  = eventLines.drop(logStart).take(availableHeight)

    val activeHooksList = state.activeHooks.toList()

    // ── Render rows ──────────────────────────────────────────────────────────
    for (y in 0 until availableHeight) {
        // Left: HOOKED list
        if (y < activeHooksList.size) {
            val hook       = activeHooksList[y]
            val isSelected = y == state.selectedHookIndex
            val selMark    = if (isSelected) "› " else "  "
            val color      = if (hook.type == HookType.METHOD) C_PURPLE else C_ORANGE
            val name       = extractMemberName(hook.memberSignature)
            val selColor   = if (isSelected) WHITE else DIM_GRAY

            val cell = "$selColor$selMark$color$name$RESET"
            val visLen = 2 + name.length
            buf.append(cell)
            buf.append(" ".repeat(maxOf(0, leftWidth - visLen)))
        } else {
            buf.append(" ".repeat(leftWidth))
        }

        buf.append(C_SEP).append("│").append(RESET)

        // Right: event log
        val line = logSlice.getOrNull(y) ?: ""
        buf.append(line)
        // No need to pad — newline follows
        buf.append("\n")
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
./gradlew linkDebugExecutableMacosArm64
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run and visually verify**

```bash
./build/bin/macosArm64/debugExecutable/barbatos.kexe
```

Navigate to Watch screen. Verify:
- Header + breadcrumb show `Classes › Inspect › **Watch**`
- HOOKED column is narrow (≈22%), member names orange (field) or purple (method)
- EVENT LOG column shows multi-line blocks per event
- METHOD events show `args:` section and `returned:` section
- Object return values expanded with one field per line, nested objects collapse as `{ ··· }`
- FIELD events show `old → new` if bridge provides `|`-separated value; otherwise just the value
- Footer: `D Remove hook  C Clear log  Esc Back  Ctrl+C Quit`

- [ ] **Step 5: Commit**

```bash
git add src/nativeMain/kotlin/Renderer.kt
git commit -m "feat: redesign Watch screen with narrow HOOKED panel and rich event log"
```

---

## Self-Review Notes

**Spec coverage check:**

| Spec requirement | Task |
|-----------------|------|
| htop-style fixed header + breadcrumb + footer | Task 1 |
| Orange = field, purple = method, blue = ref, green = active | Task 1 (constants) + Tasks 3–4 (usage) |
| Classes: package dimmed on selected row only | Task 2 |
| Classes: instance count green/dim | Task 2 |
| Inspect: H hook on static members, not instances | Task 3 |
| Inspect: fields orange, methods purple, no labels | Task 3 |
| Inspect: instances show @hashcode + active/destroyed | Task 3 |
| Inspect: destroyed instances in dark gray | Task 3 |
| Watch: HOOKED column ≈22% | Task 4 |
| Watch: event log with @hashcode, method args, complex return | Task 4 |
| English-only text | All tasks — no Portuguese strings introduced |

**Known limitations (acceptable per spec):**
- `@hashcode` in Watch events only shows if `data["instanceId"]` or `data["handle"]` is present in bridge response. The bridge currently may not send this field — display is omitted gracefully.
- "destroyed" detection on `InstanceRow` is heuristic (checks summary string). A future bridge update can make this explicit.
