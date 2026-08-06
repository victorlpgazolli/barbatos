package rpc

import model.bridge.FridaBridge
import kotlinx.serialization.json.*
import model.actions.ActionDescriptor
import model.actions.params.*
import model.rpc.RpcError
import model.rpc.RpcErrorResponse
import model.rpc.RpcRequest
import model.rpc.RpcResponse
import utils.decodeToOrThrow

data class HandlerResult(val body: String, val statusCode: Int)

class RpcHandler(private val bridge: FridaBridge) {
    val jsonParser = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun isStreamMethod(requestJson: String): Boolean {
        return try {
            val req = jsonParser.decodeFromString<RpcRequest>(requestJson)
            req.method == "listClassesStream"
        } catch (e: Exception) {
            false
        }
    }

    suspend fun handleStream(requestJson: String, emit: suspend (String) -> Unit) {
        val req = try {
            jsonParser.decodeFromString<RpcRequest>(requestJson)
        } catch (e: Exception) {
            val errorStr = jsonParser.encodeToString(
                RpcErrorResponse.serializer(),
                RpcErrorResponse(error = RpcError(-32700, "Parse error: ${e.message}"), id = null)
            )
            emit(errorStr)
            return
        }

        if (req.method == "listClassesStream") {
            val p = req.params?.let { jsonParser.decodeFromJsonElement<ListClassesParams>(it) } ?: ListClassesParams()

            try {
                bridge.listClassesStream(
                    ListClassesParams(
                        searchParam = p.searchParam,
                        appPackage = p.appPackage,
                        offset = p.offset,
                        limit = p.limit,
                    ),
                    onChunk = { chunk ->
                        val res = RpcResponse(
                            result = jsonParser.encodeToJsonElement(chunk),
                            id = req.id
                        )
                        val jsonStr = jsonParser.encodeToString(RpcResponse.serializer(), res)

                        emit(jsonStr)
                    },
                    onComplete = {}
                )
            } catch (e: Exception) {
                val errorStr = jsonParser.encodeToString(
                    RpcErrorResponse.serializer(),
                    RpcErrorResponse(
                        error = RpcError(-32603, e.message ?: "Internal stream error"),
                        id = req.id
                    )
                )
                emit(errorStr)
            }
        }
    }

    fun handle(requestJson: String): HandlerResult {
        val req = try {
            jsonParser.decodeFromString<RpcRequest>(requestJson)
        } catch (e: Exception) {
            return HandlerResult(
                jsonParser.encodeToString(
                    RpcErrorResponse.serializer(),
                    RpcErrorResponse(
                        error = RpcError(-32700, "Parse error: ${e.message}"),
                        id = null
                    )
                ),
                200
            )
        }

        return try {
            val result = processMethod(req.method, req.params)
            HandlerResult(
                jsonParser.encodeToString(RpcResponse.serializer(), RpcResponse(result = result, id = req.id)),
                200
            )
        } catch (e: Exception) {
            val code = if (e.message?.contains("not found") == true) -32601 else -32603
            HandlerResult(
                jsonParser.encodeToString(
                    RpcErrorResponse.serializer(),
                    RpcErrorResponse(
                        error = RpcError(code, e.message ?: "Internal error"),
                        id = req.id
                    )
                ),
                200
            )
        }
    }

