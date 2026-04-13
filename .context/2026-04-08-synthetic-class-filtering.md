# Synthetic Class Filtering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a toggle to show/hide synthetic and inner classes (containing `$`) in the class filter mode to reduce visual clutter.

**Architecture:** Add a `showSyntheticClasses` flag to `AppState`, update `CommandExecutor.sortClasses` to filter based on this flag (unless searching with `$`), and add an 'S' key toggle in `Main.kt` with a footer hint.

**Tech Stack:** Kotlin Native, POSIX termios, ANSI escape codes.

---

### Task 1: Update AppState

**Files:**
- Modify: `src/nativeMain/kotlin/AppState.kt`

- [ ] **Step 1: Add `showSyntheticClasses` to `AppState`**
- [ ] **Step 2: Commit**

---

### Task 2: Implement Filtering Logic in CommandExecutor

**Files:**
- Modify: `src/nativeMain/kotlin/CommandExecutor.kt`
- Test: `src/nativeTest/kotlin/CommandExecutorTest.kt`

- [ ] **Step 1: Update `sortClasses` signature and implementation**
- [ ] **Step 2: Add unit tests for filtering**
- [ ] **Step 3: Run tests**
- [ ] **Step 4: Commit**

---

### Task 3: Update Main.kt UI logic

**Files:**
- Modify: `src/nativeMain/kotlin/Main.kt`

- [ ] **Step 1: Update existing `sortClasses` calls to pass the new flag**
- [ ] **Step 2: Add 'S' key handler**
- [ ] **Step 3: Commit**

---

### Task 4: Update Footer in Renderer

**Files:**
- Modify: `src/nativeMain/kotlin/Renderer.kt`

- [ ] **Step 1: Add footer hint for the 'S' toggle**
- [ ] **Step 2: Commit**

---

### Task 5: Final Validation

- [ ] **Step 1: Build the project**
- [ ] **Step 2: Manual verification (informational)**
