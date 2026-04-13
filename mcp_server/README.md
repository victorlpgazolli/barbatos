# barbatos MCP Server

This MCP server acts as an adapter connecting LLMs (like Claude Desktop or Cursor) to the `barbatos` interactive debugger via its local JSON-RPC API.

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

*Note: Ensure the barbatos TUI or `bridge.py` is running (listening on port 8080) before using the MCP tools.*
