# Bridge KMP Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the Python Frida bridge to Kotlin Multiplatform (KMP), exposing all endpoints from `web/openapi.yaml` with identical behavior using a TDD approach with Frida mocks.

**Architecture:** A Ktor Server (`cio`) handles the HTTP endpoints (`/ping` and `/rpc`). A generic `FridaBridge` interface defines the core operations. We use `MockFridaBridge` during tests to validate the RPC logic without needing an actual Android/iOS device. The `/rpc` endpoint delegates to an `RpcHandler` which parses requests, routes them to the `FridaBridge` implementation, and returns `RpcResponse`.

**Tech Stack:** Kotlin Multiplatform (Common/Native), Ktor Server (CIO), Kotlinx Serialization.

---

### Task 1: Setup Ktor Server & Ping Endpoint

**Files:**
- Create: `src/commonMain/kotlin/server/Server.kt`
- Create: `src/commonTest/kotlin/server/ServerTest.kt`

- [ ] **Step 1: Write the failing test for `/ping`**

```kotlin
// src/commonTest/kotlin/server/ServerTest.kt
package server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerTest {
    @Test
    fun testPingEndpoint() = testApplication {
        application {
            module()
        }
        val response = client.get("/ping")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status": "pong"}""", response.bodyAsText())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew cleanTest linuxX64Test --tests "server.ServerTest"`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```kotlin
// src/commonMain/kotlin/server/Server.kt
package server

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.http.ContentType

fun Application.module() {
    routing {
        get("/ping") {
            call.respondText("""{"status": "pong"}""", ContentType.Application.Json)
        }
    }
}

fun startServer() {
    embeddedServer(CIO, port = 8080, host = "127.0.0.1", module = Application::module).start(wait = true)
}
```

```kotlin
// Modify src/commonMain/kotlin/Main.kt
import server.startServer

fun main() {
    println("Starting KMP Bridge on port 8080...")
    startServer()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew cleanTest linuxX64Test --tests "server.ServerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/commonMain/kotlin/server/Server.kt src/commonTest/kotlin/server/ServerTest.kt src/commonMain/kotlin/Main.kt
git commit -m "feat: setup ktor server and ping endpoint"
```

### Task 2: Setup RPC Handler, Mocks, and Base Models

**Files:**
- Create: `src/commonMain/kotlin/bridge/FridaBridge.kt`
- Create: `src/commonMain/kotlin/bridge/MockFridaBridge.kt`
- Create: `src/commonMain/kotlin/rpc/RpcModels.kt`
- Create: `src/commonMain/kotlin/rpc/RpcHandler.kt`
- Create: `src/commonTest/kotlin/rpc/RpcHandlerTest.kt`

- [ ] **Step 1: Write the failing test for RPC method not found**

```kotlin
// src/commonTest/kotlin/rpc/RpcHandlerTest.kt
package rpc

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import server.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RpcHandlerTest {
    @Test
    fun testRpcMethodNotFound() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "unknownMethod", "id": 1}""")
        }
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Method unknownMethod not found"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew cleanTest linuxX64Test --tests "rpc.RpcHandlerTest"`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```kotlin
// src/commonMain/kotlin/bridge/FridaBridge.kt
package bridge

interface FridaBridge {
    // Methods will be added here as we migrate endpoints
}
```

```kotlin
// src/commonMain/kotlin/bridge/MockFridaBridge.kt
package bridge

class MockFridaBridge : FridaBridge {
    // Mock implementations will go here
}
```

```kotlin
// src/commonMain/kotlin/rpc/RpcModels.kt
package rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RpcRequest(val jsonrpc: String, val method: String, val params: JsonElement? = null, val id: Int? = null)

@Serializable
data class RpcResponse(val jsonrpc: String = "2.0", val result: JsonElement? = null, val id: Int? = null)

@Serializable
data class RpcError(val status: String, val error_message: String)

@Serializable
data class RpcErrorResponse(val jsonrpc: String = "2.0", val error: RpcError, val id: Int? = null)
```

```kotlin
// src/commonMain/kotlin/rpc/RpcHandler.kt
package rpc

import bridge.FridaBridge
import kotlinx.serialization.json.*

class RpcHandler(private val bridge: FridaBridge) {
    val jsonParser = Json { ignoreUnknownKeys = true }

    fun handle(requestJson: String): String {
        val req = try {
            jsonParser.decodeFromString<RpcRequest>(requestJson)
        } catch (e: Exception) {
            return jsonParser.encodeToString(RpcErrorResponse.serializer(), RpcErrorResponse(error = RpcError("parse_error", "Invalid JSON")))
        }

        return try {
            val result = processMethod(req.method, req.params)
            jsonParser.encodeToString(RpcResponse.serializer(), RpcResponse(result = result, id = req.id))
        } catch (e: Exception) {
            jsonParser.encodeToString(RpcErrorResponse.serializer(), RpcErrorResponse(error = RpcError("unknown_error", e.message ?: "Error"), id = req.id))
        }
    }

    private fun processMethod(method: String, params: JsonElement?): JsonElement {
        return when (method) {
            else -> throw Exception("Method $method not found")
        }
    }
}
```

