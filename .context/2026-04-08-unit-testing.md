# Unit Testing for barbatos CLI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish a robust unit testing foundation targeting ~80% coverage for pure business logic and network mapping, specifically ignoring POSIX terminal dependencies.

**Architecture:** 
- Configure `kotlin.test` and `ktor-client-mock` in `build.gradle.kts` for `macosArm64Test`.
- Mirror pure logic tests in `src/nativeTest/kotlin`.
- Focus tests on `CommandExecutor` (sort/priority algorithms), `CommandRegistry` (search logic), `ListRenderer` (pagination algorithms) and specific string-parsing pure functions extracted from `Renderer.kt`.
- Mock RpcClient via dependency injection or a fake engine to validate its network mapping and error resiliency.

**Tech Stack:** Kotlin Multiplatform (`kotlin.test`), Ktor Client Mock.

---

### Task 1: Setup Test Environment

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add test dependencies to Gradle configuration**
Modify the `build.gradle.kts` to add `macosArm64Test` source set with `kotlin-test` and `ktor-client-mock`.

```kotlin
        val macosArm64Test by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:3.0.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
```

- [ ] **Step 2: Commit**

```bash
git add build.gradle.kts
git commit -m "test: add kotlin-test and ktor-client-mock dependencies for macosArm64"
```

---

### Task 2: Refactor Renderer String Utils

Extract pure functions from `Renderer` into a standalone object `StringUtils` to make them easily testable.

**Files:**
- Create: `src/nativeMain/kotlin/StringUtils.kt`
- Modify: `src/nativeMain/kotlin/Renderer.kt`

- [ ] **Step 1: Create StringUtils object**
Create `src/nativeMain/kotlin/StringUtils.kt` containing `formatValue`, `splitTopLevelCommas`, `extractParams` and `extractMemberName`. Replace references to `C_BLUE` etc with standard strings if needed, or pass them in/import `Ansi`.

```kotlin
object StringUtils {
    // Paste formatValue, splitTopLevelCommas, extractParams, extractMemberName here
    // Change visibility to `internal` or `public`
}
```

- [ ] **Step 2: Update Renderer to use StringUtils**
Update `Renderer.kt` calls from `formatValue(...)` to `StringUtils.formatValue(...)` and delete the old private functions in `Renderer.kt`.

- [ ] **Step 3: Commit**

```bash
git add src/nativeMain/kotlin/StringUtils.kt src/nativeMain/kotlin/Renderer.kt
git commit -m "refactor: extract pure string processing functions to StringUtils for testing"
```

---

### Task 3: Test StringUtils

**Files:**
- Create: `src/nativeTest/kotlin/StringUtilsTest.kt`

- [ ] **Step 1: Write failing tests for StringUtils**
```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals

class StringUtilsTest {
    @Test
    fun testExtractParams() {
        val sig = "public static void com.pkg.Class.methodName(int, java.lang.String)"
        assertEquals("int, String", StringUtils.extractParams(sig))
    }

    @Test
    fun testExtractMemberName() {
        val sig = "public static void com.pkg.Class.methodName(int, java.lang.String)"
        assertEquals("methodName", StringUtils.extractMemberName(sig))
    }

    @Test
    fun testSplitTopLevelCommas() {
        val input = "a=1, b=Test{x=1, y=2}, c=3"
        val parts = StringUtils.splitTopLevelCommas(input)
        assertEquals(3, parts.size)
        assertEquals("b=Test{x=1, y=2}", parts[1])
    }
}
```

- [ ] **Step 2: Run tests**
Run: `./gradlew macosArm64Test`
Expected: PASS (if extraction logic in `StringUtils` is correct). Fix any import issues.

- [ ] **Step 3: Commit**

```bash
git add src/nativeTest/kotlin/StringUtilsTest.kt
git commit -m "test: add tests for StringUtils"
```

---

### Task 4: Test CommandExecutor (Sort Classes Logic)

**Files:**
- Create: `src/nativeTest/kotlin/CommandExecutorTest.kt`

