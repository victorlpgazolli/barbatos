# Update Swagger, MCP, and Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Achieving 100% parity between Frida bridge functions, Swagger documentation, MCP tools, and unit tests.

**Architecture:** 
- **Documentation:** Updating `bridge/swagger.yaml` with missing method definitions and schemas.
- **Tools:** Updating `mcp_server/server.py` to expose all bridge RPCs as MCP tools.
- **Testing:** Enhancing `bridge/test_bridge.py` to verify RPC routing in the Python bridge.

**Tech Stack:** Python, OpenAPI/Swagger, MCP (FastMCP), Frida.

---

### Task 1: Update bridge/swagger.yaml - Schema Definitions

**Files:**
- Modify: `bridge/swagger.yaml`

- [ ] **Step 1: Add schemas for missing parameters and responses**

```yaml
    getinstanceaddressesParams:
      type: object
      required: [className]
      properties:
        className:
          type: string

    runOnceParams:
      type: object
      required: [className, methodSig, code]
      properties:
        className:
          type: string
        methodSig:
          type: string
        code:
          type: string

    setMethodImplementationParams:
      type: object
      required: [className, methodSig, code]
      properties:
        className:
          type: string
        methodSig:
          type: string
        code:
          type: string

    unhookMethodParams:
      type: object
      required: [className, methodSig]
      properties:
        className:
          type: string
        methodSig:
          type: string

    injectJdwpParams:
      type: object
      required: [package_name]
      properties:
        cmd:
          type: string
        break_on:
          type: string
          default: "android.os.Handler.dispatchMessage"
        package_name:
          type: string
```

- [ ] **Step 2: Update `x-rpc-methods` list**

```yaml
# x-rpc-methods: Documentation for SDK/TUI developers
x-rpc-methods:
  # ... existing ...
  getpackagename:
    description: Returns the current target's package name.
  unhookMethod:
    description: Removes an active hook.
  setMethodImplementation:
    description: Replaces a method's body with custom JS/TS.
  getInstanceAddresses:
    description: Lists raw memory addresses of instances.
  runOnce:
    description: Executes a one-time script in the context of a class/method.
  prepareEnvironment:
    description: Initial ADB setup.
  checkOrPushGadget:
    description: Ensures Frida Gadget is on device.
  resetInjection:
    description: Resets the injection state machine.
  injectGadgetFromScratch:
    description: Orchestrates the JDWP -> Gadget -> Agent sequence.
  injectJdwp:
    description: Direct JDWP injection trigger.
```

- [ ] **Step 3: Commit**

```bash
git add bridge/swagger.yaml
git commit -m "docs: update swagger with missing rpc methods and schemas"
```

### Task 2: Update mcp_server/server.py - Injection & Environment Tools

**Files:**
- Modify: `mcp_server/server.py`

- [ ] **Step 1: Add environment and injection tools**

```python
@mcp.tool()
async def barbatos_get_package_name() -> str:
    """Returns the current target's package name."""
    return await call_rpc("getpackagename")

@mcp.tool()
async def barbatos_prepare_environment() -> dict | str:
    """Initial ADB setup (port forwards, etc.)."""
    return await call_rpc("prepareEnvironment")

@mcp.tool()
async def barbatos_check_or_push_gadget() -> dict | str:
    """Ensures Frida Gadget is on device."""
    return await call_rpc("checkOrPushGadget")

@mcp.tool()
async def barbatos_reset_injection() -> dict | str:
    """Resets the injection state machine."""
    return await call_rpc("resetInjection")

@mcp.tool()
async def barbatos_inject_gadget_from_scratch(force: bool = False, with_logs: bool = False, limit: int = 50) -> dict | str:
    """Orchestrates the full injection sequence (JDWP -> Gadget -> Agent)."""
    return await call_rpc("injectGadgetFromScratch", {
        "force": force,
        "with_logs": with_logs,
        "limit": limit
    })

@mcp.tool()
async def barbatos_inject_jdwp(package_name: str, cmd: str = None, break_on: str = "android.os.Handler.dispatchMessage") -> dict | str:
    """Directly triggers JDWP injection."""
    return await call_rpc("injectJdwp", {
        "package_name": package_name,
        "cmd": cmd,
        "break_on": break_on
    })
```

- [ ] **Step 2: Commit**

```bash
git add mcp_server/server.py
git commit -m "feat(mcp): add injection and environment tools"
```

### Task 3: Update mcp_server/server.py - Advanced Debugging Tools

**Files:**
- Modify: `mcp_server/server.py`

- [ ] **Step 1: Add advanced debugging tools**

```python
@mcp.tool()
async def barbatos_unhook_method(class_name: str, method_sig: str) -> str:
    """Removes an active method hook.
    Args:
        class_name: Full class name.
        method_sig: Full method signature.
    """
    return await call_rpc("unhookMethod", {
        "className": class_name,
        "methodSig": method_sig
    })

@mcp.tool()
async def barbatos_set_method_implementation(class_name: str, method_sig: str, code: str) -> str:
    """Replaces a method's implementation with custom JavaScript.
    Args:
        class_name: Full class name.
        method_sig: Full method signature.
        code: JavaScript function body (accepts 'context').
    """
    return await call_rpc("setMethodImplementation", {
        "className": class_name,
        "methodSig": method_sig,
        "code": code
    })

@mcp.tool()
async def barbatos_get_instance_addresses(class_name: str) -> list | str:
    """Returns memory addresses of all live instances of a class."""
    return await call_rpc("getInstanceAddresses", {"className": class_name})

@mcp.tool()
async def barbatos_run_once(class_name: str, method_sig: str, code: str) -> str:
    """Executes code once in the context of a class/method.
    Args:
        class_name: Target class.
        method_sig: Target method signature.
        code: JavaScript function body.
    """
    return await call_rpc("runOnce", {
        "className": class_name,
        "methodSig": method_sig,
        "code": code
    })
```

- [ ] **Step 2: Commit**

```bash
git add mcp_server/server.py
git commit -m "feat(mcp): add advanced debugging tools (unhook, runOnce, etc.)"
```

### Task 4: Update bridge/test_bridge.py - RPC Routing Tests

**Files:**
- Modify: `bridge/test_bridge.py`

- [ ] **Step 1: Add `test_handle_rpc_routing` to `TestFridaBridge`**

```python
    @patch('bridge.FridaBridge.get_session')
    def test_handle_rpc_routing(self, mock_get_session):
        # Mock script and exports
        mock_script = MagicMock()
        self.bridge.script = mock_script
        mock_script.exports_sync.listclasses.return_value = ["java.lang.String"]
        
        # Test listClasses routing
        result = self.bridge.handle_rpc("listClasses", {"search_param": "String"})
        self.assertEqual(result, ["java.lang.String"])
        mock_script.exports_sync.listclasses.assert_called_with("String")

        # Test runOnce routing
        mock_script.exports_sync.runonce.return_value = True
        result = self.bridge.handle_rpc("runOnce", {"className": "A", "methodSig": "M", "code": "C"})
        self.assertTrue(result)
        mock_script.exports_sync.runonce.assert_called_with("A", "M", "C")
```

- [ ] **Step 2: Run tests**

Run: `python3 bridge/test_bridge.py`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add bridge/test_bridge.py
git commit -m "test: add RPC routing tests for Frida bridge"
```
