# Class Inspection Mode (DEBUG_INSPECT_CLASS)

This change implements the ability to transition from filtering classes to profoundly inspecting their properties and methods using Frida reflection, all contained structurally within separated `tmux` window panes.

## Proposed Changes

### 1. `Main.kt` & `AppState.kt` (State & Argument Parsing)
- **AppMode Enum**: Introduce `DEBUG_INSPECT_CLASS`.
- **AppState**: Add `inspectTargetClassName`, `sharedInspectResult.value = Pair(List<String>, List<String>)` to asynchronously fetch data without blocking UI.
- **Argument Parsing**: Update `Main.kt` to parse `--mode debug_inspect_class <ClassName>`.
- **DEBUG_CLASS_FILTER Enter Key Setup**: Adjust the `KeyEvent.Enter` for `DEBUG_CLASS_FILTER` to execute `TmuxManager.appendInspectWindow()` with the selected class name, rather than "do nothing".

### 2. `TmuxManager.kt` (Multi-Window Logic)
- Add `fun appendInspectWindow(sessionName: String, className: String)`:
  - This function executes: `tmux new-window -t $sessionName -n "$className" ./build/bin/macosArm64/debugExecutable/barbatos.kexe --mode debug_inspect_class $className 2>/dev/null`

---

### 3. Frida RPC Integration (`bridge/agent.js` & `bridge.py`)

#### [MODIFY] [bridge.py](./kmp_debugger/bridge/bridge.py)
Update `handle_rpc` conditional match for `"inspectClass"` logic mapping to the Javascript wrapper.

#### [MODIFY] [agent.js](./kmp_debugger/bridge/agent.js)
Export `inspectclass: function(className)` that evaluates runtime reflection.
```javascript
inspectclass: function(className) {
    var attributes = [];
    var methods = [];
    try {
        Java.perform(function() {
            var clazz = Java.use(className);
            var classDef = clazz.class;
            
            var fields = classDef.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                attributes.push(fields[i].toString());
            }
            
            var funcs = classDef.getDeclaredMethods();
            for (var j = 0; j < funcs.length; j++) {
                methods.push(funcs[j].toString());
            }
        });
        return { attributes: attributes, methods: methods };
    } catch (e) {
        return { error: e.toString() };
    }
}
```

#### [MODIFY] [RpcClient.kt](./kmp_debugger/src/nativeMain/kotlin/RpcClient.kt)
Create `inspectClass(className: String)` networking suspending capability and map its return data classes `ClassInspectionResult { val attributes: List<String>, val methods: List<String> }`.

---

### 4. `Renderer.kt` (UI Layer)
- Render the `DEBUG_INSPECT_CLASS` layout natively:
  - Discard the text input box layout.
  - Draw the class name at the very top header using `K_VIOLET`.
  - Display `Attributes:` sequentially printing fields in `DIM` color.
  - Display `Methods:` sequentially printing definitions in `DIM` color.
  - Implement vertical scrolling so user can traverse extensive lists using up/down arrow keys (updating `state.cursorPosition` and `state.selectedClassIndex` visually as offsets relative to total list lengths).

## Verification Plan
1. Enter `kmp_debugger/barbatos`.
2. Launch `debug` session.
3. List classes using substring.
4. Focus over `<class.name>` and press ENTER.
5. Verify `tmux` instantiates a secondary background window actively focused on the attributes reflection for the user!