- [ ] **Step 1: Write tests for `sortClasses` priority logic**
```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandExecutorTest {
    @Test
    fun testSortClassesPriority() {
        val classes = listOf(
            "[B", 
            "com.example.app.MainActivity", 
            "android.app.Activity", 
            "com.example.Other", 
            "[Ljava.lang.String;"
        )
        val sorted = CommandExecutor.sortClasses(classes, "com.example.app", "")
        
        // Priorities: app package (3) > first two (2) > others (1) > arrays (-1)
        assertEquals("com.example.app.MainActivity", sorted[0])
        assertEquals("com.example.Other", sorted[1])
        assertEquals("android.app.Activity", sorted[2])
        // Arrays at the bottom
        assert(sorted[3].startsWith("["))
        assert(sorted[4].startsWith("["))
    }

    @Test
    fun testSortClassesWithSearchQuery() {
        val classes = listOf("com.example.UiState", "com.example.BiometryUiState", "com.example.UiStateImpl")
        val sorted = CommandExecutor.sortClasses(classes, "com.example", "UiState")
        
        // Exact startsWith in class name gets massive +10 priority
        assertEquals("com.example.UiState", sorted[0])
        assertEquals("com.example.UiStateImpl", sorted[1])
        assertEquals("com.example.BiometryUiState", sorted[2])
    }
}
```

- [ ] **Step 2: Run tests**
Run: `./gradlew macosArm64Test`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/nativeTest/kotlin/CommandExecutorTest.kt
git commit -m "test: add tests for CommandExecutor sortClasses priority logic"
```

---

### Task 5: Test CommandRegistry

**Files:**
- Create: `src/nativeTest/kotlin/CommandRegistryTest.kt`

- [ ] **Step 1: Write tests for autocomplete searching**
```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandRegistryTest {
    @Test
    fun testSearchPrefixPriority() {
        // E.g., searching "e" should return "exit" before others containing "e" (like "clear")
        val results = CommandRegistry.search("e")
        assertEquals("exit", results[0].name)
    }

    @Test
    fun testSearchEmpty() {
        val results = CommandRegistry.search("")
        assertEquals(0, results.size)
    }
}
```

- [ ] **Step 2: Run tests**
Run: `./gradlew macosArm64Test`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/nativeTest/kotlin/CommandRegistryTest.kt
git commit -m "test: add tests for CommandRegistry autocomplete logic"
```

---

### Task 6: Test ListRenderer

**Files:**
- Create: `src/nativeTest/kotlin/ListRendererTest.kt`

- [ ] **Step 1: Write tests for viewport pagination logic**
```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals

class ListRendererTest {
    @Test
    fun testComputeViewport() {
        // 10 items, selected index 0, max 5 items visible
        val (start1, end1) = ListRenderer.computeViewport(10, 0, 5)
        assertEquals(0, start1)
        assertEquals(5, end1)

        // Select item 4 (should shift viewport down)
        val (start2, end2) = ListRenderer.computeViewport(10, 4, 5)
        // Adjust these assertions based on your exact computeViewport math!
        // Usually, selected item should be visible.
        assert(4 in start2 until end2)
    }
}
```

- [ ] **Step 2: Run tests**
Run: `./gradlew macosArm64Test`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/nativeTest/kotlin/ListRendererTest.kt
git commit -m "test: add tests for ListRenderer computeViewport logic"
```

---

### Task 7: Setup RpcClient DI for Mocking

To test `RpcClient` without hitting a real server, we need to allow injecting the Ktor `HttpClient`.

**Files:**
- Modify: `src/nativeMain/kotlin/RpcClient.kt`

- [ ] **Step 1: Refactor RpcClient to accept an HttpClient instance**
```kotlin
// Change object RpcClient to class RpcClient(val client: HttpClient)
// Create a companion object with a default instance
class RpcClientImpl(val client: HttpClient = HttpClient(Darwin) { ... }) {
    // All current RpcClient methods
}
object RpcClient : RpcClientImpl()
```
*(Alternatively, just leave it as an object and replace `val client = HttpClient(...)` with `var client = HttpClient(...)` so tests can overwrite it).*

- [ ] **Step 2: Create RpcClientTest using MockEngine**
- Create `src/nativeTest/kotlin/RpcClientTest.kt`

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RpcClientTest {
    @Test
    fun testPingSuccess() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond("{\"jsonrpc\":\"2.0\",\"result\":[\"pong\"]}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }
        
        // Assuming RpcClient allows overriding the client
        val oldClient = RpcClient.client
        RpcClient.client = mockClient
        
        val result = RpcClient.ping()
        assertEquals(true, result)
        
        RpcClient.client = oldClient
    }
}
```

- [ ] **Step 3: Run tests**
Run: `./gradlew macosArm64Test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/nativeMain/kotlin/RpcClient.kt src/nativeTest/kotlin/RpcClientTest.kt
git commit -m "test: refactor RpcClient for testability and add ping mock test"
```
