import os
import sys

def _get_resource_path(relative_path):
    base = getattr(sys, '_MEIPASS', os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(base, relative_path)

def _get_version():
    try:
        with open(_get_resource_path('version.txt')) as f:
            return f.read().strip()
    except Exception:
        return 'unknown'

if __name__ == '__main__' and len(sys.argv) == 2 and sys.argv[1] == '--version':
    print(f'barbatos-mcp {_get_version()}')
    sys.exit(0)

import argparse
import httpx
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("barbatos-debugger")
RPC_URL = "http://localhost:8080/rpc"

async def call_rpc(method: str, params: dict = None) -> dict:
    """Helper to send JSON-RPC requests to the barbatos bridge."""
    payload = {
        "jsonrpc": "2.0",
        "method": method,
        "params": params or {},
        "id": 1
    }

    try:
        async with httpx.AsyncClient() as client:
            response = await client.post(RPC_URL, json=payload, timeout=10.0)
            # Parse JSON first so error_message is never discarded on HTTP 500
            try:
                data = response.json()
            except Exception:
                response.raise_for_status()
                return f"Error: bridge returned non-JSON response (HTTP {response.status_code})"
            if not response.is_success:
                err = data.get("error", {})
                msg = err.get("error_message") or err.get("message") or str(err)
                return f"Error from bridge: {msg}"
            if "error" in data:
                err = data["error"]
                msg = err.get("error_message") or err.get("message") or str(err)
                return f"Error from bridge: {msg}"
            return data.get("result")
    except httpx.ConnectError:
        return (
            "Error: Could not connect to the barbatos bridge on port 8080. "
            "Start it with: barbatos-bridge [--serial <device-serial>]"
        )
    except Exception as e:
        return f"Error executing {method}: {str(e)}"

# Tools will be added below

@mcp.tool()
async def barbatos_list_classes(search_param: str = "", app_package: str = "", offset: int = 0, limit: int = 200) -> list | str:
    """Retrieves loaded Java classes in the target process.
    Args:
        search_param: Filter classes by name (case-insensitive).
        app_package: Package name to prioritize in the result list.
        offset: Pagination offset.
        limit: Max results.
    """
    return await call_rpc("listClasses", {
        "search_param": search_param,
        "app_package": app_package,
        "offset": offset,
        "limit": limit
    })

@mcp.tool()
async def barbatos_inspect_class(class_name: str) -> dict | str:
    """Returns fields and methods of a specific class.
    Args:
        class_name: Full class name (e.g., 'java.lang.String').
    """
    return await call_rpc("inspectClass", {"className": class_name})

@mcp.tool()
async def barbatos_count_instances(class_name: str) -> int | str:
    """Counts live instances of a class on the heap.
    Args:
        class_name: Full class name.
    """
    return await call_rpc("countInstances", {"className": class_name})

@mcp.tool()
async def barbatos_list_instances(class_name: str) -> list | str:
    """Returns handles/IDs of live instances for a given class.
    Args:
        class_name: Full class name.
    """
    res = await call_rpc("listInstances", {"className": class_name})
    if isinstance(res, dict) and "instances" in res:
        return res["instances"]
    return res

@mcp.tool()
async def barbatos_inspect_instance(class_name: str, instance_id: str, offset: int = 0, limit: int = 50) -> list | str:
    """Recursively explores an instance's fields.
    Args:
        class_name: Full class name.
        instance_id: HashCode or handle of the instance.
        offset: Pagination offset for fields.
        limit: Pagination limit.
    """
    res = await call_rpc("inspectInstance", {
        "className": class_name,
        "id": instance_id,
        "offset": offset,
        "limit": limit
    })
    if isinstance(res, dict) and "attributes" in res:
        return res["attributes"]
    return res

@mcp.tool()
async def barbatos_set_field_value(class_name: str, instance_id: str, field_name: str, field_type: str, new_value: str) -> str:
    """Modifies a primitive field and triggers Compose recomposition (if applicable).
    Args:
        class_name: Full class name.
        instance_id: Instance ID.
        field_name: The name of the field to modify.
        field_type: Type (e.g., 'int', 'string', 'boolean').
        new_value: The new value as a string.
    """
    return await call_rpc("setFieldValue", {
        "className": class_name,
        "id": instance_id,
        "fieldName": field_name,
        "type": field_type,
        "newValue": new_value
    })

@mcp.tool()
async def barbatos_hook_method(class_name: str, method_sig: str) -> str:
    """Intercepts method calls and logs events.
    Args:
        class_name: Class of the method.
        method_sig: Full method signature (as returned by inspectClass).
    """
    return await call_rpc("hookMethod", {
        "className": class_name,
        "methodSig": method_sig
    })

@mcp.tool()
async def barbatos_get_hook_events() -> list | str:
    """Returns the latest method interception events collected by the agent."""
    return await call_rpc("getHookEvents", {})

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

MCP_TOOLS_DESCRIPTION = """
Available MCP tools (Model Context Protocol capabilities):

  barbatos_list_classes          List loaded Java/Kotlin classes in the target process
  barbatos_inspect_class         Return fields and methods of a specific class
  barbatos_count_instances       Count live instances of a class on the heap
  barbatos_list_instances        Return handles/IDs of live instances for a class
  barbatos_inspect_instance      Recursively explore an instance's fields
  barbatos_set_field_value       Modify a primitive field value on a live instance
  barbatos_hook_method           Intercept method calls and capture events
  barbatos_unhook_method         Remove an active method hook
  barbatos_get_hook_events       Return the latest method interception events
  barbatos_set_method_implementation  Replace a method's implementation with custom JS
  barbatos_run_once              Execute JS code once in the context of a class/method
  barbatos_get_instance_addresses     Return memory addresses of all live instances
  barbatos_get_package_name      Return the current target's package name
  barbatos_prepare_environment   Run initial ADB port-forward setup
  barbatos_check_or_push_gadget  Ensure Frida Gadget is present on the device
  barbatos_inject_gadget_from_scratch  Orchestrate the full injection sequence
  barbatos_inject_jdwp           Directly trigger JDWP injection
  barbatos_reset_injection       Reset the injection state machine
  barbatos_health_check          Check bridge and device health status

Requires barbatos-bridge to be running on port 8080.
"""

@mcp.tool()
async def barbatos_health_check() -> str:
    """
    Checks the health of the barbatos bridge, ADB device connection, and Frida session.
    Returns a human-readable status report with actionable fix suggestions.
    Call this first when any tool returns an error.
    """

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        prog='barbatos-mcp',
        description='barbatos-mcp: MCP server exposing Android runtime debugging tools to AI agents',
        epilog=MCP_TOOLS_DESCRIPTION,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument('--version', action='version', version=f'barbatos-mcp {_get_version()}')
    parser.parse_args()
    mcp.run()
