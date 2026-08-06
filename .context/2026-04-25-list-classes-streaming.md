# Stream listClasses Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> Note: Do not use git worktrees for this plan, run it directly.

**Goal:** Migrate `listClasses` to a streaming architecture across the Kotlin TUI, Python Bridge, and Frida JS Agent to solve iOS timeout issues and provide immediate UI feedback.

**Architecture:** 
1. The Frida JS Agent will batch discovered classes into chunks and send them back to the Python bridge using `send()`.
2. The Python bridge will expose a new HTTP endpoint `/stream/classes` that streams these chunks as newline-delimited JSON (`ndjson`).
3. The Kotlin client will read this stream line-by-line, updating the TUI's state iteratively.

**Tech Stack:** Kotlin (Ktor), Python (HTTP Server), JavaScript (Frida)

---

### Task 1: Update iOS Frida Agent

**Files:**
- Modify: `bridge/agent.objc.js`

- [ ] **Step 1: Implement `listclassesstream` method in `agent.objc.js`**

Add the `listclassesstream` method inside `rpc.exports = { ... }`. It uses a Set to prevent duplicates and batches results to reduce IPC overhead.

```javascript
    listclassesstream: function(searchParam, streamId) {
        var lowercaseSearch = searchParam ? searchParam.toLowerCase() : "";
        var batch = [];
        var batchSize = 100;
        var seen = new Set();

        function flushBatch() {
            if (batch.length > 0) {
                send({ type: "class_chunk", streamId: streamId, chunk: batch });
                batch = [];
            }
        }

        // Fetch all Objective-C classes
        for (var className in ObjC.classes) {
            if (ObjC.classes.hasOwnProperty(className)) {
                if (!lowercaseSearch || className.toLowerCase().includes(lowercaseSearch)) {
                    if (!seen.has(className)) {
                        seen.add(className);
                        batch.push(className);
                        if (batch.length >= batchSize) flushBatch();
                    }
                }
            }
        }
        
        // Fetch Swift classes
        if (Swift.available) {
            var swiftClasses = Swift.classes;
            for (var swiftName in swiftClasses) {
                if (swiftClasses.hasOwnProperty(swiftName)) {
                    if (!lowercaseSearch || swiftName.toLowerCase().includes(lowercaseSearch)) {
                        if (!seen.has(swiftName)) {
                            seen.add(swiftName);
                            batch.push(swiftName);
                            if (batch.length >= batchSize) flushBatch();
                        }
                    }
                }
            }
        }
        
        flushBatch();
        send({ type: "class_stream_end", streamId: streamId });
    },
```

- [ ] **Step 2: Stage files**

```bash
git add bridge/agent.objc.js
```

### Task 2: Update Android Frida Agent

**Files:**
- Modify: `bridge/agent.js`

- [ ] **Step 1: Implement `listclassesstream` method in `agent.js`**

Add the `listclassesstream` method inside `rpc.exports = { ... }`.

```javascript
    listclassesstream: function(searchParam, streamId) {
        var lowercaseSearch = searchParam ? searchParam.toLowerCase() : "";
        var batch = [];
        var batchSize = 100;
        var seen = new Set();

        Java.perform(function() {
            Java.enumerateLoadedClasses({
                onMatch: function(className) {
                    if (!lowercaseSearch || className.toLowerCase().includes(lowercaseSearch)) {
                        if (!seen.has(className)) {
                            seen.add(className);
                            batch.push(className);
                            if (batch.length >= batchSize) {
                                send({ type: "class_chunk", streamId: streamId, chunk: batch });
                                batch = [];
                            }
                        }
                    }
                },
                onComplete: function() {
                    if (batch.length > 0) {
                        send({ type: "class_chunk", streamId: streamId, chunk: batch });
                    }
                    send({ type: "class_stream_end", streamId: streamId });
                }
            });
        });
    },
```

- [ ] **Step 2: Stage files**

```bash
git add bridge/agent.js
```

### Task 3: Update Python Bridge Message Handler

**Files:**
- Modify: `bridge/bridge.py`

- [ ] **Step 1: Add queue dictionary and lock**
In `FridaBridge.__init__` around line 597, add:
```python
        self.stream_queues = {}
        self.stream_queues_lock = threading.Lock()
```

