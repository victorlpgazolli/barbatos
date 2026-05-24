package bridge

import bridge.NativeFridaBridge.Companion.FridaRpcManager
import frida.*
import kotlinx.cinterop.*
import kotlinx.serialization.json.Json
import rpc.*
import utils.EmbeddedScripts


fun onFridaMessage(
    script: CPointer<FridaScript>?,
    message: CPointer<gcharVar>?,
    data: CPointer<GBytes>?,
    userData: gpointer?
) {
    val jsonStr = message?.toKString() ?: return

    // O Frida envelopa o retorno RPC assim:
    // {"type": "send", "payload": ["ok", "req-id", <resultado_json>]}
    // ou
    // {"type": "send", "payload": ["error", "req-id", <stacktrace>]}

    // NOTA: Em produção, use kotlinx.serialization para parsear isso de forma segura.
    // Aqui faremos uma extração simples para ilustrar o conceito.

    if (jsonStr.contains("\"payload\":[\"ok\"")) {
        // Extrair o ID e o resultado (idealmente usando um parser JSON real)
        val idRegex = "\"payload\":\\[\"ok\",\"([^\"]+)\"".toRegex()
        val match = idRegex.find(jsonStr)
        if (match != null) {
            val reqId = match.groupValues[1]
            // Acha onde o array do payload termina para extrair o objeto do resultado
            val payloadStart = jsonStr.indexOf(",\"", match.range.last) + 1
            val payloadEnd = jsonStr.lastIndexOf("]}")
            if (payloadStart in 1..<payloadEnd) {
                FridaRpcManager.pendingResponses[reqId] = jsonStr.substring(payloadStart, payloadEnd)
            } else {
                FridaRpcManager.pendingResponses[reqId] = "null"
            }
        }
    } else if (jsonStr.contains("\"payload\":[\"error\"")) {
        val idRegex = "\"payload\":\\[\"error\",\"([^\"]+)\"".toRegex()
        val match = idRegex.find(jsonStr)
        if (match != null) {
            val reqId = match.groupValues[1]
            FridaRpcManager.pendingErrors[reqId] = "RPC Error from Frida JS"
        }
    }
}
class NativeFridaBridge : FridaBridge {
    private var manager: CPointer<FridaDeviceManager>? = null
    private var session: CPointer<FridaSession>? = null
    private var script: CPointer<FridaScript>? = null
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        frida_init()
        manager = frida_device_manager_new()
        FridaRpcManager.clear()
    }

    override fun listClasses(searchParam: String, appPackage: String, offset: Int, limit: Int): List<String> {
        val jsonResult = invokeRpc("listclasses", listOf("\"$searchParam\""))
        return jsonParser.decodeFromString(jsonResult)
    }
    override fun countInstances(className: String): Int {
        val jsonResult = invokeRpc("countinstances", listOf("\"$className\""))
        return jsonResult.toIntOrNull() ?: -1
    }

    override fun inspectClass(className: String): ClassInspectionResult {
        val jsonResult = invokeRpc("inspectclass", listOf("\"$className\""))
        return jsonParser.decodeFromString(jsonResult)
    }

    override fun listInstances(className: String): ListInstancesResult {
        val jsonResult = invokeRpc("listinstances", listOf("\"$className\""))
        return jsonParser.decodeFromString(jsonResult)
    }

    override fun inspectInstance(className: String, id: String, offset: Int, limit: Int): InspectInstanceResult {
        val jsonResult = invokeRpc("inspectinstance", listOf("\"$className\"", "\"$id\"", "$offset", "$limit"))
        return jsonParser.decodeFromString(jsonResult)
    }

    override fun setFieldValue(className: String, id: String, fieldName: String, type: String, newValue: String): String {
        val safeValue = newValue.replace("\"", "\\\"")
        val jsonResult = invokeRpc("setfieldvalue", listOf("\"$className\"", "\"$id\"", "\"$fieldName\"", "\"$type\"", "\"$safeValue\""))
        return jsonResult
    }

    override fun hookMethod(className: String, methodSig: String): String {
        val jsonResult = invokeRpc("hookmethod", listOf("\"$className\"", "\"$methodSig\""))
        return jsonResult
    }

    override fun getHookEvents(): List<HookEvent> {
        val jsonResult = invokeRpc("gethookevents")
        return jsonParser.decodeFromString(jsonResult)
    }

    override fun setMethodImplementation(className: String, methodSig: String, code: String): String {
        val escapedCode = code.replace("\"", "\\\"").replace("\n", "\\n")
        val jsonResult = invokeRpc("setmethodimplementation", listOf("\"$className\"", "\"$methodSig\"", "\"$escapedCode\""))
        return jsonResult
    }

    override fun runOnce(className: String, methodSig: String, code: String): String {
        val escapedCode = code.replace("\"", "\\\"").replace("\n", "\\n")
        val jsonResult = invokeRpc("runonce", listOf("\"$className\"", "\"$methodSig\"", "\"$escapedCode\""))
        return jsonResult
    }

    override fun getInstanceAddresses(className: String): List<String> {
        val jsonResult = invokeRpc("getinstanceaddresses", listOf("\"$className\""))
        return jsonParser.decodeFromString(jsonResult)
    }

    override fun prepareEnvironment(target: String): PrepareEnvResult {
        memScoped {
            val error = allocPointerTo<GError>()
            
            // 1. Enumarar devices para garantir que o manager está pronto
            frida_device_manager_enumerate_devices_sync(manager, null, error.ptr)
            if (error.value != null) throw RuntimeException("Failed to enumerate devices: ${error.value?.pointed?.message?.toKString()}")
            
            // 2. Tentar encontrar o processo em dispositivos USB ou Local
            val deviceTypes = listOf(FridaDeviceType.FRIDA_DEVICE_TYPE_USB, FridaDeviceType.FRIDA_DEVICE_TYPE_LOCAL)
            var device: CPointer<FridaDevice>? = null
            var targetPid: UInt = 0u

            for (type in deviceTypes) {
                val d = frida_device_manager_get_device_by_type_sync(manager, type, 0, null, null) ?: continue
                
                val pid = target.toIntOrNull()
                if (pid != null) {
                    // Se for PID, assumimos este device (o primeiro que responder)
                    device = d
                    targetPid = pid.toUInt()
                    break
                } else {
                    // Tenta achar o processo pelo nome/identificador neste device
                    val process = frida_device_get_process_by_name_sync(d, target, null, null, null)
                    if (process != null) {
                        device = d
                        targetPid = frida_process_get_pid(process)
                        break
                    }
                }
            }

            if (device == null) {
                throw RuntimeException("Process '$target' not found on any USB or Local device. Try using the package name (e.g., co.stone.sample) instead of the display name.")
            }

            // 3. Attach ao processo
            session = frida_device_attach_sync(device, targetPid, null, null, error.ptr)
            if (session == null) throw RuntimeException("Failed to attach to target '$target' (PID $targetPid): ${error.value?.pointed?.message?.toKString()}")

            // 4. Criar e carregar o script
            val options = frida_script_options_new()
            frida_script_options_set_name(options, "agent")
            script = frida_session_create_script_sync(
                session,
                EmbeddedScripts.agent,
                options,
                null,
                error.ptr
            )

            if (script == null) throw RuntimeException("Failed to create script: ${error.value?.pointed?.message?.toKString()}")

            g_signal_connect_data(
                script!!.reinterpret(),
                "message",
                staticCFunction(::onFridaMessage).reinterpret(),
                null,
                null,
                0u
            )
            
            frida_script_load_sync(script, null, error.ptr)
            if (error.value != null) throw RuntimeException("Failed to load script: ${error.value?.pointed?.message?.toKString()}")
        }

        return PrepareEnvResult(0, "Ready", 0, "Attached to $target")
    }
    override fun injectGadgetFromScratch(withLogs: Boolean, limit: Int): InjectionProgressResult = InjectionProgressResult("not_implemented", emptyList())
    override fun injectJdwp(target: String, port: Int, packageName: String): String = "Not implemented yet"
    
    override fun healthCheck(): HealthCheckResponse {
        return HealthCheckResponse("degraded", mapOf("frida_native" to CheckResponse("warning", "Native Core initialized but methods are not yet implemented")))
    }

    override fun patchAndInstallIosApp(appPath: String): String = "Not implemented yet"
    override fun checkIosJailbreakStatus(serial: String): String = "Not implemented yet"
    override fun injectJailbrokenIos(serial: String): String = "Not implemented yet"
    override fun checkIosDeployStatus(): GenericStatusResult = GenericStatusResult("idle")

    private fun invokeRpc(methodName: String, args: List<String> = emptyList()): String {
        val scriptPtr = script ?: throw IllegalStateException("Frida script not loaded or attached.")
        val reqId = FridaRpcManager.generateReqId()

        val argsJson = args.joinToString(",")
        val rpcPayload = """["call", "$reqId", "$methodName", [$argsJson]]"""
        val fridaMsg = """{"type":"send","payload":$rpcPayload}"""

        FridaRpcManager.pendingResponses[reqId] = null
        frida_script_post(scriptPtr, fridaMsg, null)

        val context = g_main_context_default()
        while (FridaRpcManager.pendingResponses[reqId] == null) {
            if (FridaRpcManager.pendingErrors.containsKey(reqId)) {
                throw RuntimeException("JS Error: ${FridaRpcManager.pendingErrors.remove(reqId)}")
            }
            g_main_context_iteration(context, 1)
        }

        return FridaRpcManager.pendingResponses.remove(reqId)!!
    }
    companion object {
        object FridaRpcManager {
            private var reqCounter = 0
            val pendingResponses = mutableMapOf<String, String?>()
            val pendingErrors = mutableMapOf<String, String>()

            fun generateReqId(): String = "req-${reqCounter++}"

            fun clear() {
                pendingResponses.clear()
                pendingErrors.clear()
            }
        }
    }
}