```kotlin
// Modify src/commonMain/kotlin/server/Server.kt
// add the POST "/rpc" route
package server

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import rpc.RpcHandler
import bridge.MockFridaBridge

// Provide an instance of the bridge. In tests this will be MockFridaBridge.
// In a real run, it would be the real NativeFridaBridge (to be implemented later).
val globalBridge = MockFridaBridge()

fun Application.module() {
    val rpcHandler = RpcHandler(globalBridge)
    routing {
        get("/ping") {
            call.respondText("""{"status": "pong"}""", ContentType.Application.Json)
        }
        post("/rpc") {
            val body = call.receiveText()
            val responseText = rpcHandler.handle(body)
            if (responseText.contains("\"error\":")) {
                call.respondText(responseText, ContentType.Application.Json, HttpStatusCode.InternalServerError)
            } else {
                call.respondText(responseText, ContentType.Application.Json, HttpStatusCode.OK)
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew cleanTest linuxX64Test --tests "rpc.RpcHandlerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/commonMain/kotlin/ bridge/ rpc/
git commit -m "feat: rpc base handler and models"
```

### Task 3: Migrate Class Analysis Endpoints (`listClasses`, `countInstances`)

**Files:**
- Modify: `src/commonMain/kotlin/bridge/FridaBridge.kt`
- Modify: `src/commonMain/kotlin/bridge/MockFridaBridge.kt`
- Modify: `src/commonMain/kotlin/rpc/RpcHandler.kt`
- Modify: `src/commonTest/kotlin/rpc/RpcHandlerTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// Add to src/commonTest/kotlin/rpc/RpcHandlerTest.kt
    @Test
    fun testListClasses() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "listClasses", "params": {"search_param": "MainActivity", "app_package": "com.example", "offset": 0, "limit": 10}, "id": 2}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("com.example.MainActivity"))
    }

    @Test
    fun testCountInstances() = testApplication {
        application { module() }
        val response = client.post("/rpc") {
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc": "2.0", "method": "countInstances", "params": {"className": "com.example.MainActivity"}, "id": 3}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"result\":5"))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew cleanTest linuxX64Test --tests "rpc.RpcHandlerTest.testListClasses"`
Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```kotlin
// Modify src/commonMain/kotlin/bridge/FridaBridge.kt
    fun listClasses(searchParam: String, appPackage: String, offset: Int, limit: Int): List<String>
    fun countInstances(className: String): Int
```

```kotlin
// Modify src/commonMain/kotlin/bridge/MockFridaBridge.kt
    override fun listClasses(searchParam: String, appPackage: String, offset: Int, limit: Int): List<String> {
        val all = listOf("com.example.MainActivity", "java.lang.String")
        return all.filter { it.contains(searchParam, ignoreCase = true) }
    }

    override fun countInstances(className: String): Int {
        return if (className == "com.example.MainActivity") 5 else 0
    }
```

```kotlin
// Add Serializable param classes to src/commonMain/kotlin/rpc/RpcModels.kt
@Serializable data class ListClassesParams(val search_param: String = "", val app_package: String = "", val offset: Int = 0, val limit: Int = 200)
@Serializable data class CountInstancesParams(val className: String)
```

```kotlin
// Modify processMethod in src/commonMain/kotlin/rpc/RpcHandler.kt
    private fun processMethod(method: String, params: JsonElement?): JsonElement {
        return when (method) {
            "listClasses" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<ListClassesParams>(it) } ?: ListClassesParams()
                val res = bridge.listClasses(p.search_param, p.app_package, p.offset, p.limit)
                jsonParser.encodeToJsonElement(res)
            }
            "countInstances" -> {
                val p = params?.let { jsonParser.decodeFromJsonElement<CountInstancesParams>(it) } ?: throw Exception("Missing params")
                val res = bridge.countInstances(p.className)
                jsonParser.encodeToJsonElement(res)
            }
            else -> throw Exception("Method $method not found")
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew cleanTest linuxX64Test --tests "rpc.RpcHandlerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: migrate listClasses and countInstances endpoints"
```

### Task 4: Migrate Instance Inspection (`inspectClass`, `listInstances`, `inspectInstance`)

- [ ] **Step 1: Write the failing tests**
- [ ] **Step 2: Run tests to verify they fail**
- [ ] **Step 3: Write minimal implementation in `MockFridaBridge`, `RpcModels`, and `RpcHandler`**
- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit**

### Task 5: Migrate Action Endpoints (`setFieldValue`, `hookMethod`, `getHookEvents`, `setMethodImplementation`, `runOnce`)

- [ ] **Step 1: Write the failing tests**
- [ ] **Step 2: Run tests to verify they fail**
- [ ] **Step 3: Write minimal implementation in `MockFridaBridge`, `RpcModels`, and `RpcHandler`**
- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit**

### Task 6: Migrate Environment/Injection Endpoints (`prepareEnvironment`, `checkOrPushGadget`, `resetInjection`, `injectGadgetFromScratch`, `injectJdwp`, `healthCheck`)

- [ ] **Step 1: Write the failing tests**
- [ ] **Step 2: Run tests to verify they fail**
- [ ] **Step 3: Write minimal implementation in `MockFridaBridge`, `RpcModels`, and `RpcHandler`**
- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit**

### Task 7: Migrate iOS Specific Endpoints (`patchAndInstallIosApp`, `checkIosJailbreakStatus`, `injectJailbrokenIos`, `checkIosDeployStatus`)

- [ ] **Step 1: Write the failing tests**
- [ ] **Step 2: Run tests to verify they fail**
- [ ] **Step 3: Write minimal implementation in `MockFridaBridge`, `RpcModels`, and `RpcHandler`**
- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit**

### Task 8: Final Review & Release Build

- [ ] **Step 1: Run all tests locally**
Run: `./gradlew clean check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Test release build generation (native)**
Run: `./gradlew linkReleaseExecutableMacosArm64` or equivalent depending on host OS to verify there are no compilation errors in release mode.
Expected: BUILD SUCCESSFUL