- [ ] **Step 2: Handle `class_chunk` and `class_stream_end` messages**
In `FridaBridge._on_message` around line 273 (inside `if message['type'] == 'send':`), handle the new payloads:

```python
    def _on_message(self, message, data):
        if message['type'] == 'send':
            payload = message.get('payload', {})
            
            # Check if payload is a dict (some logs are strings)
            if isinstance(payload, dict):
                payload_type = payload.get('type')
                
                if payload_type == "class_chunk":
                    stream_id = payload.get('streamId')
                    chunk = payload.get('chunk', [])
                    with self.stream_queues_lock:
                        if stream_id in self.stream_queues:
                            self.stream_queues[stream_id].put({"type": "chunk", "data": chunk})
                    return
                elif payload_type == "class_stream_end":
                    stream_id = payload.get('streamId')
                    with self.stream_queues_lock:
                        if stream_id in self.stream_queues:
                            self.stream_queues[stream_id].put({"type": "end"})
                    return

            # Keep existing log handling below...
```

- [ ] **Step 3: Stage files**

```bash
git add bridge/bridge.py
```

### Task 4: Add Python HTTP Streaming Endpoint

**Files:**
- Modify: `bridge/bridge.py`

- [ ] **Step 1: Add `/stream/classes` to `RpcHandler`**

In `RpcHandler.do_POST`, add routing for `/stream/classes` before the standard `/rpc`:

```python
    def do_POST(self):
        if self.path == '/stream/classes':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            try:
                import uuid
                import queue
                req = json.loads(post_data.decode('utf-8'))
                search_param = req.get('search_param', '')
                
                stream_id = str(uuid.uuid4())
                q = queue.Queue()
                
                with self.server.bridge.stream_queues_lock:
                    self.server.bridge.stream_queues[stream_id] = q

                # Call the agent method asynchronously
                asyncio.run(self.server.bridge.script.exports_async.listclassesstream(search_param, stream_id))
                
                self.send_response(200)
                self.send_header('Content-Type', 'application/x-ndjson')
                self.end_headers()
                
                while True:
                    msg = q.get(timeout=120)
                    if msg["type"] == "end":
                        break
                    elif msg["type"] == "chunk":
                        chunk_res = {"chunk": msg["data"]}
                        self.wfile.write((json.dumps(chunk_res) + "\n").encode('utf-8'))
                        self.wfile.flush()
                        
            except Exception as e:
                logging.error(f"Error in /stream/classes: {e}")
            finally:
                if 'stream_id' in locals():
                    with self.server.bridge.stream_queues_lock:
                        self.server.bridge.stream_queues.pop(stream_id, None)
            return

        # Existing rpc logic
        if self.path == '/rpc':
```

- [ ] **Step 2: Stage files**

```bash
git add bridge/bridge.py
```

### Task 5: Add Ktor Streaming Client Method

**Files:**
- Modify: `src/commonMain/kotlin/RpcClient.kt`

- [ ] **Step 1: Add `listClassesStream` to `RpcClient.kt`**

```kotlin
    suspend fun listClassesStream(searchParam: String, appPackage: String, onChunk: (List<String>) -> Unit): String? {
        return try {
            val requestBody = buildJsonObject {
                put("search_param", searchParam)
                put("app_package", appPackage)
            }

            client.preparePost("http://127.0.0.1:8080/stream/classes") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.execute { response ->
                if (response.status.value !in 200..299) {
                    return@execute "HTTP Error: ${response.status.value}"
                }
                
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank()) continue
                    
                    try {
                        val json = Json.parseToJsonElement(line).jsonObject
                        val chunkArray = json["chunk"]?.jsonArray
                        if (chunkArray != null) {
                            val chunk = chunkArray.map { it.jsonPrimitive.content }
                            onChunk(chunk)
                        }
                    } catch (e: Exception) {
                        // ignore malformed line
                    }
                }
                null // return null on success
            }
        } catch (e: Exception) {
            e.message ?: "Unknown streaming error"
        }
    }
```
*(Ensure `io.ktor.client.request.preparePost`, `io.ktor.client.statement.bodyAsChannel`, `io.ktor.utils.io.readUTF8Line`, `kotlinx.serialization.json.*` and `io.ktor.client.statement.HttpStatement` are imported)*