    public fun processMethod(method: String, params: JsonElement?): JsonElement {
        return when (method) {
            DEBUG_PING.name -> {
                val result = bridge.pingJava()
                jsonParser.encodeToJsonElement(result)
            }
            TEST_RPC.name -> {
                val result = bridge.testRpc()
                jsonParser.encodeToJsonElement(result)
            }
            COUNT_INSTANCES.name -> {
                val decodedParams = decodeToOrThrow<CountInstancesParams>(params)
                val result = bridge.countInstances(decodedParams)
                jsonParser.encodeToJsonElement(result)
            }
            INSPECT_CLASS.name -> {
                val decodedParams = decodeToOrThrow<InspectClassParams>(params)
                val result = bridge.inspectClass(decodedParams)
                jsonParser.encodeToJsonElement(result)
            }
            LIST_INSTANCES.name -> {
                val decodedParams = decodeToOrThrow<ListInstancesParams>(params)
                val result = bridge.listInstances(decodedParams)
                jsonParser.encodeToJsonElement(result)
            }
            INSPECT_INSTANCE.name -> {
                val decodedParams = decodeToOrThrow<InspectInstanceParams>(params)
                val result = bridge.inspectInstance(decodedParams)
                jsonParser.encodeToJsonElement(result)
            }
            SET_FIELD_VALUE.name -> {
                val decodedParams = decodeToOrThrow<SetFieldValueParams>(params)
                val result = bridge.setFieldValue(decodedParams)
                jsonParser.encodeToJsonElement(result)
            }
            HOOK_METHOD.name -> {
                val decodedParams = decodeToOrThrow<HookParams>(params)
                val result = bridge.hookMethod(decodedParams)
                jsonParser.encodeToJsonElement(result)
            }
            GET_HOOK_EVENTS.name -> {
                val result = bridge.getHookEvents()
                jsonParser.encodeToJsonElement(result)
            }
            SET_METHOD_IMPLEMENTATION.name -> {
                val decodedParams = decodeToOrThrow<SetMethodImplementationParams>(params)
                val result = bridge.setMethodImplementation(decodedParams)
                jsonParser.encodeToJsonElement(result)
            }
            RUN_ONCE.name -> {
                val decodedParams = decodeToOrThrow<RunOnceParams>(params)
                val result = bridge.runOnce(decodedParams)
                jsonParser.encodeToJsonElement(result)
            }
            GET_INSTANCE_ADDRESSES.name -> {
                val decodedParams = decodeToOrThrow<GetInstanceAddressesParams>(params)
                val res = bridge.getInstanceAddresses(decodedParams)
                jsonParser.encodeToJsonElement(res)
            }
            PREPARE_ENVIRONMENT.name -> {
                val decodedParams = decodeToOrThrow<PrepareEnvParams>(params)
                val result = bridge.prepareEnvironment(decodedParams)
                jsonParser.encodeToJsonElement(result)
            }
            INJECT_GADGET_FROM_SCRATCH.name -> {
                val decodedParams = decodeToOrThrow<InjectGadgetParams>(params)
                val result = bridge.injectGadgetFromScratch(decodedParams)
                jsonParser.encodeToJsonElement(result)
            }
            INJECT_JDWP.name -> {
                val decodedParams = decodeToOrThrow<InjectJdwpParams>(params)
                val result = bridge.injectJdwp(decodedParams)
                jsonParser.encodeToJsonElement(result)
            }
            HEALTH_CHECK.name -> {
                val result = bridge.healthCheck()
                jsonParser.encodeToJsonElement(result)
            }
            else -> throw Exception("Method $method not found")
        }
    }

