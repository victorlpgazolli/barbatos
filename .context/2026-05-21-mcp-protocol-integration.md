# MCP Protocol Integration Implementation Plan (v4 - Final & Build-Safe)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the Model Context Protocol (MCP) into the barbatos bridge, ensuring a shielded Stdio transport, proper JSON-RPC error codes, and a scalable tool registry.

**Architecture:** 
1. **Separation of Concerns:** Dedicated `McpHandler` and `McpTool` interface.
2. **Standard Compliance:** Accurate JSON-RPC 2.0 error codes (-32601 for missing methods).
3. **Shielded Stdio:** Redirecting all non-protocol logs to `stderr`.
4. **Dynamic Registry:** Frida tools implemented as independent classes.

**Tech Stack:** Kotlin Multiplatform, Kotlinx Serialization, POSIX Native I/O.

---

### Task 1: Flexible MCP Models and Tool Interface

**Files:**
- Create: `src/commonMain/kotlin/rpc/McpModels.kt`
- Create: `src/commonMain/kotlin/rpc/McpTool.kt`

- [ ] **Step 1: Define flexible MCP schemas**

```kotlin
// src/commonMain/kotlin/rpc/McpModels.kt
package rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class McpInitializeParams(
    val protocolVersion: String,
    val capabilities: JsonObject,
    val clientInfo: McpClientInfo
)

@Serializable
data class McpClientInfo(val name: String, val version: String)

@Serializable
data class McpInitializeResult(
    val protocolVersion: String,
    val capabilities: JsonObject,
    val serverInfo: McpServerInfo
)

@Serializable
data class McpServerInfo(val name: String, val version: String)

@Serializable
data class McpToolDef(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

@Serializable
data class McpToolsListResult(val tools: List<McpToolDef>)

@Serializable
data class McpCallToolParams(
    val name: String,
    val arguments: JsonObject? = null
)

@Serializable
data class McpContent(
    val type: String, // "text", "image", "resource"
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)

@Serializable
data class McpCallToolResult(
    val content: List<McpContent>,
    val isError: Boolean = false
)
```

- [ ] **Step 2: Create `McpTool` interface**

```kotlin
// src/commonMain/kotlin/rpc/McpTool.kt
package rpc

import kotlinx.serialization.json.JsonObject

interface McpTool {
    val name: String
    val description: String
    val inputSchema: JsonObject
    fun execute(args: JsonObject?): McpCallToolResult
}
```

- [ ] **Step 3: Commit**

```bash
git add src/commonMain/kotlin/rpc/McpModels.kt src/commonMain/kotlin/rpc/McpTool.kt
git commit -m "feat(mcp): add mcp models and tool interface"
```

### Task 2: Implement McpHandler with Protocol Compliance

**Files:**
- Create: `src/commonMain/kotlin/rpc/McpHandler.kt`
- Create: `src/commonTest/kotlin/rpc/McpHandlerTest.kt`

- [ ] **Step 1: Write failing test for initialization and missing methods**

```kotlin
// src/commonTest/kotlin/rpc/McpHandlerTest.kt
package rpc

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.serialization.json.*

class McpHandlerTest {
    @Test
    fun testMcpInitialize() {
        val handler = McpHandler(emptyList())
        val request = """{"jsonrpc": "2.0", "method": "initialize", "params": {"protocolVersion": "2024-11-05", "capabilities": {}, "clientInfo": {"name": "test", "version": "1"}}, "id": 1}"""
        val response = handler.handle(request)
        assertTrue(response.contains("protocolVersion"), "Should return MCP initialization")
    }

    @Test
    fun testMethodNotFound() {
        val handler = McpHandler(emptyList())
        val request = """{"jsonrpc": "2.0", "method": "invalidMethod", "id": 1}"""
        val response = handler.handle(request)
        // Check for specific JSON-RPC error code -32601
        assertTrue(response.contains("-32601"), "Should return Method not found code (-32601)")
    }
}
```

- [ ] **Step 2: Implement `McpHandler` with correct error codes**

```kotlin
// src/commonMain/kotlin/rpc/McpHandler.kt
package rpc

import kotlinx.serialization.json.*

/**
 * Custom exception to trigger the -32601 (Method not found) JSON-RPC error.
 */
class McpMethodNotFoundException(message: String) : Exception(message)

class McpHandler(private val tools: List<McpTool>) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun handle(requestJson: String): String {
        val req = try {
            json.decodeFromString<RpcRequest>(requestJson)
        } catch (e: Exception) {
            return json.encodeToString(RpcErrorResponse(error = RpcError(-32700, "Parse error"), id = null))
        }

        return try {
            val result = processMethod(req.method, req.params)
            json.encodeToString(RpcResponse(result = result, id = req.id))
        } catch (e: McpMethodNotFoundException) {
            json.encodeToString(RpcErrorResponse(error = RpcError(-32601, e.message ?: "Method not found"), id = req.id))
        } catch (e: Exception) {
            json.encodeToString(RpcErrorResponse(error = RpcError(-32603, e.message ?: "Internal error"), id = req.id))
        }
    }

    private fun processMethod(method: String, params: JsonElement?): JsonElement {
        return when (method) {
            "initialize" -> json.encodeToJsonElement(McpInitializeResult(
                protocolVersion = "2024-11-05",
                capabilities = buildJsonObject { putJsonObject("tools") {} },
                serverInfo = McpServerInfo("barbatos-bridge", "1.0.0")
            ))
            "notifications/initialized" -> JsonNull
            "tools/list" -> json.encodeToJsonElement(McpToolsListResult(
                tools.map { McpToolDef(it.name, it.description, it.inputSchema) }
            ))
            "tools/call" -> {
                val p = params?.let { json.decodeFromJsonElement<McpCallToolParams>(it) }
                    ?: return json.encodeToJsonElement(McpCallToolResult(listOf(McpContent("text", "Missing params")), true))
                
                val tool = tools.find { it.name == p.name }
                    ?: return json.encodeToJsonElement(McpCallToolResult(listOf(McpContent("text", "Tool not found")), true))
                
                try {
                    json.encodeToJsonElement(tool.execute(p.arguments))
                } catch (e: Exception) {
                    json.encodeToJsonElement(McpCallToolResult(listOf(McpContent("text", "Execution error: ${e.message}")), true))
                }
            }
            else -> throw McpMethodNotFoundException("Method $method not found")
        }
    }
}
```

