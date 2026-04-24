import RpcClient.client
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeoutOrNull

@Serializable
data class ListClassesParams(
    val search_param: String,
    val app_package: String,
    val offset: Int,
    val limit: Int
)

@Serializable
data class JsonRpcRequestListClasses(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: ListClassesParams,
    val id: Int = 1
)
@Serializable
data class CheckResponse(
    val status: String,
    val message: String,
    val fix: String? = null
)
@Serializable
data class HealthCheckResponse(
    val overall: String,
    val checks: Map<String, CheckResponse>
)
@Serializable
data class JsonRpcResponseHealthCheck(
    val jsonrpc: String = "2.0",
    val result: HealthCheckResponse? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

@Serializable
data class InspectClassParams(
    val className: String
)

@Serializable
data class JsonRpcRequestInspectClass(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: InspectClassParams,
    val id: Int = 1
)

@Serializable
data class CountInstancesParams(
    val className: String
)

@Serializable
data class JsonRpcRequestCountInstances(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: CountInstancesParams,
    val id: Int = 1
)

@Serializable
data class JsonRpcRequestSimple(
    val jsonrpc: String = "2.0",
    val method: String,
    val id: Int = 1
)
@Serializable
data class JsonRpcRequestSimpleWithSerial(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: Map<String, String>,
    val id: Int = 1
)

@Serializable
data class InjectGadgetParams(
    val with_logs: Boolean = true,
    val limit: Int = 100
)

@Serializable
data class JsonRpcRequestInjectGadget(
    val jsonrpc: String = "2.0",
    val method: String = "injectGadgetFromScratch",
    val params: InjectGadgetParams = InjectGadgetParams(),
    val id: Int = 1
)

@Serializable
data class ListInstancesParams(
    val className: String
)

@Serializable
data class JsonRpcRequestListInstances(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: ListInstancesParams,
    val id: Int = 1
)

@Serializable
data class InspectInstanceParams(
    val className: String,
    val id: String,
    val offset: Int = 0,
    val limit: Int = 50
)

@Serializable
data class JsonRpcRequestInspectInstance(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: InspectInstanceParams,
    val id: Int = 1
)

@Serializable
data class InstanceInfo(
    val id: String,
    val handle: String,
    val summary: String,
    val detectionMethod: String = "heap_scan"
)

@Serializable
data class InjectionStep(
    val id: String,
    val title: String,
    val status: String
)

@Serializable
data class InjectionProgressResult(
    val status: String,
    val steps: List<InjectionStep>,
    val logs: List<String> = emptyList(),
    val error_message: String? = null
)

@Serializable
data class JsonRpcInjectionProgressResponse(
    val jsonrpc: String,
    val result: InjectionProgressResult? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

@Serializable
data class ListInstancesResult(
    val instances: List<InstanceInfo>,
    val totalCount: Int,
    val detectionMethod: String = "heap_scan"
)

@Serializable
data class JsonRpcListInstancesResponse(
    val jsonrpc: String,
    val result: ListInstancesResult? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

@Serializable
data class InstanceAttribute(
    val name: String,
    val type: String,
    val value: String,
    val childId: String? = null,
    val childClassName: String? = null,
    val isPagination: Boolean = false,
    val nextOffset: Int = 0
)

@Serializable
data class InspectInstanceResult(
    val attributes: List<InstanceAttribute>
)

@Serializable
data class JsonRpcInspectInstanceResponse(
    val jsonrpc: String,
    val result: InspectInstanceResult? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String,
    val result: List<String>? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

@Serializable
data class JsonRpcInspectResponse(
    val jsonrpc: String,
    val result: ClassInspectionResult? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

@Serializable
data class JsonRpcStringResponse(
    val jsonrpc: String,
    val result: String? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

@Serializable
data class JsonRpcCountInstancesResponse(
    val jsonrpc: String,
    val result: Int? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String
)

@Serializable
data class PrepareEnvResult(
    val pid: Int,
    val package_name: String,
    val port: Int,
    val target: String
)

@Serializable
data class JsonRpcPrepareEnvResponse(
    val jsonrpc: String,
    val result: PrepareEnvResult? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

@Serializable
data class GenericStatusResult(
    val status: String,
    val error_message: String? = null
)

@Serializable
data class JsonRpcGenericStatusResponse(
    val jsonrpc: String,
    val result: GenericStatusResult? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

@Serializable
data class InjectJdwpParams(
    val target: String,
    val port: Int,
    val package_name: String
)

@Serializable
data class JsonRpcInjectJdwpRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: InjectJdwpParams,
    val id: Int = 1
)

@Serializable
data class HookParams(val className: String, val methodSig: String)

@Serializable
data class JsonRpcHookRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: HookParams,
    val id: Int = 1
)

@Serializable
data class SetMethodImplementationParams(
    val className: String,
    val methodSig: String,
    val code: String
)

@Serializable
data class JsonRpcRequestSetMethodImplementation(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: SetMethodImplementationParams,
    val id: Int = 1
)

@Serializable
data class JsonRpcHookEventsResponse(
    val jsonrpc: String,
    val result: List<HookEvent>? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

@Serializable
data class RunOnceParams(
    val className: String,
    val methodSig: String,
    val code: String
)

@Serializable
data class JsonRpcRequestRunOnce(
    val jsonrpc: String = "2.0",
    val method: String = "runOnce",
    val params: RunOnceParams,
    val id: Int = 1
)

@Serializable
data class GetInstanceAddressesParams(
    val className: String
)

@Serializable
data class JsonRpcRequestGetInstanceAddresses(
    val jsonrpc: String = "2.0",
    val method: String = "getInstanceAddresses",
    val params: GetInstanceAddressesParams,
    val id: Int = 1
)

@Serializable
data class SetFieldValueParams(
    val className: String,
    val id: String,
    val fieldName: String,
    val type: String,
    val newValue: String
)

@Serializable
data class JsonRpcRequestSetFieldValue(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: SetFieldValueParams,
    val id: Int = 1
)

@Serializable
data class PatchAndInstallIosAppParams(
    val appPath: String
)

@Serializable
data class JsonRpcRequestPatchAndInstallIosApp(
    val jsonrpc: String = "2.0",
    val method: String = "patchAndInstallIosApp",
    val params: PatchAndInstallIosAppParams,
    val id: Int = 1
)

@Serializable
data class IosJailbreakParams(
    val serial: String
)

@Serializable
data class JsonRpcRequestCheckIosJailbreakStatus(
    val jsonrpc: String = "2.0",
    val method: String = "checkIosJailbreakStatus",
    val params: IosJailbreakParams,
    val id: Int = 1
)

@Serializable
data class JsonRpcRequestInjectJailbrokenIos(
    val jsonrpc: String = "2.0",
    val method: String = "injectJailbrokenIos",
    val params: IosJailbreakParams,
    val id: Int = 1
)

@Serializable
data class JsonRpcRequestCheckIosDeployStatus(
    val jsonrpc: String = "2.0",
    val method: String = "checkIosDeployStatus",
    val id: Int = 1
)

object RpcClient {
    var client: HttpClient = HttpClient(
        getRpcClientEngine()
    ) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
    }

    suspend fun ping(): Boolean {
        return try {
            val response: HttpResponse = withTimeoutOrNull(5000) {
                client.get("http://127.0.0.1:8080/ping")
            } ?: return false
            response.status.value in 200..299
        } catch (e: Exception) {
            false
        }
    }

    suspend fun listClasses(searchParam: String, appPackage: String, offset: Int = 0, limit: Int = 200): Pair<List<String>?, String?> {
        return try {
            val requestBody = JsonRpcRequestListClasses(
                method = "listClasses",
                params = ListClassesParams(searchParam, appPackage, offset, limit)
            )

            val response: HttpResponse = withTimeoutOrNull(5000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return Pair(null, "RPC Timeout (5s)")

            if (response.status.value in 200..299) {
                val rpcResponse = response.body<JsonRpcResponse>()
                if (rpcResponse.error != null) {
                    Pair(null, rpcResponse.error.message)
                } else {
                    Pair(rpcResponse.result, null)
                }
            } else {
                Pair(null, "RPC HTTP Error: ${response.status.value}")
            }
        } catch (e: Exception) {
            Pair(null, "RPC Internal Error: ${e.message}")
        }
    }

    suspend fun inspectClass(className: String): Pair<ClassInspectionResult?, String?> {
        return try {
            val requestBody = JsonRpcRequestInspectClass(
                method = "inspectClass",
                params = InspectClassParams(className)
            )

            val response: HttpResponse = withTimeoutOrNull(5000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return Pair(null, "RPC Timeout (5s)")

            if (response.status.value in 200..299) {
                val rpcResponse = response.body<JsonRpcInspectResponse>()
                if (rpcResponse.error != null) {
                    Pair(null, rpcResponse.error.message)
                } else if (rpcResponse.result == null) {
                    Pair(null, "RPC Result is null")
                } else {
                    Pair(rpcResponse.result, null)
                }
            } else {
                Pair(null, "RPC HTTP Error: ${response.status.value}")
            }
        } catch (e: Exception) {
            Pair(null, "RPC Internal Error: ${e.message}")
        }
    }

    suspend fun countInstances(className: String): Pair<Int?, String?> {
        return try {
            val requestBody = JsonRpcRequestCountInstances(
                method = "countInstances",
                params = CountInstancesParams(className)
            )

            val response: HttpResponse = withTimeoutOrNull(10000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return Pair(null, "RPC Timeout (10s)")

            if (response.status.value in 200..299) {
                val rpcResponse = response.body<JsonRpcCountInstancesResponse>()
                if (rpcResponse.error != null) {
                    Pair(null, rpcResponse.error.message)
                } else if (rpcResponse.result == null) {
                    Pair(null, "RPC Result is null")
                } else {
                    Pair(rpcResponse.result, null)
                }
            } else {
                Pair(null, "RPC HTTP Error: ${response.status.value}")
            }
        } catch (e: Exception) {
            Pair(null, "RPC Internal Error: ${e.message}")
        }
    }

    suspend fun listInstances(className: String): Pair<ListInstancesResult?, String?> {
        return try {
            val requestBody = JsonRpcRequestListInstances(
                method = "listInstances",
                params = ListInstancesParams(className)
            )

            val response: HttpResponse = withTimeoutOrNull(10000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return Pair(null, "RPC Timeout (10s)")

            if (response.status.value in 200..299) {
                val rpcResponse = response.body<JsonRpcListInstancesResponse>()
                if (rpcResponse.error != null) {
                    Pair(null, rpcResponse.error.message)
                } else if (rpcResponse.result == null) {
                    Pair(null, "RPC Result is null")
                } else {
                    Pair(rpcResponse.result, null)
                }
            } else {
                Pair(null, "RPC HTTP Error: ${response.status.value}")
            }
        } catch (e: Exception) {
            Pair(null, "RPC Internal Error: ${e.message}")
        }
    }

    suspend fun inspectInstance(className: String, id: String, offset: Int = 0, limit: Int = 50): Pair<List<InstanceAttribute>?, String?> {
        return try {
            val requestBody = JsonRpcRequestInspectInstance(
                method = "inspectInstance",
                params = InspectInstanceParams(className, id, offset, limit)
            )

            val response: HttpResponse = withTimeoutOrNull(10000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return Pair(null, "RPC Timeout (10s)")
            if (response.status.value in 200..299) {
                val rpcResponse = response.body<JsonRpcInspectInstanceResponse>()
                if (rpcResponse.error != null) {
                    Pair(null, rpcResponse.error.message)
                } else if (rpcResponse.result == null) {
                    Pair(null, "RPC Result is null")
                } else {
                    Pair(rpcResponse.result.attributes, null)
                }
            } else {
                Pair(null, "RPC HTTP Error: ${response.status.value}")
            }
        } catch (e: Exception) {
            Pair(null, "RPC Internal Error: ${e.message}")
        }
    }

    suspend fun setFieldValue(className: String, id: String, fieldName: String, type: String, newValue: String): String? {
        return try {
            val requestBody = JsonRpcRequestSetFieldValue(
                method = "setFieldValue",
                params = SetFieldValueParams(className, id, fieldName, type, newValue)
            )

            val response: HttpResponse = withTimeoutOrNull(10000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return "RPC Timeout (10s)"

            if (response.status.value in 200..299) {
                val rpcResponse = response.body<JsonRpcGenericStatusResponse>()
                if (rpcResponse.error != null) {
                    rpcResponse.error.message
                } else if (rpcResponse.result?.error_message != null) {
                    rpcResponse.result.error_message
                } else {
                    null // Success
                }
            } else {
                "RPC HTTP Error: ${response.status.value}"
            }
        } catch (e: Exception) {
            "RPC Internal Error: ${e.message}"
        }
    }

    suspend fun prepareEnvironment(serial: String): Pair<PrepareEnvResult?, String?> {
        val serverUp = ping()
        if (!serverUp) {
            return Pair(null, "Bridge server is not running")
        }

        return try {
            val requestBody = JsonRpcRequestSimpleWithSerial(method = "prepareEnvironment", params = mapOf("serial" to serial))
            val response: HttpResponse = withTimeoutOrNull(20000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return Pair(null, "Timeout preparing ADB environment")

            if (response.status.value in 200..299) {
                val rpcResponse = response.body<JsonRpcPrepareEnvResponse>()
                if (rpcResponse.result != null) {
                    Pair(rpcResponse.result, null)
                } else if (rpcResponse.error != null) {
                    Pair(null, rpcResponse.error.message)
                } else {
                    Pair(null, "Unexpected empty response")
                }
            } else {
                Pair(null, "RPC HTTP Error: ${response.status.value}")
            }
        } catch (e: Exception) {
            Pair(null, "RPC Internal Error: ${e.message}")
        }
    }


    suspend fun resetInjection(serial: String): Boolean {
        return try {
            val requestBody = JsonRpcRequestSimpleWithSerial(method = "resetInjection", params = mapOf("serial" to serial))
            val response: HttpResponse = withTimeoutOrNull(5000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return false
            response.status.value in 200..299
        } catch (e: Exception) {
            false
        }
    }
    suspend fun healthCheck(serial: String): HealthCheckResponse? {
        return try {
            val requestBody = JsonRpcRequestSimpleWithSerial(method = "healthCheck", params = mapOf("serial" to serial))
            val response: HttpResponse = withTimeoutOrNull(5000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return null

            if (response.status.value in 200..299) {
                response.body<JsonRpcResponseHealthCheck>().result
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun injectJdwp(target: String, port: Int, packageName: String): Pair<String?, String?> {
        return try {
            val requestBody = JsonRpcInjectJdwpRequest(
                method = "injectJdwp",
                params = InjectJdwpParams(target, port, packageName)
            )
            val response: HttpResponse = withTimeoutOrNull(60000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return Pair(null, "Timeout during JDWP injection")

            if (response.status.value in 200..299) {
                val rpcResponse = response.body<JsonRpcGenericStatusResponse>()
                if (rpcResponse.result != null) {
                    Pair(rpcResponse.result.status, rpcResponse.result.error_message)
                } else if (rpcResponse.error != null) {
                    Pair(null, rpcResponse.error.message)
                } else {
                    Pair(null, "Unexpected empty response")
                }
            } else {
                try {
                    val rpcResponse = response.body<JsonRpcGenericStatusResponse>()
                    if (rpcResponse.error != null) {
                        Pair(null, rpcResponse.error.message)
                    } else {
                        Pair(null, "RPC HTTP Error: ${response.status.value}")
                    }
                } catch (e: Exception) {
                    Pair(null, "RPC HTTP Error: ${response.status.value}")
                }
            }
        } catch (e: Exception) {
            Pair(null, "RPC Internal Error: ${e.message}")
        }
    }

    suspend fun getPackageName(): Pair<String?, String?> {
        return try {
            val requestBody = JsonRpcRequestSimple(method = "getpackagename")

            val response: HttpResponse = withTimeoutOrNull(5000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return Pair(null, "RPC Timeout (5s)")

            if (response.status.value in 200..299) {
                val rpcResponse = response.body<JsonRpcStringResponse>()
                if (rpcResponse.error != null) {
                    Pair(null, rpcResponse.error.message)
                } else {
                    Pair(rpcResponse.result, null)
                }
            } else {
                Pair(null, "RPC HTTP Error: ${response.status.value}")
            }
        } catch (e: Exception) {
            Pair(null, "RPC Internal Error: ${e.message}")
        }
    }

    suspend fun injectGadgetFromScratch(): Pair<InjectionProgressResult?, String?> {
        return try {
            val requestBody = JsonRpcRequestInjectGadget()

            val response: HttpResponse = withTimeoutOrNull(5000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return Pair(null, "RPC Timeout (5s)")


            if (response.status.value in 200..299) {
                val rpcResponse = response.body<JsonRpcInjectionProgressResponse>()
                if (rpcResponse.result != null) {
                    Pair(rpcResponse.result, null)
                } else if (rpcResponse.error != null) {
                    Pair(null, rpcResponse.error.message)
                } else {
                    Pair(null, "Unexpected empty response")
                }
            } else {
                try {
                    val rpcResponse = response.body<JsonRpcInjectionProgressResponse>()
                    if (rpcResponse.error != null) {
                        Pair(null, rpcResponse.error.message)
                    } else {
                        Pair(null, "RPC HTTP Error: ${response.status.value}")
                    }
                } catch (e: Exception) {
                    Pair(null, "RPC HTTP Error: ${response.status.value}")
                }
            }
        } catch (e: Exception) {
            Pair(null, "RPC Internal Error: ${e.message}")
        }
    }

    suspend fun toggleHook(className: String, methodSig: String, enable: Boolean, implementation: String? = null): Boolean {
        if (enable && implementation != null) {
            return setMethodImplementation(className, methodSig, implementation)
        }
        val method = if (enable) "hookMethod" else "unhookMethod"
        return try {
            val requestBody = JsonRpcHookRequest(method = method, params = HookParams(className, methodSig))
            val response: HttpResponse = client.post("http://127.0.0.1:8080/rpc") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            response.status.value in 200..299
        } catch (e: Exception) { false }
    }
    suspend fun getHookEvents(): List<HookEvent> {
        return try {
            val requestBody = JsonRpcRequestSimple(method = "getHookEvents")
            val response: HttpResponse = client.post("http://127.0.0.1:8080/rpc") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            if (response.status.value in 200..299) {
                response.body<JsonRpcHookEventsResponse>().result ?: emptyList()
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun setMethodImplementation(className: String, methodSig: String, code: String): Boolean {
        return try {
            val requestBody = JsonRpcRequestSetMethodImplementation(
                method = "setMethodImplementation",
                params = SetMethodImplementationParams(className, methodSig, code)
            )

            val response: HttpResponse = withTimeoutOrNull(10000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return false

            response.status.value in 200..299
        } catch (e: Exception) {
            false
        }
    }

    suspend fun runOnce(className: String, methodSig: String, code: String): Boolean {
        return try {
            val requestBody = JsonRpcRequestRunOnce(
                params = RunOnceParams(className, methodSig, code)
            )
            val response: HttpResponse = withTimeoutOrNull(30000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return false
            response.status.value in 200..299
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getInstanceAddresses(className: String): List<String> {
        return try {
            val requestBody = JsonRpcRequestGetInstanceAddresses(
                params = GetInstanceAddressesParams(className)
            )
            val response: HttpResponse = withTimeoutOrNull(5000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return emptyList()
            val body = response.body<String>()
            val json = Json { ignoreUnknownKeys = true }
            val parsed = json.decodeFromString<JsonRpcResponse>(body)
            parsed.result ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun removeMethodImplementation(className: String, methodSig: String): Boolean {
        return toggleHook(className, methodSig, false)
    }

    suspend fun syncAllHooks(hooks: List<HookTarget>) {
        hooks.filter { it.enabled }.forEach { hook ->
            toggleHook(hook.className, hook.memberSignature, true)
        }
    }

    suspend fun patchAndInstallIosApp(appPath: String): Pair<Boolean, String?> {
        return try {
            val requestBody = JsonRpcRequestPatchAndInstallIosApp(
                params = PatchAndInstallIosAppParams(appPath)
            )
            val response: HttpResponse = withTimeoutOrNull(30_000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return Pair(false, "Timeout calling bridge")
            
            if (response.status.value in 200..299) {
                val rpcResponse = response.body<JsonRpcInjectionProgressResponse>()
                if (rpcResponse.result != null) {
                    Pair(true, null)
                } else if (rpcResponse.error != null) {
                    Pair(false, rpcResponse.error.message)
                } else {
                    Pair(false, "Unexpected empty response")
                }
            } else {
                Pair(false, "RPC HTTP Error: ${response.status.value}")
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Error connecting to bridge")
        }
    }

    suspend fun checkIosDeployStatus(): Pair<GadgetInstallStatus, InjectionProgressResult?> {
        return try {
            val requestBody = JsonRpcRequestCheckIosDeployStatus()
            val response: HttpResponse = withTimeoutOrNull(5000) {
                client.post("http://127.0.0.1:8080/rpc") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            } ?: return Pair(GadgetInstallStatus.IDLE, null)

            if (response.status.value in 200..299) {
                val rpcResponse = response.body<JsonRpcInjectionProgressResponse>()
                val res = rpcResponse.result
                if (res != null) {
                    val status = when (res.status) {
                        "completed", "success" -> GadgetInstallStatus.SUCCESS
                        "error" -> GadgetInstallStatus.ERROR
                        else -> GadgetInstallStatus.WAITING_BRIDGE_SETUP
                    }
                    Pair(status, res)
                } else Pair(GadgetInstallStatus.IDLE, null)
            } else Pair(GadgetInstallStatus.ERROR, null)
        } catch (e: Exception) {
            Pair(GadgetInstallStatus.ERROR, null)
        }
    }
}

