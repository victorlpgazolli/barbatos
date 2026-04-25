# Barbatos MCP Server

This MCP server acts as an adapter connecting AI agents and LLMs (like Claude Desktop or Cursor) to the **Barbatos** interactive debugger via its local JSON-RPC API.

## AI-Driven Debugging

By connecting Barbatos to your AI agent via MCP, you empower the agent to autonomously interact with the Android and iOS runtime. The agent can use the provided tools to:
*   Search for specific classes and instances.
*   Read live properties and variables from the heap.
*   Hook methods to monitor arguments and return values.
*   Modify variables in real-time to test hypotheses or fix states without recompiling.

### Example Prompts:
*   "Locate the active `PaymentSession` instance and check the `errorCode` field to understand why the transaction is failing."
*   "Hook the `NotificationManager.show` method to intercept and log the raw content of incoming push notifications."
*   "List all live instances of `NetworkError`, check their `message` field, and identify recurring failure patterns."
*   "Analyze the foreground app on the connected iOS device and list its classes."

## Installation

Run the following command in your terminal to install the Barbatos MCP server:

```bash
curl -sSL https://barbatos.victorlpgazolli.dev/install.sh | bash
```



<details><summary>Alternatively, you can build and install it manually:</summary>

```bash
git clone git@github.com:victorlpgazolli/barbatos.git
cd barbatos/mcp_server

python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

python3 -m PyInstaller mcp.spec
# you will find the executable in dist/barbatos-mcp, move it to ~/.local/bin
mv dist/barbatos-mcp ~/.local/bin/
# then follow the MCP Server Setup instructions below
```

</details>

## Configuration

**Standard config** works in most of the tools:

```json
{
  "mcpServers": {
    "barbatos-debugger": {
      "command": "barbatos-mcp"
    }
  }
}
```

*Note: Ensure the barbatos-bridge is running (listening on port 8080) before using the MCP tools.*

### Config examples for specific tools:

<details>
<summary>Amp</summary>

Add via the Amp VS Code extension settings screen or by updating your `settings.json` file:

```json
"amp.mcpServers": {
  "barbatos-debugger": {
    "command": "barbatos-mcp"
  }
}
```

**Amp CLI:**

Run the following command in your terminal:

```bash
amp mcp add barbatos-debugger -- barbatos-mcp
```

</details>

<details>
<summary>Cline</summary>

To setup Cline, just add the json above to your MCP settings file.

</details>

<details>
<summary>Claude Code</summary>

Use the Claude Code CLI to add the Barbatos MCP server:

```bash
claude mcp add barbatos-debugger -- barbatos-mcp
```
</details>

<details>
<summary>Claude Desktop</summary>

Follow the [MCP install guide](https://modelcontextprotocol.io/quickstart/user), use json configuration above.

</details>

<details>
<summary>Codex</summary>

Use the Codex CLI to add the Barbatos MCP server:

```bash
codex mcp add barbatos-debugger barbatos-mcp
```

Alternatively, create or edit the configuration file `~/.codex/config.toml` and add:

```toml
[mcp_servers.barbatos-debugger]
command = "barbatos-mcp"
```

</details>

<details>
<summary>Copilot</summary>

Use the Copilot CLI to interactively add the Barbatos MCP server:

```text
/mcp add
```

You can edit the configuration file `~/.copilot/mcp-config.json` and add:

```json
{
  "mcpServers": {
    "barbatos-debugger": {
      "type": "local",
      "command": "barbatos-mcp",
      "tools": ["*"]
    }
  }
}
```

</details>

<details>
<summary>Cursor</summary>

Go to `Cursor Settings` -> `MCP` -> `Add new MCP Server`. Name to your liking, use `command` type with the command `barbatos-mcp`.

</details>

<details>
<summary>Gemini CLI</summary>

Use the Gemini CLI to add the Barbatos MCP server:

```bash
gemini mcp add barbatos-debugger barbatos-mcp
```

</details>

<details>
<summary>Goose</summary>

Go to `Advanced settings` -> `Extensions` -> `Add custom extension`. Name to your liking, use type `STDIO`, and set the `command` to `barbatos-mcp`.

</details>

<details>
<summary>Kiro</summary>

Follow the MCP Servers [documentation](https://kiro.dev/docs/mcp/). For example in `.kiro/settings/mcp.json`:

```json
{
  "mcpServers": {
    "barbatos-debugger": {
      "command": "barbatos-mcp"
    }
  }
}
```

</details>

<details>
<summary>opencode</summary>

Follow the MCP Servers documentation. For example in `~/.config/opencode/opencode.json`:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "barbatos-debugger": {
      "type": "local",
      "command": ["barbatos-mcp"],
      "enabled": true
    }
  }
}
```

</details>

<details>
<summary>Qodo Gen</summary>

Open Qodo Gen chat panel in VSCode or IntelliJ → Connect more tools → + Add new MCP → Paste the standard config above.

Click <code>Save</code>.

</details>

### OpenAPI Specification

Barbatos exposes an API via the bridge, allowing you to interact with the debugger programmatically. 

[View API Docs >](https://barbatos.victorlpgazolli.dev/openapi)