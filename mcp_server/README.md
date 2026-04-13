# Barbatos MCP Server

This MCP server acts as an adapter connecting AI agents and LLMs (like Claude Desktop or Cursor) to the **Barbatos** interactive debugger via its local JSON-RPC API.

## AI-Driven Debugging

By connecting Barbatos to your AI agent via MCP, you empower the agent to autonomously interact with the Android runtime. The agent can use the 8 provided tools to:
*   Search for specific classes and instances.
*   Read live properties and variables from the heap.
*   Hook methods to monitor arguments and return values.
*   Modify variables in real-time to test hypotheses or fix states without recompiling.

### Example Prompts:
*   "Locate the active `PaymentSession` instance and check the `errorCode` field to understand why the transaction is failing."
*   "Hook the `NotificationManager.show` method to intercept and log the raw content of incoming push notifications."
*   "List all live instances of `NetworkError`, check their `message` field, and identify recurring failure patterns."

## Installation

1. Ensure Python 3.10+ is installed.
2. Navigate to this directory: `cd mcp_server`
3. Install dependencies: `pip install -r requirements.txt`

## Claude Desktop Configuration

Add the following to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "barbatos-debugger": {
      "command": "python",
      "args": ["/absolute/path/to/barbatos/mcp_server/server.py"]
    }
  }
}
```

*Note: Ensure the barbatos-bridge is running (listening on port 8080) before using the MCP tools.*