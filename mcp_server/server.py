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
            response.raise_for_status()
            data = response.json()
            if "error" in data:
                return f"Error from bridge: {data['error']}"
            return data.get("result")
    except httpx.ConnectError:
        return "Error: Could not connect to the barbatos bridge. Make sure 'barbatos' or 'bridge.py' is running on port 8080."
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

if __name__ == "__main__":
    mcp.run()
