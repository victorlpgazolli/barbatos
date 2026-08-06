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
            description = "",
        )
        internal val TEST_RPC = ActionDescriptor.create(
            name = "testRpc",
            description = "",
        )
        internal val COUNT_INSTANCES = ActionDescriptor.create<CountInstancesParams>(
            name = "countInstances",
            description = "",
        )
        internal val INSPECT_CLASS = ActionDescriptor.create<InspectClassParams>(
            name = "inspectClass",
            description = "",
        )
        internal val LIST_INSTANCES = ActionDescriptor.create<ListInstancesParams>(
            name = "listInstances",
            description = "",
        )
        internal val INSPECT_INSTANCE = ActionDescriptor.create<InspectInstanceParams>(
            name = "inspectInstance",
            description = "",
        )
        internal val SET_FIELD_VALUE = ActionDescriptor.create<SetFieldValueParams>(
            name = "setFieldValue",
            description = "",
        )
        internal val HOOK_METHOD = ActionDescriptor.create<HookParams>(
            name = "hookMethod",
            description = "",
        )
        internal val GET_HOOK_EVENTS = ActionDescriptor.create(
            name = "getHookEvents",
            description = "",
        )
        internal val SET_METHOD_IMPLEMENTATION = ActionDescriptor.create<SetMethodImplementationParams>(
            name = "setMethodImplementation",
            description = "",
        )
        internal val RUN_ONCE = ActionDescriptor.create<RunOnceParams>(
            name = "runOnce",
            description = "",
        )
        internal val GET_INSTANCE_ADDRESSES = ActionDescriptor.create<GetInstanceAddressesParams>(
            name = "getInstanceAddresses",
            description = "",
        )
        internal val PREPARE_ENVIRONMENT = ActionDescriptor.create<PrepareEnvParams>(
            name = "prepareEnvironment",
            description = "",
        )
        internal val INJECT_GADGET_FROM_SCRATCH = ActionDescriptor.create<InjectGadgetParams>(
            name = "injectGadgetFromScratch",
            description = "",
        )
        internal val INJECT_JDWP = ActionDescriptor.create<InjectJdwpParams>(
            name = "injectJdwp",
            description = "",
        )
        internal val HEALTH_CHECK = ActionDescriptor.create(
            name = "healthCheck",
            description = "",
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