    companion object {
        internal val DEBUG_PING = ActionDescriptor.create(
            name = "debugPing",
            description = "Ping the Frida Java bridge to verify the injected agent is alive and responding. " +
                "Takes no parameters. Requires an active Frida session (call injectGadgetFromScratch first). " +
                "Returns the string \"pong\" on success, or \"error: Frida script not loaded.\" if no session is active.",
        )
        internal val TEST_RPC = ActionDescriptor.create(
            name = "testRpc",
            description = "Test the JSON-RPC communication channel between the host and the Frida agent running inside the target process. " +
                "Takes no parameters. Requires an active Frida session. " +
                "Returns the string \"ok\" on success, or \"error: Frida script not loaded.\" if no session is active.",
        )
        internal val COUNT_INSTANCES = ActionDescriptor.create<CountInstancesParams>(
            name = "countInstances",
            description = "Count the number of live instances of a Java/Kotlin class currently on the Android heap. " +
                "Performs a heap scan to find all objects of the given class. " +
                "Requires an active Frida session. " +
                "Returns a JSON object with a \"count\" field (integer). " +
                "Example: {\"count\": 3}.",
        )
        internal val INSPECT_CLASS = ActionDescriptor.create<InspectClassParams>(
            name = "inspectClass",
            description = "Inspect a Java/Kotlin class to retrieve its full structure: static attributes, instance attributes, and methods (including private, synthetic, and compiler-generated ones like R8 lambdas and Kotlin accessors). " +
                "Use the fully qualified class name (e.g. \"com.example.MyClass\"). " +
                "Requires an active Frida session. " +
                "Returns a JSON object with three arrays: \"staticAttributes\", \"instanceAttributes\", and \"methods\", each containing the full Java signature strings.",
        )
        internal val LIST_INSTANCES = ActionDescriptor.create<ListInstancesParams>(
            name = "listInstances",
            description = "List all live instances of a Java/Kotlin class on the Android heap with their unique identifiers. " +
                "Performs a heap scan and returns each instance's id, memory handle, a human-readable summary (toString-like), and the detection method used. " +
                "Requires an active Frida session. " +
                "Returns a JSON object with \"instances\" (array of {id, handle, summary, detectionMethod}), \"totalCount\" (integer), and \"detectionMethod\" (string, typically \"heap_scan\"). " +
                "Use the returned \"id\" values with inspectInstance, setFieldValue, or other instance-level operations.",
        )
        internal val INSPECT_INSTANCE = ActionDescriptor.create<InspectInstanceParams>(
            name = "inspectInstance",
            description = "Deep-inspect a specific live object instance on the Android heap. " +
                "Retrieves the current runtime values of all fields, including their types, string representations, and child object references for further drill-down. " +
                "Requires className and id (obtained from listInstances). Supports pagination via offset and limit (default: offset=0, limit=50). " +
                "Requires an active Frida session. " +
                "Returns a JSON object with an \"attributes\" array where each entry has: name, type, value (string representation), childId (nullable, for navigating into nested objects), childClassName (nullable), isPagination (boolean), and nextOffset (integer).",
        )
        internal val SET_FIELD_VALUE = ActionDescriptor.create<SetFieldValueParams>(
            name = "setFieldValue",
            description = "Modify the value of a field on a live object instance at runtime. " +
                "Requires className, id (from listInstances), fieldName, type (Java type name, e.g. \"String\", \"int\", \"boolean\"), and newValue (string representation of the new value). " +
                "Requires an active Frida session. " +
                "Returns a JSON object with a \"status\" field: \"true\" on success.",
        )
        internal val HOOK_METHOD = ActionDescriptor.create<HookParams>(
            name = "hookMethod",
            description = "Hook a method to intercept and record every invocation at runtime. " +
                "Once hooked, each call to the method is captured as a hook event with its arguments and return value. " +
                "Retrieve captured events with getHookEvents. " +
                "Requires className and methodSig (the method name, e.g. \"onLoginClicked\"). " +
                "Requires an active Frida session. " +
                "Returns a JSON object with a \"status\" field: \"true\" on success.",
        )
        internal val GET_HOOK_EVENTS = ActionDescriptor.create(
            name = "getHookEvents",
            description = "Retrieve all hook events captured since the last call. " +
                "Returns invocations recorded by previously installed hooks (via hookMethod). " +
                "Takes no parameters. Requires an active Frida session. " +
                "Returns a JSON object with an \"events\" array. Each event contains details about the intercepted method call (arguments, return value, timestamp). " +
                "The events list is empty if no hooked methods have been called yet.",
        )
        internal val SET_METHOD_IMPLEMENTATION = ActionDescriptor.create<SetMethodImplementationParams>(
            name = "setMethodImplementation",
            description = "Replace the implementation of a method at runtime with custom JavaScript code. " +
                "The replacement persists for the lifetime of the Frida session. Every future call to the method will execute the provided code instead of the original implementation. " +
                "Requires className, methodSig (method name), and code (JavaScript string — use \"args\" to access method arguments, \"this\" for the instance). " +
                "Requires an active Frida session. " +
                "Returns a JSON object with a \"status\" field: \"true\" on success.",
        )
        internal val RUN_ONCE = ActionDescriptor.create<RunOnceParams>(
            name = "runOnce",
            description = "Execute custom JavaScript code once in the context of a method, then restore the original implementation. " +
                "Unlike setMethodImplementation, this is a one-shot execution: the next call to the method runs the custom code, and subsequent calls revert to the original behavior. " +
                "Requires className, methodSig (method name), and code (JavaScript string). " +
                "Requires an active Frida session. " +
                "Returns a JSON object with a \"status\" field: \"true\" on success.",
        )
        internal val GET_INSTANCE_ADDRESSES = ActionDescriptor.create<GetInstanceAddressesParams>(
            name = "getInstanceAddresses",
            description = "Get the memory addresses (Java object references) of all live instances of a class on the Android heap. " +
                "Requires className. Requires an active Frida session. " +
                "Returns a JSON object with an \"addresses\" array of strings in the format \"fully.qualified.ClassName@hexHash\" (e.g. \"LoginUiModel@2ec2d5e\").",
        )
        internal val PREPARE_ENVIRONMENT = ActionDescriptor.create<PrepareEnvParams>(
            name = "prepareEnvironment",
            description = "Attach Frida to a running process and load the instrumentation agent. " +
                "This is the low-level setup step — prefer injectGadgetFromScratch for a fully automated flow. " +
                "Finds the target process by pid or package_name on the specified device (serial), attaches a Frida session, and loads the JavaScript agent. " +
                "When target is \"Gadget\" or \"127.0.0.1\", connects to a remote Frida Gadget via TCP on port 27042 (used for the non-rooted/debuggable injection path). " +
                "When serial is provided, uses frida_device_manager_get_device_by_id_sync to target the exact device (critical when multiple USB devices are connected). " +
                "Returns a JSON object with a confirmation message on success.",
        )
        internal val INJECT_GADGET_FROM_SCRATCH = ActionDescriptor.create<InjectGadgetParams>(
            name = "injectGadgetFromScratch",
            description = "Fully automated Frida injection into the frontmost Android application. This is the primary entry point — call this before using any inspection or hooking tools. " +
                "Detects the foreground app, determines whether the device is rooted or the app is debuggable, and chooses the appropriate injection strategy: " +
                "(1) Rooted device: pushes frida-server to /data/local/tmp/, launches it as root, then attaches to the target process via USB. " +
                "(2) Debuggable app (non-rooted): sets up ADB port forwards, pushes the Frida Gadget shared library, injects via JDWP, then attaches via the Gadget remote interface. " +
                "If neither rooted nor debuggable, returns an error. " +
                "Use the optional serial parameter to target a specific device when multiple are connected (serial number from \"adb devices\"). " +
                "Returns a JSON object with \"status\" (\"completed\" or \"error\"), \"steps\" (array of {id, title, status} tracking each phase), and \"error_message\" (nullable). " +
                "After a successful injection, all other tools (inspectClass, listInstances, hookMethod, etc.) become operational.",
        )
        internal val INJECT_JDWP = ActionDescriptor.create<InjectJdwpParams>(
            name = "injectJdwp",
            description = "Inject Frida Gadget into a debuggable Android app via the JDWP (Java Debug Wire Protocol) interface. " +
                "This is the low-level JDWP injection step — prefer injectGadgetFromScratch for a fully automated flow. " +
                "Requires target (host address, e.g. \"127.0.0.1\"), port (JDWP port, e.g. 5005), and package_name (Android app package). " +
                "Uses the Gadget library cached at ~/.cache/barbatos/frida-gadget.so. " +
                "Returns a JSON object with \"status\" (\"ok\" or \"error\") and \"message\" describing the result.",
        )
        internal val HEALTH_CHECK = ActionDescriptor.create(
            name = "healthCheck",
            description = "Run a comprehensive system health check covering ADB connectivity, device root status, frontmost app detection, Frida device enumeration, and active session state. " +
                "Takes no parameters. Can be called at any time (does not require an active Frida session). " +
                "Returns a JSON object with \"overall\" (\"ok\" or \"degraded\"), \"checks\" (a map of component names to {status, message, fix, package, pid, debuggable}), and \"recommendation\" (nullable). " +
                "Check statuses: \"ok\", \"info\", \"warning\", \"error\", \"skipped\", \"unknown\". " +
                "Components checked: adb, android_root, android_frontmost_app, frida_device, frida_connection, session.",
        )

        public val tools = listOf(
            DEBUG_PING,
            TEST_RPC,
            COUNT_INSTANCES,
            INSPECT_CLASS,
            LIST_INSTANCES,
            INSPECT_INSTANCE,
            SET_FIELD_VALUE,
            HOOK_METHOD,
            GET_HOOK_EVENTS,
            SET_METHOD_IMPLEMENTATION,
            RUN_ONCE,
            GET_INSTANCE_ADDRESSES,
            PREPARE_ENVIRONMENT,
            INJECT_GADGET_FROM_SCRATCH,
            INJECT_JDWP,
            HEALTH_CHECK,
        )

    }
}