- [ ] **Step 3: Run test and commit**

```bash
./gradlew commonTest
git add src/commonMain/kotlin/rpc/McpHandler.kt src/commonTest/kotlin/rpc/McpHandlerTest.kt
git commit -m "feat(mcp): implement compliant McpHandler with error code -32601"
```

### Task 3: Implement Initial Frida Tools

**Files:**
- Create: `src/commonMain/kotlin/rpc/tools/ListClassesTool.kt`
- Create: `src/commonMain/kotlin/rpc/tools/InspectClassTool.kt`

- [ ] **Step 1: Implement `ListClassesTool`**

```kotlin
// src/commonMain/kotlin/rpc/tools/ListClassesTool.kt
package rpc.tools

import bridge.FridaBridge
import kotlinx.serialization.json.*
import rpc.*

class ListClassesTool(private val bridge: FridaBridge) : McpTool {
    override val name = "list_classes"
    override val description = "List loaded Java/ObjC classes in the target process."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("search_param") { put("type", "string"); put("description", "Filter classes by name") }
        }
    }

    override fun execute(args: JsonObject?): McpCallToolResult {
        val search = args?.get("search_param")?.jsonPrimitive?.content ?: ""
        val classes = bridge.listClasses(search, "", 0, 200)
        return McpCallToolResult(listOf(McpContent("text", classes.joinToString("\n"))))
    }
}
```

- [ ] **Step 2: Implement `InspectClassTool`**

```kotlin
// src/commonMain/kotlin/rpc/tools/InspectClassTool.kt
package rpc.tools

import bridge.FridaBridge
import kotlinx.serialization.json.*
import rpc.*

class InspectClassTool(private val bridge: FridaBridge) : McpTool {
    override val name = "inspect_class"
    override val description = "Inspect fields and methods of a specific class."
    override val inputSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("className") { put("type", "string"); put("description", "Full class name") }
        }
        putJsonArray("required") { add("className") }
    }

    override fun execute(args: JsonObject?): McpCallToolResult {
        val className = args?.get("className")?.jsonPrimitive?.content ?: return McpCallToolResult(listOf(McpContent("text", "Missing className")), true)
        val res = bridge.inspectClass(className)
        val output = """
            Methods:
            ${res.methods.joinToString("\n")}
            
            Static Attributes:
            ${res.staticAttributes.joinToString("\n")}
            
            Instance Attributes:
            ${res.instanceAttributes.joinToString("\n")}
        """.trimIndent()
        return McpCallToolResult(listOf(McpContent("text", output)))
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/commonMain/kotlin/rpc/tools/
git commit -m "feat(mcp): add list_classes and inspect_class tools"
```

### Task 4: Shielded Stdio Loop and Logging Redirection

**Files:**
- Modify: `src/unixMain/kotlin/Main.kt`

- [ ] **Step 1: Implement the robust Stdio loop in `Main.kt`**

```kotlin
// src/unixMain/kotlin/Main.kt
import server.startServer
import bridge.NativeFridaBridge
import rpc.*
import rpc.tools.*
import platform.posix.fprintf
import platform.posix.stderr

fun main(args: Array<String>) {
    val bridge = NativeFridaBridge()
    
    if (args.contains("mcp") || args.contains("--mcp")) {
        // IMPORTANT: Redirect all system logs to stderr to keep stdout clean for JSON-RPC
        fprintf(stderr, "Starting Barbatos MCP Server (Stdio Mode)...\n")
        fprintf(stderr, "Note: This transport is synchronous/blocking in v1.\n")
        
        // Register tools - Ensure Task 3 is fully completed before building Task 4
        val tools = listOf(
            ListClassesTool(bridge),
            InspectClassTool(bridge)
        )
        val mcpHandler = McpHandler(tools)
        
        while (true) {
            // Using readlnOrNull (modern Kotlin Native equivalent)
            val line = readlnOrNull() ?: break
            if (line.isBlank()) continue
            
            try {
                val response = mcpHandler.handle(line)
                // Guaranteed that only JSON goes to stdout
                println(response)
            } catch (e: Exception) {
                // Last line of defense for fatal crashes
                println("""{"jsonrpc": "2.0", "error": {"code": -32603, "message": "Fatal: ${e.message}"}, "id": null}""")
            }
        }
    } else {
        println("Starting KMP Bridge (HTTP Mode) on port 8080...")
        startServer(bridge)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/unixMain/kotlin/Main.kt
git commit -m "feat(mcp): add shielded stdio loop with stderr logging"
```
