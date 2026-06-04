package bridge

import bridge.NativeFridaBridge.Companion.FridaRpcManager
import frida.*
import kotlinx.cinterop.*
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import rpc.*
import utils.EmbeddedScripts


fun onFridaMessage(
    script: CPointer<FridaScript>?,
    message: CPointer<gcharVar>?,
    data: CPointer<GBytes>?,
    userData: gpointer?
) {
    println("[DEBUG] Received Frida message: ${message?.toKString()}")
    val jsonStr = message?.toKString() ?: return

    // Log para debug
    if (!jsonStr.contains("class_chunk")) {
        platform.posix.fprintf(platform.posix.stderr, "[NativeBridge] Frida Message: %s\n", jsonStr)
        platform.posix.fflush(platform.posix.stderr)
    }

    // O Frida envelopa o retorno RPC assim:
    // {"type": "send", "payload": ["ok", "req-id", <resultado_json>]}
    
    if (jsonStr.contains("\"payload\":[\"ok\"")) {
        // Encontra o início do payload (após o ID do request)
        // Buscamos o segundo elemento do array (ID) e pegamos o que vem depois
        val parts = jsonStr.split(",")
        if (parts.size >= 3) {
            val reqIdPart = parts[1].trim().trim('"')
            // O resultado começa após a segunda vírgula do array payload
            // Vamos usar uma abordagem mais robusta: achar a segunda vírgula após '"ok"'
            val okIndex = jsonStr.indexOf("\"ok\"")
            val firstComma = jsonStr.indexOf(",", okIndex)
            val secondComma = jsonStr.indexOf(",", firstComma + 1)
            
            val payloadStart = secondComma + 1
            val payloadEnd = jsonStr.lastIndexOf("]}")
            
            if (payloadStart in 1..<payloadEnd) {
                val result = jsonStr.substring(payloadStart, payloadEnd).trim()
                val reqId = reqIdPart.removePrefix("req-") // Se o split pegou o ID sujo
                // Melhor: usar regex apenas para o ID
                val idMatch = "\"req-(\\d+)\"".toRegex().find(jsonStr)
                val finalId = if (idMatch != null) "req-${idMatch.groupValues[1]}" else reqIdPart
                
                FridaRpcManager.pendingResponses[finalId] = result
            }
        }
    } else if (jsonStr.contains("\"payload\":[\"error\"")) {
        val idMatch = "\"req-(\\d+)\"".toRegex().find(jsonStr)
        if (idMatch != null) {
            val reqId = "req-${idMatch.groupValues[1]}"
            FridaRpcManager.pendingErrors[reqId] = "RPC Error from Frida JS"
        }
    } else if (jsonStr.contains("\"class_chunk\"")) {
        val chunkRegex = "\"chunk\":\\[(.*)\\]".toRegex()
        val match = chunkRegex.find(jsonStr)
        if (match != null) {
            val classes = match.groupValues[1].split(",").map { it.trim('"') }.filter { it.isNotEmpty() }
            FridaRpcManager.onChunkReceived?.invoke(classes)
        }
    } else if (jsonStr.contains("\"class_stream_end\"")) {
        FridaRpcManager.isStreamCompleted = true
    }
}
class NativeFridaBridge : FridaBridge, AutoCloseable {
    private var manager: CPointer<FridaDeviceManager>? = null
    private var session: CPointer<FridaSession>? = null
    private var script: CPointer<FridaScript>? = null
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val fridaCoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        frida_init()
        manager = frida_device_manager_new()
        FridaRpcManager.clear()
    }

    override fun listClasses(searchParam: String, appPackage: String, offset: Int, limit: Int): List<String> {
        val jsonResult = invokeRpc("listclasses", listOf("\"$searchParam\"", "$offset", "$limit"))
        return jsonParser.decodeFromString(jsonResult)
    }
    
    override fun pingJava(): String {
        return try {
            invokeRpc("pingjava")
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    override fun testRpc(): String {
        return try {
            invokeRpc("testrpc")
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    override fun listClassesStream(searchParam: String, onChunk: (List<String>) -> Unit, onComplete: () -> Unit) {
        val scriptPtr = script ?: throw IllegalStateException("Frida script not loaded or attached.")
        
        FridaRpcManager.onChunkReceived = onChunk
        FridaRpcManager.isStreamCompleted = false

        val rpcPayload = """["call", "stream-0", "listclassesstream", ["\"$searchParam\"", "stream-0"]]"""
        val fridaMsg = """{"type":"send","payload":$rpcPayload}"""
        
        platform.posix.fprintf(platform.posix.stdout, "[NativeBridge] INFO: Posting stream request. searchParam=%s\n", searchParam)
        platform.posix.fflush(platform.posix.stdout)

        println("[DEBUG] Posting stream request. searchParam=$searchParam")
        frida_script_post(scriptPtr, fridaMsg, null)


        val context = g_main_context_default()
        val start = platform.posix.time(null)
        while (!FridaRpcManager.isStreamCompleted) {
            // Processa eventos pendentes do Frida
            g_main_context_iteration(context, 0)
            
            val elapsed = platform.posix.time(null) - start
            if (elapsed > 30) {
                platform.posix.fprintf(platform.posix.stderr, "[NativeBridge] ERROR: Stream timed out after 30s. isStreamCompleted=%s\n", FridaRpcManager.isStreamCompleted.toString())
                platform.posix.fflush(platform.posix.stderr)
                break
            }
            platform.posix.usleep(10000u) // 10ms para não travar a CPU e permitir mensagens
        }
        
        FridaRpcManager.onChunkReceived = null
        onComplete()
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

    override fun prepareEnvironment(target: String, pid: Int?): PrepareEnvResult {
        memScoped {
            println("[DEBUG] Preparing environment for $target...")
            val error = allocPointerTo<GError>()
            
            println("[DEBUG] Enumerating devices...")
            frida_device_manager_enumerate_devices_sync(manager, null, error.ptr)
            if (error.value != null) throw RuntimeException("Failed to enumerate devices: ${error.value?.pointed?.message?.toKString()}")
            var device: CPointer<FridaDevice>? = null
            var targetPid: UInt? = pid?.toUInt()

            if (target == "Gadget" || target == "127.0.0.1") {
                // Modo Remoto (Gadget injetado via JDWP/TCP)
                val options = frida_remote_device_options_new()
                platform.posix.fprintf(platform.posix.stderr, "[NativeFridaBridge] Connecting to remote device at 127.0.0.1:27042...\n")
                device = frida_device_manager_add_remote_device_sync(manager, "127.0.0.1:27042", options, null, error.ptr)
                if (device == null) {
                    val msg = error.value?.pointed?.message?.toKString() ?: "Unknown error"
                    throw RuntimeException("Frida Gadget not found at 127.0.0.1:27042. Error: $msg")
                }
                
                platform.posix.fprintf(platform.posix.stderr, "[NativeFridaBridge] Remote device added. Searching for 'Gadget' process...\n")
                // No modo Gadget remoto, o nome do processo é fixo como "Gadget"
                var process: CPointer<FridaProcess>? = null
                for (i in 0..10) {
                    process = frida_device_get_process_by_name_sync(device, "Gadget", null, null, null)
                    if (process != null) break
                    platform.posix.fprintf(platform.posix.stderr, "[NativeFridaBridge] 'Gadget' process not found, retrying ($i/10)...\n")
                    platform.posix.sleep(1u)
                }
                
                if (process == null) throw RuntimeException("Could not find 'Gadget' process on remote device after 10 retries")
                targetPid = frida_process_get_pid(process)
                platform.posix.fprintf(platform.posix.stderr, "[NativeFridaBridge] Found 'Gadget' process with PID $targetPid\n")
            } else {
                val deviceTypes = listOf(FridaDeviceType.FRIDA_DEVICE_TYPE_USB, FridaDeviceType.FRIDA_DEVICE_TYPE_LOCAL)
                println("[DEBUG] Enumerating devices of types: $deviceTypes")
                for (type in deviceTypes) {

                    val d = frida_device_manager_get_device_by_type_sync(manager, type, 0, null, null) ?: continue

                    if (targetPid != null) {
                        device = d
                        break
                    }
                    val pid = target.toIntOrNull()
                    if (pid != null) {
                        targetPid = pid.toUInt()
                        break
                    } else {
                        val process = frida_device_get_process_by_name_sync(d, target, null, null, null)
                        if (process != null) {
                            targetPid = frida_process_get_pid(process)
                            break
                        }
                    }
                }
            }

            if (device == null) {
                throw RuntimeException("Process '$target' not found on any USB or Local device.")
            }

            // 3. Attach ao processo
            session = frida_device_attach_sync(device, targetPid!!, null, null, error.ptr)
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
    override fun injectGadgetFromScratch(withLogs: Boolean, limit: Int): InjectionProgressResult {
        val adb = AdbManagerImpl()
        val devices = try { adb.listDevices() } catch (e: Exception) { emptyList() }
        if (devices.isEmpty()) {
            return InjectionProgressResult("error", listOf(InjectionStep("get_target", "No devices found", "error")), error_message = "No ADB devices connected")
        }
        
        val serial = devices.first()
        val steps = mutableListOf<InjectionStep>()
        
        return try {
            steps.add(InjectionStep("get_target", "Identify target application", "running"))
            val (pkg, pid) = AndroidEnv.getFrontmostApp(serial)
            steps[0] = steps[0].copy(status = "completed")

            val isRooted = AndroidEnv.isRooted(serial)
            val isDebuggable = AndroidEnv.isDebuggable(serial, pkg)
            println("[DEBUG] isRooted: $isRooted, isDebuggable: $isDebuggable")
            if (isRooted) {
                // Root Path (frida-server)
                steps.add(InjectionStep("prepare_server", "Prepare frida-server on device", "running"))

                val arch = utils.Shell.execute("adb -s $serial shell getprop ro.product.cpu.abi").output.trim()
                println("[DEBUG] Device architecture: $arch")
                val mappedArch = utils.BinaryManager.mapArch(arch)
                
                val serverLocal = kotlinx.coroutines.runBlocking { 
                    utils.BinaryManager.ensureBinary("frida-server", mappedArch, "xz") 
                }
                println("[DEBUG] Server local path: $serverLocal")
                adb.pushFile(serial, serverLocal, "/data/local/tmp/frida-server")
                println("[DEBUG] Server pushed to device")
                adb.executeShellCommand(serial, "chmod 755 /data/local/tmp/frida-server")
                println("[DEBUG] Server made executable")
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")
                steps.add(InjectionStep("start_server", "Start frida-server as root", "running"))
                try {
                    adb.executeShellCommand(serial, "su -c 'pkill -f frida-server 2>/dev/null || true'")
                } catch (e: Exception){}
                println("[DEBUG] Killed existing frida-server processes (if any)")
                try {
                    fridaCoroutineScope.launch(Dispatchers.IO) {
                        adb.executeShellCommand(serial, "su -c 'nohup /data/local/tmp/frida-server &'")
                    }
                } catch (e: Exception) {}
                println("[DEBUG] Started frida-server as root")
                platform.posix.sleep(2u)
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")
                
                steps.add(InjectionStep("load_agent", "Attach to process and load agent", "running"))
                println("[DEBUG] Loading agent")
                prepareEnvironment(pkg, pid)
                println("[DEBUG] Agent loaded successfully")
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")
                
                InjectionProgressResult("completed", steps)
            } else if (isDebuggable) {
                // Gadget Path (JDWP)
                steps.add(InjectionStep("setup_adb", "Configure ADB port forwards", "running"))
                adb.removeForwardAll(serial)
                adb.forwardJdwp(serial, 5005, pid)
                adb.forwardPort(serial, 27042, 27042)
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")
                
                steps.add(InjectionStep("push_gadget", "Push Gadget library to device", "running"))
                val arch = utils.Shell.execute("adb -s $serial shell getprop ro.product.cpu.abi").output.trim()
                val mappedArch = utils.BinaryManager.mapArch(arch)
                val gadgetLocal = kotlinx.coroutines.runBlocking { 
                    utils.BinaryManager.ensureBinary("frida-gadget", mappedArch, "so") 
                }
                // JdwpManager.load already handles pushing to /data/local/tmp/ and then copying inside
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")
                
                steps.add(InjectionStep("inject_jdwp", "Trigger JDWP gadget injection", "running"))
                val jdwp = JdwpManagerImpl(adb)
                val jdwpRes = jdwp.load("127.0.0.1", 5005, gadgetLocal, null, pkg, serial)
                if (jdwpRes.isFailure) throw jdwpRes.exceptionOrNull()!!
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")
                
                steps.add(InjectionStep("load_agent", "Load Frida instrumentation agent", "running"))
                // Give some time for the gadget to start its TCP server after JDWP resume
                platform.posix.fprintf(platform.posix.stderr, "[NativeFridaBridge] JDWP injection complete. Waiting 5s for Gadget to start TCP server...\n")
                platform.posix.sleep(5u)
                
                // Connect to the gadget we just injected
                prepareEnvironment("Gadget",) // When gadget is listening on 27042, Frida sees it as "Gadget" process
                platform.posix.fprintf(platform.posix.stderr, "[NativeFridaBridge] Agent loaded successfully via Gadget.\n")
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")
                
                InjectionProgressResult("completed", steps)
            } else {
                throw Exception("App '$pkg' is not debuggable and device is not rooted. Use a rooted device or a debuggable app.")
            }
        } catch (e: Exception) {
            println("[NativeFridaBridge] Error: ${e.message}")
            val lastStep = steps.lastOrNull()
            if (lastStep != null && lastStep.status == "running") {
                steps[steps.size - 1] = lastStep.copy(status = "error")
            }
            InjectionProgressResult("error", steps, error_message = e.message)
        }
    }
    
    override fun injectJdwp(target: String, port: Int, packageName: String): String {
        val adbManager = AdbManagerImpl()
        val jdwpManager = JdwpManagerImpl(adbManager)
        
        // Use default path for gadget
        val home = platform.posix.getenv("HOME")?.toKString() ?: "/tmp"
        val libraryPath = "$home/.cache/barbatos/frida-gadget.so"
        
        // Serial is target if it's localhost or an IP, otherwise we might need to list devices
        // For now, let's assume serial is the target if it's an IP, or null to let ADB decide.
        val serial = if (target == "127.0.0.1" || target == "localhost") "" else target
        
        val result = jdwpManager.load(
            target = target,
            port = port,
            libraryPath = libraryPath,
            packageName = packageName,
            serial = serial
        )
        
        return if (result.isSuccess) "Success" else "Error: ${result.exceptionOrNull()?.message}"
    }
    
    override fun healthCheck(): HealthCheckResponse {
        val checks = mutableMapOf<String, CheckResponse>()
        val adb = AdbManagerImpl()
        var serial: String? = null
        val devices = try { adb.listDevices() } catch (e: Exception) { emptyList() }
        
        if (devices.isNotEmpty()) {
            serial = devices.first()
            checks["adb"] = CheckResponse("ok", "Device state: device")
        } else {
            checks["adb"] = CheckResponse("error", "adb get-state failed: no devices/emulators found", fix = "Run: adb devices — ensure a device is listed as 'device'")
        }

        var isRooted: Boolean? = null
        var isDebuggable: Boolean? = null
        var device: CPointer<FridaDevice>? = null
        var pid: Int? = null

        if (serial != null) {
            try {
                val rootRes = utils.Shell.execute("adb -s $serial shell su -c id")
                isRooted = rootRes.output.contains("uid=0")
                checks["android_root"] = CheckResponse(if (isRooted == true) "ok" else "info", if (isRooted == true) "Device is rooted" else "Device probably not rooted")
            } catch (e: Exception) {
                checks["android_root"] = CheckResponse("unknown", "Could not check root: ${e.message}")
            }

            try {
                val topRes = utils.Shell.execute("adb -s $serial shell dumpsys window | grep -E \"mCurrentFocus\" | xargs | cut -d' ' -f3 | cut -d'/' -f1 | tr -d ' \\r\\n'")
                val pkgName = topRes.output.trim()
                if (pkgName.isNotBlank()) {
                    val pidOutput = utils.Shell.execute("adb -s $serial shell pidof $pkgName").output.trim().takeIf { it.isNotBlank() }
                        ?: throw Exception("PID not found for package '$pkgName'")

                    pid = pidOutput.toIntOrNull()
                        ?: throw Exception("Could not fetch frontmost app PID: '$pidOutput'")

                    val debuggableRes = try {
                        utils.Shell.execute("adb -s $serial shell run-as $pkgName id")
                    } catch (e: Exception) { utils.ShellResult("", -1) }
                    isDebuggable = debuggableRes.output.contains("uid=")

                    val status = if (isDebuggable || isRooted == true) "ok" else "warning"
                    checks["android_frontmost_app"] = CheckResponse(
                        status = status,
                        message = "App on screen: $pkgName (Debuggable: $isDebuggable)",
                        fix = null,
                        `package` = pkgName,
                        pid = pid,
                        debuggable = isDebuggable
                    )
                } else {
                    checks["android_frontmost_app"] = CheckResponse("unknown", "Could not fetch frontmost app: Parse failed")
                }
            } catch (e: Exception) {
                checks["android_frontmost_app"] = CheckResponse("unknown", "Could not fetch frontmost app: ${e.message}")
            }
        }
        if ((checks["android_root"]?.status != "ok") && isDebuggable == false) {
            checks["android_frontmost_app"] = checks["android_frontmost_app"]!!.copy(
                status = "error",
                fix = "Frontmost app is not debuggable and device is not rooted, please open a debuggable app on the device and try again"
            )
        }

        try {
            memScoped {
                val error = allocPointerTo<GError>()
                frida_device_manager_enumerate_devices_sync(manager, null, error.ptr)
                if (error.value != null) throw RuntimeException(error.value?.pointed?.message?.toKString())
                device = frida_device_manager_get_device_by_type_sync(manager, FridaDeviceType.FRIDA_DEVICE_TYPE_USB, 0, null, null)

            }
            checks["frida_device"] = CheckResponse("ok", "Frida device enumeration succeeded")
        } catch (e: Exception) {
            checks["frida_device"] = CheckResponse("error", "Frida device enumeration failed: ${e.message}", fix = "Check USB debugging is enabled; re-run")
        }

        try {
            if (session != null) {
                val isDetached = frida_session_is_detached(session) != 0
                checks["frida_connection"] = CheckResponse("ok", "Frida session already exists")
                checks["session"] = CheckResponse(if (isDetached) "error" else "ok", if (isDetached) "Session is detached" else "Session is active")
            } else {
                try {
                    memScoped {
                        val error = allocPointerTo<GError>()
                        frida_device_attach_sync(device, pid!!.toUInt(), null, null, error.ptr)
                    }
                    checks["frida_connection"] = CheckResponse("warning", "Successfully attached to the frontmost application but no session exists", fix = "Please call prepareEnvironment to start session")
                    checks["session"] = CheckResponse("skipped", "No active session (injection not yet run)")
                } catch (error: Exception) {
                    checks["frida_connection"] = CheckResponse("error", "Failed to attach to the frontmost application, error: $error", fix = "Please call prepareEnvironment to attach to the frontmost application")
                    checks["session"] = CheckResponse("skipped", "No active session (injection not yet run)")
                }
            }
        } catch (e: Exception) {
            checks["frida_connection"] = CheckResponse("error", "Frida device connection failed: ${e.message}", fix = "Check USB debugging is enabled; re-run")
            checks["session"] = CheckResponse("skipped", "No active session (injection not yet run)")
        }


        checks["injection"] = CheckResponse("ok", "No injection running")

        val overall = if (checks.values.any { it.status == "error" }) "degraded" else "ok"
        val recommendations = checks.values
            .sortedBy { it.status }
            .filter { (it.status == "warning" || it.status == "error") && it.fix != null }.mapNotNull { it.fix }
        val recommendation = recommendations.firstOrNull() ?: if (checks["frida_connection"]?.status == "ok") "Ready to receive debug commands." else null

        return HealthCheckResponse(overall, checks, recommendation)
    }

    override fun patchAndInstallIosApp(appPath: String): String = "Not implemented yet"
    override fun checkIosJailbreakStatus(serial: String): String = "Not implemented yet"
    override fun injectJailbrokenIos(serial: String): String = "Not implemented yet"
    override fun checkIosDeployStatus(): GenericStatusResult = GenericStatusResult("idle")

    private fun invokeRpc(methodName: String, args: List<String> = emptyList()): String {
        val scriptPtr = script ?: throw IllegalStateException("Frida script not loaded or attached.")
        val reqId = FridaRpcManager.generateReqId()
        println("[DEBUG] Invoking RPC: $methodName ($reqId)")
 
        val argsJson = args.joinToString(",")
        val rpcPayload = """["call", "$reqId", "$methodName", [$argsJson]]"""
        val fridaMsg = """{"type":"send","payload":$rpcPayload}"""
 
        platform.posix.fprintf(platform.posix.stdout, "[NativeBridge] Calling RPC: %s\n", fridaMsg)
        platform.posix.fflush(platform.posix.stdout)

        FridaRpcManager.pendingResponses[reqId] = null

        frida_script_post(scriptPtr, fridaMsg, null)

        val context = g_main_context_default()
        val start = platform.posix.time(null)
        while (FridaRpcManager.pendingResponses[reqId] == null) {
            println("[DEBUG] Waiting for RPC response for $reqId...")
            if (FridaRpcManager.pendingErrors.containsKey(reqId)) {
                throw RuntimeException("JS Error: ${FridaRpcManager.pendingErrors.remove(reqId)}")
            }
            g_main_context_iteration(context, 0)
            if (platform.posix.time(null) - start > 15) {
                throw RuntimeException("RPC Timeout for $methodName ($reqId)")
            }
            platform.posix.usleep(1000u)
        }
 
        return FridaRpcManager.pendingResponses.remove(reqId)!!
    }

    override fun close() {
        fridaCoroutineScope.cancel()
    }

    companion object {
        object FridaRpcManager {
            private var reqCounter = 0
            val pendingResponses = mutableMapOf<String, String?>()
            val pendingErrors = mutableMapOf<String, String>()
            
            var onChunkReceived: ((List<String>) -> Unit)? = null
            var isStreamCompleted = false

            fun generateReqId(): String = "req-${reqCounter++}"

            fun clear() {
                pendingResponses.clear()
                pendingErrors.clear()
                onChunkReceived = null
                isStreamCompleted = false
            }
        }
    }
}
