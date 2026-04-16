# Arbitrary Execution & Root Injection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable manual arbitrary execution via the 'X' key in the Watch view and automatic root-level injection for non-debuggable applications.

**Architecture:** 
- **Feature 1:** Uses a one-time RPC call to execute arbitrary JS in the target process, triggered from `DEBUG_HOOK_WATCH`. Requires the hook to be active.
- **Feature 2:** Implements an environment-aware injection state machine in the Python bridge that switches to `frida-server` deployment when Root is detected on a non-debuggable app.

**Tech Stack:** Kotlin Native, Python, Frida, TypeScript/JavaScript.

---

### Task 1: Bridge Agent - One-time Execution Support

**Files:**
- Modify: `bridge/agent.js`

- [x] **Step 1: Add `runonce` export to `agent.js`**
- [x] **Step 2: Add `git add bridge/agent.js`**

### Task 2: Bridge Python - Manual Execution RPC & Transpilation

**Files:**
- Modify: `bridge/bridge.py`

- [x] **Step 1: Add `runOnce` method to `FridaBridge` and RPC handler**
- [x] **Step 2: Add `git add bridge/bridge.py`**

### Task 3: Kotlin Native - Main & Renderer for 'X' Key

**Files:**
- Modify: `src/unixMain/kotlin/Main.kt`
- Modify: `src/unixMain/kotlin/Renderer.kt`

- [x] **Step 1: Add 'X' key handler in `Main.kt` for `DEBUG_HOOK_WATCH` mode**
- [x] **Step 2: Add `[EXEC]` event rendering in `Renderer.kt`**
- [x] **Step 3: Add `git add src/unixMain/kotlin/Main.kt src/unixMain/kotlin/Renderer.kt`**

### Task 4: Kotlin Native - RpcClient & CommandExecutor for Execution

**Files:**
- Modify: `src/commonMain/kotlin/RpcClient.kt`
- Modify: `src/commonMain/kotlin/CommandExecutor.kt`

- [x] **Step 1: Add `runOnce` to `RpcClient`**
- [x] **Step 2: Implement `executeWatchedHook` in `CommandExecutor.kt` to spawn editor and call `runOnce` (ephemeral code)**
- [x] **Step 3: Ensure execution only proceeds if hook is active**
- [x] **Step 4: Add `git add src/commonMain/kotlin/RpcClient.kt src/commonMain/kotlin/CommandExecutor.kt`**

### Task 5: Bridge Python - Environment Detection & Frida-Server Manager

**Files:**
- Modify: `bridge/bridge.py`

- [x] **Step 1: Add Root and Debuggable checks**
- [x] **Step 2: Implement `frida-server` download and push**
- [x] **Step 3: Add `git add bridge/bridge.py`**

### Task 6: Bridge Python - Smart Injection Branching

**Files:**
- Modify: `bridge/bridge.py`

- [x] **Step 1: Update `inject_gadget_from_scratch`**
- [x] **Step 2: Add `git add bridge/bridge.py`**

### Task 7: Verification

- [x] **Step 1: Verify Arbitrary Execution from Watch view**
- [x] **Step 2: Verify Root Injection**
