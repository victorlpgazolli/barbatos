package bridge

import device.AdbManagerImpl
import device.AndroidEnv
import device.JdwpManagerImpl
import frida.*
import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import model.actions.params.*
import model.actions.result.*
import model.bridge.FridaBridge
import model.bridge.FridaMessage
import model.bridge.FridaPayload
import utils.EmbeddedScripts

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class NativeFridaBridge : FridaBridge, AutoCloseable {
    private var manager: CPointer<FridaDeviceManager>? = null
    private var device: CPointer<FridaDevice>? = null
    private var session: CPointer<FridaSession>? = null
    private var script: CPointer<FridaScript>? = null

    override val jsonParser = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val fridaCoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        frida_init()
        manager = frida_device_manager_new()
        FridaRpcManager.clear()
    }

    private fun checkError(error: CPointer<CPointerVar<GError>>) {
        val errorValue = error.pointed.value
        if (errorValue != null) {
            val message = errorValue.pointed.message?.toKString() ?: "Unknown Frida Error"
            g_error_free(errorValue)
            throw RuntimeException(message)
        }
    }

    override fun pingJava(): String = try { invokeRpc("pingjava") } catch (e: Exception) { "error: ${e.message}" }

    override fun testRpc(): String = try { invokeRpc("testrpc") } catch (e: Exception) { "error: ${e.message}" }

    override fun listClassesStream(
        params: ListClassesParams,
        onChunk: suspend (partialResult: ListClassesPartialResult) -> Unit,
        onComplete: () -> Unit
    ) {
        val scriptPtr = script ?: throw IllegalStateException("Frida script not loaded or attached.")

        FridaRpcManager.onChunkReceived = { classes ->
            fridaCoroutineScope.launch {
                onChunk(
                    ListClassesPartialResult(classes)
                )
            }
        }
        FridaRpcManager.isStreamCompleted = false

        val context = g_main_context_default()
        val start = platform.posix.time(null)

        scriptPtr.sendRpcRequest(
            reqId = "stream-0",
            method = "listclassesstream",
            params.searchParam,
            "stream-0"
        )

        try {
            while (!FridaRpcManager.isStreamCompleted) {
                g_main_context_iteration(context, 0)
                val elapsed = platform.posix.time(null) - start
                if (elapsed > 30) {
                    platform.posix.fprintf(
                        platform.posix.stderr,
                        "[NativeBridge] ERROR: Stream timed out after 30s.\n"
                    )
                    platform.posix.fflush(platform.posix.stderr)
                    break
                }
                platform.posix.usleep(10000u)
            }
        } finally {
            FridaRpcManager.onChunkReceived = null
            FridaRpcManager.isStreamCompleted = true
            onComplete()
        }
    }

    override fun countInstances(params: CountInstancesParams): CountInstancesResult {
        val jsonResult = invokeRpc("countinstances", listOf(params.className))
        return CountInstancesResult(
            count = jsonResult.toIntOrNull() ?: -1
        )
    }

    override fun inspectClass(params: InspectClassParams): InspectClassResult {
        val jsonResult = invokeRpc("inspectclass", listOf(params.className))
        return jsonParser.decodeFromString(jsonResult)
    }

    override fun listInstances(params: ListInstancesParams): ListInstancesResult {
        val jsonResult = invokeRpc("listinstances", listOf(params.className))
        return jsonParser.decodeFromString(jsonResult)
    }

    override fun inspectInstance(params: InspectInstanceParams): InspectInstanceResult {
        val jsonResult = invokeRpc(
            "inspectinstance",
            listOf(
                params.id,
                params.offset.toString(),
                params.limit.toString(),
            )
        )
        return jsonParser.decodeFromString(jsonResult)
    }

    override fun setFieldValue(params: SetFieldValueParams): SetFieldValueResult {
        val safeValue = params.newValue.replace("\"", "\\\"")
        return SetFieldValueResult(
            status = invokeRpc(
                "setfieldvalue",
                listOf(params.className, params.id, params.fieldName, params.type, safeValue)
            )
        )
    }

    override fun hookMethod(params: HookParams): HookMethodResult {
        return HookMethodResult(
            status = invokeRpc("hookmethod", listOf(params.className, params.methodSig))
        )
    }

    override fun getHookEvents(): HookEventsResult {
        val jsonResult = invokeRpc("gethookevents")
        val events: List<HookEvent> = jsonParser.decodeFromString(jsonResult)
        return HookEventsResult(events = events)
    }

    override fun setMethodImplementation(params: SetMethodImplementationParams): SetMethodImplementationResult {
        val escapedCode = params.code.replace("\"", "\\\"").replace("\n", "\\n")
        return SetMethodImplementationResult(
            status = invokeRpc(
                "setmethodimplementation",
                listOf(params.className, params.methodSig, escapedCode)
            )
        )
    }

    override fun runOnce(params: RunOnceParams): RunOnceResult {
        val escapedCode = params.code.replace("\"", "\\\"").replace("\n", "\\n")
        return RunOnceResult(
            status = invokeRpc("runonce", listOf(params.className, params.methodSig, escapedCode))
        )
    }

    override fun getInstanceAddresses(params: GetInstanceAddressesParams): GetInstanceAddressesResult {
        val jsonResult = invokeRpc("getinstanceaddresses", listOf(params.className))
        return GetInstanceAddressesResult(
            addresses = jsonParser.decodeFromString(jsonResult)
        )
    }

    override fun prepareEnvironment(params: PrepareEnvParams): PrepareEnvResult {
        memScoped {
            val error = allocPointerTo<GError>()

            frida_device_manager_enumerate_devices_sync(manager, null, error.ptr)
            checkError(error.ptr)

            var targetPid: UInt? = params.pid?.toUInt()

            if (params.target == "Gadget" || params.target == "127.0.0.1") {
                val options = frida_remote_device_options_new()
                device = frida_device_manager_add_remote_device_sync(manager, "127.0.0.1:27042", options, null, error.ptr)
                g_object_unref(options)
                checkError(error.ptr)

                var process: CPointer<FridaProcess>? = null
                for (i in 0..10) {
                    process = frida_device_get_process_by_name_sync(device, "Gadget", null, null, null)
                    if (process != null) break
                    platform.posix.sleep(1u)
                }

                if (process == null) throw RuntimeException("Could not find 'Gadget' process on remote device, try running rpcInjectGadgetFromScratch first")
                targetPid = frida_process_get_pid(process)
                g_object_unref(process)
            } else if (params.serial != null) {
                // When a specific device serial is provided, look it up by ID directly
                // instead of iterating by type — avoids picking the wrong device when
                // multiple USB devices are connected.
                val d = frida_device_manager_get_device_by_id_sync(manager, params.serial, 5000, null, error.ptr)
                checkError(error.ptr)
                if (d != null) {
                    val p = if (targetPid != null) {
                        frida_device_get_process_by_pid_sync(d, targetPid, null, null, null)
                    } else {
                        val processName = params.target ?: params.packageName
                        processName.toIntOrNull()?.let { frida_device_get_process_by_pid_sync(d, it.toUInt(), null, null, null) }
                            ?: frida_device_get_process_by_name_sync(d, processName, null, null, null)
                    }
                    if (p != null) {
                        device = d
                        targetPid = frida_process_get_pid(p)
                        g_object_unref(p)
                    } else {
                        g_object_unref(d)
                    }
                }
            } else {
                val deviceTypes = listOf(FridaDeviceType.FRIDA_DEVICE_TYPE_USB, FridaDeviceType.FRIDA_DEVICE_TYPE_LOCAL)
                for (type in deviceTypes) {
                    val d = frida_device_manager_get_device_by_type_sync(manager, type, 0, null, null) ?: continue

                    val p = if (targetPid != null) {
                        frida_device_get_process_by_pid_sync(d, targetPid, null, null, null)
                    } else {
                        val processName = params.target ?: params.packageName
                        processName.toIntOrNull()?.let { frida_device_get_process_by_pid_sync(d, it.toUInt(), null, null, null) }
                            ?: frida_device_get_process_by_name_sync(d, processName, null, null, null)
                    }

                    if (p != null) {
                        device = d
                        targetPid = frida_process_get_pid(p)
                        g_object_unref(p)
                        break
                    } else {
                        g_object_unref(d)
                    }
                }
            }

            if (device == null || targetPid == null) {
                val identifier = params.serial ?: params.target ?: params.packageName
                throw RuntimeException("Process not found on device '$identifier' (pid=${params.pid}, pkg=${params.packageName}).")
            }

            session = frida_device_attach_sync(device, targetPid, null, null, error.ptr)
            checkError(error.ptr)

            val options = frida_script_options_new()
            frida_script_options_set_name(options, "agent")
            script = frida_session_create_script_sync(session, EmbeddedScripts.agent, options, null, error.ptr)
            g_object_unref(options)
            checkError(error.ptr)

            g_signal_connect_data(script!!.reinterpret(), "message", staticCFunction(::onFridaMessage).reinterpret(), null, null, 0u)

            frida_script_load_sync(script, null, error.ptr)
            checkError(error.ptr)
        }

        return PrepareEnvResult("Attached to ${params.target}, ready to receive commands")
    }

    override fun injectGadgetFromScratch(params: InjectGadgetParams): InjectGadgetResult {
        val adb = AdbManagerImpl()
        val devices = try { adb.listDevices() } catch (e: Exception) { emptyList() }
        if (devices.isEmpty()) {
            return InjectGadgetResult(
                status = "error",
                steps = listOf(InjectionStep("get_target", "No devices found", "error")),
                errorMessage = "No ADB devices connected"
            )
        } else {
            if (params.serial?.isNotEmpty() == true && !devices.contains(params.serial)) {
                return InjectGadgetResult(
                    status = "error",
                    steps = listOf(InjectionStep("get_target", "Device not found", "error")),
                    errorMessage = "Device '${params.serial}' not found"
                )
            }
        }
        val serial = params.serial
            ?.takeIf { it.isNotEmpty() }
            ?: devices.first()

        val steps = mutableListOf<InjectionStep>()

        return try {
            steps.add(InjectionStep("get_target", "Identify target application", "running"))
            val (pkg, pid) = AndroidEnv.getFrontmostApp(serial)
            steps[0] = steps[0].copy(status = "completed")

            val isRooted = AndroidEnv.isRooted(serial)
            val isDebuggable = AndroidEnv.isDebuggable(serial, pkg)

            if (isRooted) {
                steps.add(
                    InjectionStep(
                        "prepare_server",
                        "Prepare frida-server on device",
                        "running"
                    )
                )
                val arch = utils.Shell.execute("adb -s $serial shell getprop ro.product.cpu.abi").output.trim()
                val mappedArch = utils.BinaryManager.mapArch(arch)

                val serverLocal = kotlinx.coroutines.runBlocking {
                    utils.BinaryManager.ensureBinary("frida-server", mappedArch, "xz")
                }
                adb.pushFile(serial, serverLocal, "/data/local/tmp/frida-server")
                adb.executeShellCommand(serial, "chmod 755 /data/local/tmp/frida-server")
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")

                steps.add(InjectionStep("start_server", "Start frida-server as root", "running"))
                try { adb.executeShellCommand(serial, "su -c 'pkill -f frida-server 2>/dev/null || true'") } catch (e: Exception){}

                // `pidof` exits non-zero (the standard Unix convention) when it finds no match,
                // and AdbManagerImpl.executeShellCommand treats any non-zero exit as a thrown
                // failure — so "no frida-server running" (the common/expected case) must be read
                // as an exception here, not a real error.
                fun pidofFridaServerOrEmpty(): String =
                    try { adb.executeShellCommand(serial, "pidof frida-server").trim() } catch (e: Exception) { "" }

                // Confirm no frida-server instance survived the kill before starting a new one.
                // Earlier/aborted injection attempts could have left one running detached on the
                // device (killing barbatos on the host doesn't kill a process it started remotely
                // via adb), and a stale instance can hold the port a fresh one needs.
                for (attempt in 1..10) {
                    val remaining = pidofFridaServerOrEmpty()
                    if (remaining.isEmpty()) break
                    try { adb.executeShellCommand(serial, "su -c 'kill -9 $remaining 2>/dev/null || true'") } catch (e: Exception) {}
                    platform.posix.usleep(250_000u)
                }

                // Launch frida-server detached/backgrounded ON THE DEVICE (nohup + trailing `&`)
                // so this adb shell command returns immediately once the remote shell forks,
                // instead of blocking forever on the daemon's lifetime. The previous approach ran
                // frida-server in the foreground and wrapped the blocking call in a Kotlin
                // coroutine that was never awaited nor cancelled — the orphaned coroutine kept
                // occupying a Dispatchers.IO thread indefinitely, and repeated injection attempts
                // (e.g. after a failed attach) could leave multiple such blocked threads/adb
                // sessions around, causing later calls to hang.
                adb.executeShellCommand(serial, "su -c 'nohup /data/local/tmp/frida-server >/dev/null 2>&1 &'")

                // Poll for readiness instead of a single fixed sleep — verifies frida-server is
                // actually up (not just that the launch command returned) before moving on,
                // which matters more on older/slower devices.
                var serverReady = false
                for (attempt in 1..20) {
                    val pidCheck = pidofFridaServerOrEmpty()
                    if (pidCheck.isNotEmpty() && pidCheck.toIntOrNull() != null) {
                        serverReady = true
                        break
                    }
                    platform.posix.usleep(250_000u)
                }
                if (!serverReady) {
                    throw RuntimeException("frida-server did not start within 5s on device '$serial'.")
                }
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")

                steps.add(
                    InjectionStep(
                        "load_agent",
                        "Attach to process (pid=$pid, pkg=$pkg) and load agent",
                        "running"
                    )
                )
                prepareEnvironment(
                    PrepareEnvParams(
                        pid = pid,
                        packageName = pkg,
                        serial = serial
                    )
                )
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")

                InjectGadgetResult("completed", steps)
            } else if (isDebuggable) {
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
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")

                steps.add(InjectionStep("inject_jdwp", "Trigger JDWP gadget injection", "running"))
                val jdwp = JdwpManagerImpl(adb)
                val jdwpRes = jdwp.load("127.0.0.1", 5005, gadgetLocal, null, pkg, serial)
                if (jdwpRes.isFailure) throw jdwpRes.exceptionOrNull()!!
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")

                steps.add(
                    InjectionStep(
                        "load_agent",
                        "Load Frida instrumentation agent",
                        "running"
                    )
                )
                platform.posix.sleep(5u)
                prepareEnvironment(
                    PrepareEnvParams(
                        pid = pid,
                        packageName = pkg,
                        target = "Gadget",
                        serial = serial
                    )
                )
                steps[steps.size - 1] = steps[steps.size - 1].copy(status = "completed")

                InjectGadgetResult("completed", steps)
            } else {
                throw Exception("App '$pkg' is not debuggable and device is not rooted.")
            }
        } catch (e: Exception) {
            val lastStep = steps.lastOrNull()
            if (lastStep != null && lastStep.status == "running") {
                steps[steps.size - 1] = lastStep.copy(status = "error")
            }
            InjectGadgetResult("error", steps, errorMessage = e.message)
        }
    }

    override fun injectJdwp(params: InjectJdwpParams): InjectJdwpResult {
        val adbManager = AdbManagerImpl()
        val jdwpManager = JdwpManagerImpl(adbManager)
        val home = platform.posix.getenv("HOME")?.toKString() ?: "/tmp"
        val libraryPath = "$home/.cache/barbatos/frida-gadget.so"
        val serial = if (params.target == "127.0.0.1" || params.target == "localhost") "" else params.target

        val result = jdwpManager.load(
            target = params.target,
            port = params.port,
            libraryPath = libraryPath,
            breakOn = null,
            packageName = params.packageName,
            serial = serial,
        )
        return InjectJdwpResult(
            status = "Success".takeIf { result.isSuccess }
                ?: "Error: ${result.exceptionOrNull()?.message}",
        )
    }

    override fun healthCheck(): HealthCheckResult {
        val checks = mutableMapOf<String, CheckResponse>()
        val adb = AdbManagerImpl()
        var serial: String? = null
        val devices = try { adb.listDevices() } catch (e: Exception) { emptyList() }

        if (devices.isNotEmpty()) {
            serial = devices.first()
            checks["adb"] = CheckResponse("ok", "Device state: device")
        } else {
            checks["adb"] = CheckResponse("error", "adb get-state failed", fix = "Run: adb devices")
        }

        var isRooted = false
        var isDebuggable = false
        var pid: Int? = null

        if (serial != null) {
            try {
                val rootRes = utils.Shell.execute("adb -s $serial shell su -c id")
                isRooted = rootRes.output.contains("uid=0")
                checks["android_root"] = CheckResponse(
                    if (isRooted) "ok" else "info",
                    if (isRooted) "Device is rooted" else "Device not rooted"
                )
            } catch (e: Exception) {
                checks["android_root"] = CheckResponse("unknown", "Root check failed")
            }

            try {
                val (pkgName, appPid) = AndroidEnv.getFrontmostApp(serial)
                pid = appPid
                val debuggableRes = try {
                    utils.Shell.execute("adb -s $serial shell run-as $pkgName id")
                } catch (e: Exception) { utils.ShellResult("", -1) }
                isDebuggable = debuggableRes.output.contains("uid=")

                checks["android_frontmost_app"] = CheckResponse(
                    status = if (isDebuggable || isRooted) "ok" else "warning",
                    message = "App: $pkgName (Debuggable: $isDebuggable)",
                    `package` = pkgName,
                    pid = pid,
                    debuggable = isDebuggable
                )
            } catch (e: Exception) {
                checks["android_frontmost_app"] =
                    CheckResponse("unknown", "Frontmost app check failed")
            }
        }

        try {
            memScoped {
                val error = allocPointerTo<GError>()
                frida_device_manager_enumerate_devices_sync(manager, null, error.ptr)
                checkError(error.ptr)
            }
            checks["frida_device"] = CheckResponse("ok", "Frida enumeration OK")
        } catch (e: Exception) {
            checks["frida_device"] =
                CheckResponse("error", "Frida enumeration failed", fix = "Check USB debugging")
        }

        if (session != null) {
            val isDetached = frida_session_is_detached(session) != 0
            checks["frida_connection"] = CheckResponse("ok", "Frida session active")
            checks["session"] = CheckResponse(
                if (isDetached) "error" else "ok",
                if (isDetached) "Detached" else "Active"
            )
        } else {
            checks["frida_connection"] = CheckResponse("warning", "No session")
            checks["session"] = CheckResponse("skipped", "No session")
        }

        val overall = if (checks.values.any { it.status == "error" }) "degraded" else "ok"
        return HealthCheckResult(overall, checks, null)
    }

    internal fun CPointer<FridaScript>.sendRpcRequest(reqId: String, method: String, vararg args: String) {
        val request = FridaPayload.RpcRequest(
            reqId = reqId,
            method = method,
            args = args.toList()
        )

        val jsonString = jsonParser.encodeToString(value = request, serializer = FridaPayload.RpcRequest.serializer())

        frida_script_post(this, jsonString, null)
    }

    private fun invokeRpc(methodName: String, args: List<String> = emptyList()): String {
        val scriptPtr = script ?: throw IllegalStateException("Frida script not loaded.")
        val reqId = FridaRpcManager.generateReqId()

        FridaRpcManager.pendingResponses[reqId] = null

        scriptPtr.sendRpcRequest(
            reqId = reqId,
            method = methodName,
            *args.toTypedArray()
        )

        val context = g_main_context_default()
        val start = platform.posix.time(null)
        while (FridaRpcManager.pendingResponses[reqId] == null) {
            if (FridaRpcManager.pendingErrors.containsKey(reqId)) {
                throw RuntimeException(FridaRpcManager.pendingErrors.remove(reqId))
            }
            g_main_context_iteration(context, 0)
            if (platform.posix.time(null) - start > 15) throw RuntimeException("RPC Timeout: $methodName")
            platform.posix.usleep(1000u)
        }

        return FridaRpcManager.pendingResponses.remove(reqId)!!
    }

    override fun close() {
        fridaCoroutineScope.cancel()
        script?.let { g_object_unref(it) }
        session?.let { g_object_unref(it) }
        device?.let { g_object_unref(it) }
        manager?.let { g_object_unref(it) }
    }


}
