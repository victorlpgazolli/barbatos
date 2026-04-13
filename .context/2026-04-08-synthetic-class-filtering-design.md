# Design Spec: Simple Filtering for Synthetic Classes in DEBUG_CLASS_FILTER

## Problem
In `DEBUG_CLASS_FILTER` mode, the class list is often cluttered with synthetic classes (e.g., `$$ExternalSyntheticLambda0`, `$1`, etc.) and anonymous inner classes. This creates visual pollution and makes it harder to find the main classes.

## Proposed Solution: Simple Filtering (Toggle)
We will introduce a toggle to show/hide "synthetic" classes (any class name containing `$`). By default, these classes will be hidden to keep the list clean.

### 1. App State (`AppState.kt`)
Add a new property to track the toggle state:
- `var showSyntheticClasses: Boolean = false`

### 2. Filtering Logic (`CommandExecutor.kt`)
Update `sortClasses` to include a filtering step:
- If `showSyntheticClasses` is `false`, filter out any class that contains `$`.
- **Exception:** If the `searchQuery` provided by the user contains a `$`, we bypass the filter and show matching synthetic classes regardless of the toggle state. This allows users to find specific synthetic classes by explicitly searching for them.

### 3. User Interaction (`Main.kt`)
- Add handling for the `S` (or `s`) key in `DEBUG_CLASS_FILTER` mode.
- When pressed, toggle `state.showSyntheticClasses`.
- Immediately update `state.displayedClasses` by re-running `CommandExecutor.sortClasses`.
- Re-render the UI.

### 4. Visual Feedback (`Renderer.kt`)
- Update the sticky footer in `DEBUG_CLASS_FILTER` mode to include `S: Synthetic`.
- (Optional) When `showSyntheticClasses` is `true`, we could show a small indicator that we are in "All Classes" mode, but the footer hint might be enough.

## Approaches Considered
- **Grouping (Expandable):** Show parent classes only, with a count of children that can be expanded with `ArrowRight`. Rejected by the user in favor of a simpler toggle.
- **Smart Filtering:** Only hide purely synthetic classes (numeric suffixes or `ExternalSyntheticLambda`). Rejected for simplicity; the toggle allows the user to decide when they want to see inner classes.

## Success Criteria
- By default, classes containing `$` are not visible in the search results.
- Pressing `S` reveals all classes including synthetic ones.
- Searching for a string containing `$` (e.g., `MyClass$`) reveals synthetic classes even if the toggle is off.
- The footer clearly shows the `S` keybinding.