- [ ] **Step 2: Stage files**

```bash
git add src/commonMain/kotlin/RpcClient.kt
```

### Task 6: Wire Streaming to AppState and Main UI

**Files:**
- Modify: `src/commonMain/kotlin/AppState.kt`
- Modify: `src/unixMain/kotlin/Main.kt`

- [ ] **Step 1: Add new state variables in `AppState.kt`**
Under `var isFetchingClasses: Boolean = false`, add:
```kotlin
    val sharedStreamedClasses: AtomicReference<List<String>> = AtomicReference(emptyList())
    val sharedStreamCompleted: AtomicReference<Boolean> = AtomicReference(false)
```

- [ ] **Step 2: Update `Main.kt` to use the stream**
Replace the old `RpcClient.listClasses` block around line 1029:
```kotlin
                    if (currentTimeMillis() - state.lastInputTimestamp > 500 && state.inputBuffer != state.lastSearchedParam) {
                        state.lastSearchedParam = state.inputBuffer
                        state.isFetchingClasses = true
                        state.sharedStreamedClasses.value = emptyList()
                        state.sharedStreamCompleted.value = false
                        needsRender = true

                        scope.launch {
                            val err = RpcClient.listClassesStream(state.lastSearchedParam, state.appPackageName) { chunk ->
                                val current = state.sharedStreamedClasses.value
                                state.sharedStreamedClasses.value = current + chunk
                            }
                            state.sharedRpcError.value = err
                            state.sharedStreamCompleted.value = true
                        }
                    }

                    val currentStreamed = state.sharedStreamedClasses.value
                    if (currentStreamed != state.allFetchedClasses) {
                        state.allFetchedClasses = currentStreamed
                        state.displayedClasses = CommandExecutor.sortClasses(currentStreamed, state.appPackageName, state.lastSearchedParam, state.showSyntheticClasses)
                        state.selectedClassIndex = if (state.displayedClasses.isNotEmpty()) 0 else -1
                        needsRender = true
                    }
                    
                    if (state.sharedStreamCompleted.value && state.isFetchingClasses) {
                        state.isFetchingClasses = false
                        needsRender = true
                    }
```

- [ ] **Step 3: Stage files**

```bash
git add src/commonMain/kotlin/AppState.kt src/unixMain/kotlin/Main.kt
```

### Task 7: Update CommandExecutor for default mode

**Files:**
- Modify: `src/commonMain/kotlin/CommandExecutor.kt`

- [ ] **Step 1: Replace `listClasses` in `CommandExecutor.kt`**

Around line 61, replace the `RpcClient.listClasses` call with `listClassesStream` to avoid the timeout on iOS even in default mode:
```kotlin
                val list = mutableListOf<String>()
                val error = RpcClient.listClassesStream(state.inputBuffer, state.appPackageName) { chunk ->
                    list.addAll(chunk)
                }
                state.sharedFetchedClasses.value = list
                state.sharedRpcError.value = error
```

- [ ] **Step 2: Stage files**

```bash
git add src/commonMain/kotlin/CommandExecutor.kt
```

### Task 8: Manual Validation

**Files:**
- Output logs redirection

- [ ] **Step 1: Test Python Bridge API independently**
Run the bridge in background, outputting to a log file so it does not block the terminal:
```bash
python3 bridge/bridge.py > bridge_test_output.log 2>&1 &
echo $! > bridge.pid
```
Let it start. We won't test full frida injection without a device right now, but we will ensure the bridge can be pinged and the agent compiles/loads correctly syntax-wise.
```bash
curl http://127.0.0.1:8080/ping
```
*(If you have a testing device connected, you can invoke `/stream/classes` with `curl` using raw JSON)*

- [ ] **Step 2: Build the Kotlin App and Check Compile Output**
Run a full build to ensure the new Ktor streaming code compiles without missing imports:
```bash
./gradlew linkDebugExecutableMacosArm64 > build_test_output.log 2>&1
cat build_test_output.log
```
If errors occur (like missing imports), fix them.

- [ ] **Step 3: Stop Background Bridge**
```bash
kill -9 $(cat bridge.pid)
rm bridge.pid
```
