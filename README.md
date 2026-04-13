# Barbatos

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-Native-7f52ff?style=flat-square&logo=kotlin" alt="Kotlin Native">
  <img src="https://img.shields.io/badge/Frida-16.x-ff1e56?style=flat-square" alt="Frida">
  <img src="https://img.shields.io/badge/Platform-macOS%20%7C%20Linux%20%7C%20WSL-000000?style=flat-square" alt="Platforms">
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" alt="License">
</p>

<p align="center">
  <b>Android Runtime Debugger & MCP Server.</b><br>
  <i>"Understanding the language of systems, revealing hidden states, and reconciling the gap between developer and machine."</i>
</p>

<div align="center">
  <p>IMAGEM_AQUI_1</p>
</div>

---

## Main Use Cases

*   **Terminal-First Experience:** ready to use in any terminal environment (local, SSH, WSL) without IDE plugins or complex setup.
*   **Simple App Exploration:** quickly list classes, inspect objects, and understand app structure.
*   **Real-time App Debugging:** modify field values on the fly and hook methods to see live execution flow.
*   **AI-Assisted Debugging:** Use the built-in MCP Server to let LLMs (Claude/Gemini/Cursor) debug your app autonomously.
* (soon) **Intercept Method Calls:** Modify return values or arguments of any method.

---

## Main Features

*   **Class Discovery:** Real-time enumeration of loaded Java/Kotlin classes with package filtering.
*   **Deep Inspection:** Recursive traversal of object hierarchies (Fields, Maps, Collections, Arrays).
*   **Zero-effort Integration:** No IDE plugins or dependency installs — just run the binary and connect.
*   **Method Hooking:** Intercept execution flow, inspect arguments, and capture return values.
*   **Live Field Editing:** Modify primitive field values (String, Int, Boolean) in real-time to test hypotheses.
*   **MCP Server:** Native Model Context Protocol support to connect your debugger to AI agents.

---

## AI-Driven Debugging (MCP Server)

Barbatos provides native support for the Model Context Protocol (MCP). By connecting an AI agent to the Barbatos MCP Server, your LLM becomes an autonomous debugging assistant capable of direct runtime interaction. 

### Example Agent Capabilities (experimental):

*   **State Exploration:** "Locate the active `LoginState` instance and tell me the value of its `errorCode` field."
*   **DI Troubleshooting:** "Search for `MyUseCase` instance in `MyViewModel` and see if it is the same one in `MyOtherViewModel`."
*   **Method Hooking:** "Tell me the parameters passed to the `MyViewModel.fetchData` method every time it is called."
*   **Live Edit:** "Change the `isPremium` boolean on the active `UserProfile` instance to `true`."
*   **Root Cause Analysis:** "List all instances of `ApiError`, check their `message` field, and identify recurring failure patterns."
* (soon) **Mock Behavior:** "Intercept the `fetchData` method in `MyViewModel` and modify its return to throw an exception when the `isActive` field is `false`."


---

## Architecture

Barbatos uses a multi-stage pipeline for reliable communication:

```mermaid
graph TD
    A[Native TUI] -->|Ktor/HTTP| C[Python Bridge]
    B[MCP Client] -->|JSON-RPC| C
    C -->|Frida RPC| D[Frida JS Agent]
    D -->|Java Bridge| E[Android Runtime]
    E -->|ART| F[Target App]
```

1.  **Native TUI**: Standalone Kotlin Native binary for a deterministic terminal experience.
2.  **MCP Client**: Integration with AI agents (Claude, Cursor) via Model Context Protocol.
3.  **Python Bridge**: Mediator exposing a standardized JSON-RPC interface.
4.  **Frida Injection**: JS agent injected into the process for runtime interaction.

---

## Available MCP Tools

<details>
<summary><b>View All 8 Debugging Tools</b></summary>

| Tool | Description |
| :--- | :--- |
| `barbatos_list_classes` | Retrieves loaded Java classes with optional search and package filtering. |
| `barbatos_inspect_class` | Returns all static/instance fields and methods of a specific class. |
| `barbatos_count_instances` | Counts live instances of a class on the heap. |
| `barbatos_list_instances` | Returns handles/IDs of live instances for a given class. |
| `barbatos_inspect_instance` | Recursively explores an instance's fields and values. |
| `barbatos_set_field_value` | Modifies a primitive field (String, Int, Boolean, etc.) in real-time. |
| `barbatos_hook_method` | Intercepts method calls and logs arguments/returns. |
| `barbatos_get_hook_events` | Retrieves the latest method interception events collected by the agent. |

</details>

---

## Installation

### **Quick Install (Recommended)**
Run the following command to automatically detect architecture and setup environment:

```bash
curl -sSL https://barbatos.victorlpgazolli.dev/install.sh | bash
```

### **MCP Server Setup**
To use `barbatos` with **Claude Desktop**, add this to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "barbatos-debugger": {
      "command": "python3",
      "args": ["/path/to/barbatos/mcp_server/server.py"]
    }
  }
}
```