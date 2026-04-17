# Design Doc: Update Swagger, MCP, and Tests for Bridge Functions

Update the documentation and tooling interfaces to match the current capabilities of the `barbatos` Frida bridge and agent.

## Goals
- Achieve 100% parity between `bridge.py` RPC handlers and Swagger documentation.
- Expose all bridge functions as MCP tools for AI-driven debugging and automation.
- Verify RPC routing via unit tests.

## Proposed Changes

### 1. bridge/swagger.yaml
Add definitions for the following methods:
- `getpackagename`: Returns the current target's package name.
- `unhookMethod`: Removes an active hook.
- `setMethodImplementation`: Replaces a method's body with custom JS/TS.
- `getInstanceAddresses`: Lists raw memory addresses of instances.
- `runOnce`: Executes a one-time script in the context of a class/method.
- `prepareEnvironment`: Initial ADB setup.
- `checkOrPushGadget`: Ensures Frida Gadget is on device.
- `resetInjection`: Resets the injection state machine.
- `injectGadgetFromScratch`: Background injection orchestration.
- `injectJdwp`: Direct JDWP injection trigger.

### 2. mcp_server/server.py
Expose the above methods as MCP tools:
- `barbatos_get_package_name()`
- `barbatos_unhook_method(class_name, method_sig)`
- `barbatos_set_method_implementation(class_name, method_sig, code)`
- `barbatos_get_instance_addresses(class_name)`
- `barbatos_run_once(class_name, method_sig, code)`
- `barbatos_prepare_environment()`
- `barbatos_check_or_push_gadget()`
- `barbatos_reset_injection()`
- `barbatos_inject_gadget_from_scratch(force, with_logs, limit)`
- `barbatos_inject_jdwp(package_name, cmd, break_on)`

### 3. bridge/test_bridge.py
Add a comprehensive test case for `handle_rpc` that mocks the underlying methods (e.g., `list_classes`, `script.exports_sync.*`) and verifies that `handle_rpc` calls them with the correct parameters and returns the expected JSON-RPC structure.

## Verification Plan
1. **Swagger Validation**: Manually verify `swagger.yaml` against a linter (or visual check).
2. **MCP Tool Testing**: Run `server.py` and list tools to ensure all are present and correctly typed.
3. **Unit Tests**: Run `python3 bridge/test_bridge.py` and ensure 100% pass rate.
