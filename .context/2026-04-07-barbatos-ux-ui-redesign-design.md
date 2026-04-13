# Barbatos UX/UI Redesign

**Date:** 2026-04-07  
**Status:** Approved

## Overview

Full UX/UI redesign of Barbatos (Interactive Debug Kit), a Frida-powered Android runtime debugger with a terminal interface. The goal is to fix two core pain points: confusing navigation flow between screens, and a weak visual identity with no personality. The platform stays TUI — no core rewrite required.

## Design Principles

- **htop as visual reference**: fixed header + scrollable content + fixed footer with visible shortcuts
- **Color as language**: no type labels ("field", "method") — color conveys meaning consistently across all screens
- **Breadcrumb navigation**: the user always knows where they are and how to get back
- **Package on demand**: full package names are available but only surfaced on selected items, keeping lists clean

## Color Language

Applied globally across all screens. No labels needed — color speaks for itself.

| Color   | Meaning                              |
|---------|--------------------------------------|
| Orange  | Field / attribute                    |
| Purple  | Method                               |
| Blue    | Object reference (inspectable child) |
| Green   | Active instance / live count         |
| Dimmed  | Destroyed / irrelevant               |

## Layout Structure

Every screen shares the same 4-zone layout:

```
┌─ Header ──────────────── Barbatos · com.example.app · pid 12345 · ● connected ─┐
├─ Breadcrumb ──────────── Classes › Inspect › Watch ────────────────────────┤
├─ Content (scrollable) ─────────────────────────────────────────────────────┤
│                                                                              │
└─ Footer ──────────────── F-key shortcuts (htop style) ─────────────────────┘
```

The header and footer are always visible. Breadcrumb shows all three stages; the current stage is highlighted, others are dimmed.

## Screen 1 — Classes

**Purpose:** Browse and filter all classes loaded in the target process.

**Layout:**
- Below breadcrumb: inline search bar with a cursor and result count on the right
- List of classes, one per row
- Each row: class name (left) · instance count (right, green if > 0, dimmed if 0)
- **Selected row only**: full package name appears dimmed on a second line below the class name

**Footer shortcuts:** `Enter` Inspect · `W` Watch · `Q` Quit

**Loading state:** ASCII spinner shown inside the list area while fetching classes.

## Screen 2 — Inspect Class

**Purpose:** Explore the static members and live instances of a selected class.

**Layout:**
- Below breadcrumb: package name of the class, dimmed, one line
- Two collapsible sections:

### Static Members (collapsible)

Shows fields and methods of the class. No "field" or "method" labels — color identifies type:

- **Fields**: name in orange · `:` · value in gray
- **Methods**: name in purple · `(` · param types in gray · `)`
- Each row has `H` on the right to hook that member
- Hook applies to all instances of the class (not per-instance)

### Instances (collapsible)

List of live instances. Each row: `inst#N · @hashcode · active/destroyed`

- **Active instances**: normal brightness, left border accent when selected
- **Destroyed instances**: rendered in dark gray (e.g. ANSI color 238/239), neutral — de-prioritized visually without disappearing
- Instances are expandable to show their fields recursively:
  - Field names in orange, values in gray
  - Object references in blue with `→ I` hint to inspect that child
  - Nested expansion follows the same rules recursively
  - Cycle detection prevents infinite loops

**Footer shortcuts:** `H` Hook member · `I` Inspect child · `W` Watch · `Esc` Back

## Screen 3 — Watch

**Purpose:** Monitor hooked fields and methods in real time.

**Layout:** Horizontal split

### Left panel — HOOKED (~22% width)

List of all active hooks. Each item shows:
- Color badge: orange for fields, purple for methods
- Member name (no class label needed in context)

Selected hook is highlighted with a left border accent.

### Right panel — EVENT LOG (~78% width)

Chronological stream of hook events. Each event block:

```
14:23:07  [METHOD]  getUser  @4a2f1c8
  args:
    userId: "usr_29af"
  returned: User {
    id: "usr_29af"
    name: "Alice"
    role: ADMIN
    createdAt: 1712505600
    prefs: UserPrefs { ··· }
  }
```

- **Timestamp** in dim gray
- **Badge** colored by type (FIELD badge orange, METHOD badge purple)
- **Member name** in white
- **`@hashcode`** of the instance that triggered the event, in dim gray
- **Fields**: show `old value → new value`
- **Methods**: show named args with types, then return value
- **Complex objects**: expanded with indented fields; nested objects that would go deep are collapsed as `{ ··· }` to keep the log readable
- Events cap at 100 entries (oldest dropped); duplicate events within 500ms are collapsed with a counter

**Footer shortcuts:** `D` Remove hook · `C` Clear log · `Esc` Back

## Navigation Flow

```
DEFAULT
  └─ debug
       ├─ Classes (Screen 1)
       │    └─ Enter → Inspect (Screen 2)
       │              ├─ H     → adds hook, stays on Inspect
       │              ├─ I     → pushes current class to back stack, navigates into child class
       │              ├─ W     → Watch (Screen 3)
       │              └─ Esc   → Back (pops back stack or returns to Classes)
       └─ W → Watch (Screen 3)
                └─ Esc → Back to previous screen
```

`Esc` always goes back one level. The breadcrumb reflects the current position at all times.

## What Is Not Changing

- The Frida bridge (`bridge/bridge.py`) — no changes
- RPC protocol between CLI and bridge — no changes
- The tmux-based process spawning model for screens — preserved
- Core Kotlin Native build setup — no changes

## Out of Scope

- Web UI (considered and deliberately deferred — no SSH support, distribution complexity)
- New debug features (this spec covers UI only)
- Settings or configuration screens
