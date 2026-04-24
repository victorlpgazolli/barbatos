import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.posix.F_OK
import platform.posix.access
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fprintf
import platform.posix.getenv
import platform.posix.localtime
import platform.posix.pclose
import platform.posix.popen
import platform.posix.remove
import platform.posix.system
import platform.posix.time
import platform.posix.time_tVar

object CommandExecutor {
    private val charPool = ('a'..'z') + ('0'..'9')

    fun execute(command: String, state: AppState, scope: CoroutineScope) {
        val parts = command.split(" ")
        val baseCommand = parts[0]

        when (baseCommand) {
            "debug" -> handleDebug(state, scope)
            "exit" -> state.running = false
            "clear" -> {
                state.commandHistory.clear()
                HistoryStore.clear()
            }
            else -> {}
        }
    }

    fun initDebugClassFilter(state: AppState, scope: CoroutineScope) {
        state.pushMode(AppMode.DEBUG_CLASS_FILTER)
        state.isFetchingClasses = true
        state.gadgetInstallStatus = GadgetInstallStatus.WAITING_BRIDGE_SETUP
        scope.launch {
            val ok = RpcClient.ping()
            if (!ok) {
                state.sharedRpcError.value = "Frida bridge is not running on 127.0.0.1:8080. Start bridge.py"
                state.isFetchingClasses = false
                state.gadgetInstallStatus = GadgetInstallStatus.ERROR
            } else {
                val (pkgResult, _) = RpcClient.getPackageName()
                if (pkgResult != null) {
                    state.sharedAppPackageName.value = pkgResult
                }
                state.lastSearchedParam = state.inputBuffer
                val (result, error) = RpcClient.listClasses(state.inputBuffer, state.appPackageName, 0, 200)
                state.sharedFetchedClasses.value = result ?: emptyList()
                state.sharedRpcError.value = error
                state.isFetchingClasses = false
                
                if (error == null && result != null) {
                    state.gadgetInstallStatus = GadgetInstallStatus.SUCCESS
                } else {
                    state.gadgetInstallStatus = GadgetInstallStatus.ERROR
                }
            }
        }
    }

    fun handleDebugEntrypoint(state: AppState, scope: CoroutineScope) {
        when (state.debugEntrypointIndex) {
            0 -> initDebugClassFilter(state, scope)
            1 -> {
                state.activeHooks.clear()
                state.activeHooks.addAll(HookStore.load(state.appPackageName))
                state.pushMode(AppMode.DEBUG_HOOK_WATCH)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun getBridgeCommand(serialArg: String): String {
        // 1. Check if we're in development mode (running from source)
        if (access("./bridge/bridge.py", F_OK) == 0) {
            return "python3 -u ./bridge/bridge.py$serialArg"
        }

        // 2. Check environment variable
        val envPath = getenv("BARBATOS_BRIDGE_PATH")?.toKString()
        if (!envPath.isNullOrEmpty()) {
            return "$envPath$serialArg"
        }

        // 3. Check $PATH via `which`
        val whichResult = buildString {
            val pipe = popen("which barbatos-bridge 2>/dev/null", "r") ?: return@buildString
            val buf = ByteArray(256)
            while (fgets(buf.refTo(0), buf.size, pipe) != null) {
                append(buf.toKString())
            }
            pclose(pipe)
        }.trim()

        if (whichResult.isNotEmpty()) {
            return "$whichResult$serialArg"
        }

        // 4. Check current working directory for binary
        if (access("./barbatos-bridge", F_OK) == 0) {
            return "./barbatos-bridge$serialArg"
        }

        throw RuntimeException("barbatos-bridge not found. Ensure you are in the project root, have Barbatos_BRIDGE_PATH set, or barbatos-bridge is in your PATH.")
    }

    fun restartBridge(state: AppState, scope: CoroutineScope) {
        scope.launch {
            if (RpcClient.ping()) return@launch
            
            state.bridgeLogs = emptyList()
            val logFile = "${CacheManager.cacheDir()}/bridge.log"
            val pidFile = "${CacheManager.cacheDir()}/bridge.pid"
            val serialArg = if (state.adbSerial != null) " --serial ${state.adbSerial}" else ""
            val bridgeCmd = getBridgeCommand(serialArg)
            system("$bridgeCmd > \"$logFile\" 2>&1 & echo \$! > \"$pidFile\"")
        }
    }

    fun sortClasses(classes: List<String>, appPackage: String, searchQuery: String = "", showSynthetic: Boolean = false): List<String> {
        val filteredClasses = if (!showSynthetic && !searchQuery.contains('$')) {
            classes.filter { !it.contains('$') }
        } else {
            classes
        }

        val segments = appPackage.split('.')
        val firstTwo = if (segments.size >= 2) segments.take(2).joinToString(".") else ""

        return filteredClasses.sortedWith(compareByDescending<String> { className ->
            var priority = 0

            if (appPackage.isNotEmpty()) {
                if (className.startsWith("$appPackage.") || className == appPackage) {
                    priority = 3
                } else if (firstTwo.isNotEmpty() && (className.startsWith("$firstTwo.") || className == firstTwo)) {
                    priority = 2
                } else {
                    priority = 1
                }
            } else {
                priority = 1
            }

            if (className.contains("[")) {
                priority = -1
            }

            if (searchQuery.isNotEmpty() && priority >= 0) {
                val simpleName = className.substringAfterLast('.')
                if (simpleName.startsWith(searchQuery, ignoreCase = true) || className.startsWith(searchQuery, ignoreCase = true)) {
                    priority += 10
                }
            }

            priority
        }.thenBy { it })
    }

    private fun generateSessionId(): String {
        return (1..5).map { charPool.random() }.joinToString("")
    }

    private fun formatTimestamp(): String {
        memScoped {
            val t = alloc<time_tVar>()
            time(t.ptr)
            val tm = localtime(t.ptr)?.pointed ?: return "00/00 00:00"

            val day = tm.tm_mday
            val month = tm.tm_mon + 1
            val hour = tm.tm_hour
            val minute = tm.tm_min

            val dayStr = if (day < 10) "0$day" else "$day"
            val monthStr = if (month < 10) "0$month" else "$month"
            val hourStr = if (hour < 10) "0$hour" else "$hour"
            val minuteStr = if (minute < 10) "0$minute" else "$minute"

            return "$dayStr/$monthStr $hourStr:$minuteStr"
        }
    }

    fun proceedWithTmux(state: AppState) {
        val sessionId = generateSessionId()

        if (!TmuxManager.createSession(sessionId)) {
            return
        }

        val timestamp = formatTimestamp()
        SessionStore.addSession(sessionId, timestamp)

        TmuxManager.attachSession(sessionId)

        if (!TmuxManager.sessionExists(sessionId)) {
            SessionStore.removeSession(sessionId)
        }
    }

    private fun handleDebug(state: AppState, scope: CoroutineScope) {
        if (state.gadgetInstallStatus == GadgetInstallStatus.WAITING_BRIDGE_SETUP) {
            return
        }

        // Enumerate devices
        state.isFetchingDevices = true
        state.rpcError = null

        scope.launch {
            val (androidDevices, error) = AdbDeviceManager.getConnectedDevices()
            val iosDevices = IosDeviceManager.getConnectedDevices()
            
            val allDevices = androidDevices + iosDevices

            when {
                error != null && iosDevices.isEmpty() -> {
                    state.sharedRpcError.value = error
                    state.isFetchingDevices = false
                }
                allDevices.isEmpty() -> {
                    state.sharedRpcError.value = "No online devices found. Check USB connection and developer mode"
                    state.isFetchingDevices = false
                }
                allDevices.size == 1 -> {
                    // Auto-select single device
                    val device = allDevices[0]
                    state.adbSerial = device.serial
                    state.selectedPlatform = device.status
                    state.isFetchingDevices = false

                    state.gadgetInstallStatus = GadgetInstallStatus.WAITING_BRIDGE_SETUP
                    state.gadgetErrorMessage = null
                    state.gadgetSpinnerFrame = 0
                    state.gadgetInjectionSteps = emptyList()
                    state.sharedGadgetResult.value = Pair(GadgetInstallStatus.WAITING_BRIDGE_SETUP, null)

                    if (device.status == "iOS") {
                        initIosAppSelection(state, scope)
                    } else {
                        proceedWithDebugSetup(state, scope)
                    }
                }
                else -> {
                    // Show device selection UI - use shared observable pattern
                    state.pushMode(AppMode.DEBUG_DEVICE_SELECTION)
                    state.deviceInfoList = allDevices
                    state.allFetchedClasses = emptyList()
                    state.selectedDeviceIndex = 0
                    state.isFetchingDevices = false
                    state.sharedDeviceSelectionReady.value = true
                }
            }
        }
    }

    fun initIosAppSelection(state: AppState, scope: CoroutineScope) {
        state.isFetchingDevices = true
        scope.launch {
            try {
                RpcClient.prepareEnvironment()
                val healthCheck = RpcClient.healthCheck()
                val isFridaConnected = healthCheck?.checks?.get("frida_connection")?.status == "ok"
                if (!isFridaConnected) {
                    discoverIosApps(state)
                    if (state.iosAppPaths.isEmpty()) {
                        state.iosRepackageError =
                            "No Xcode-built .app found. Ensure you've built the app in Xcode within the last 48 hours."
                    }
                } else {
                    state.isFetchingDevices = false
                    state.pushMode(AppMode.DEBUG_ENTRYPOINT)
                    return@launch
                }
            } catch (e: Exception) {
                state.iosRepackageError = "Error discovering apps: ${e.message}"
            }
            state.isFetchingDevices = false
            state.pushMode(AppMode.IOS_APP_SELECTION)
            state.sharedIosAppSelectionReady.value = true
        }
    }

    private fun discoverIosApps(state: AppState) {
        val home = Shell.execute("echo \$HOME").trim()
        if (home.isBlank()) {
            throw Exception("Cannot determine home directory")
        }
        val derivedDataDir = "$home/Library/Developer/Xcode/DerivedData"
        // Find .app directories modified in the last 48 hours (-mtime -2)
        val output = Shell.execute("find $derivedDataDir -maxdepth 5 -type d -name '*.app' -mtime -2 2>/dev/null")
        state.iosAppPaths = output.trim().lines().filter { it.isNotBlank() && it.endsWith(".app") }
        state.selectedIosAppIndex = 0
    }

    fun proceedWithDebugSetup(state: AppState, scope: CoroutineScope) {
        if (state.selectedPlatform == "iOS") {
            initIosAppSelection(state, scope)
            return
        }
        state.gadgetInstallStatus = GadgetInstallStatus.WAITING_BRIDGE_SETUP
        state.gadgetErrorMessage = null
        state.gadgetSpinnerFrame = 0
        state.gadgetInjectionSteps = emptyList()

        scope.launch {
            state.sharedGadgetResult.value = Pair(GadgetInstallStatus.WAITING_BRIDGE_SETUP, null)

            // Smart check: if bridge is already responding, don't restart it
            if (!RpcClient.ping()) {
                // Start bridge in background
                val logFile = "${CacheManager.cacheDir()}/bridge.log"
                val pidFile = "${CacheManager.cacheDir()}/bridge.pid"
                val serialArg = if (state.adbSerial != null) " --serial ${state.adbSerial}" else ""
                val bridgeCmd = getBridgeCommand(serialArg)
                system("PYTHONUNBUFFERED=1 $bridgeCmd > \"$logFile\" 2>&1 & echo \$! > \"$pidFile\"")
                delay(1000) // Give bridge time to start before first ping
            }

            var bridgeReady = false
            for (i in 0..50) {
                if (RpcClient.ping()) {
                    bridgeReady = true
                    break
                }
                delay(500)
            }

            if (!bridgeReady) {
                state.sharedGadgetResult.value = Pair(GadgetInstallStatus.ERROR, "Bridge not ready. Is it running?")
                return@launch
            }

            // Ensure bridge is in a clean state before starting injection
            RpcClient.resetInjection()

            // Start polling for progress and logs
            var isFinished = false
            while (!isFinished) {
                val (progress, error) = RpcClient.injectGadgetFromScratch()

                if (error != null) {
                    state.sharedGadgetResult.value = Pair(GadgetInstallStatus.ERROR, error)
                    isFinished = true
                } else if (progress != null) {
                    state.sharedGadgetSteps.value = progress.steps
                    state.sharedBridgeLogs.value = progress.logs

                    when (progress.status) {
                        "completed" -> {
                            state.sharedGadgetResult.value = Pair(GadgetInstallStatus.SUCCESS, null)
                            isFinished = true
                        }
                        "error" -> {
                            state.sharedGadgetResult.value = Pair(GadgetInstallStatus.ERROR, progress.error_message ?: "Unknown error during injection")
                            isFinished = true
                        }
                        "running" -> {
                            delay(500)
                        }
                    }
                } else {
                    state.sharedGadgetResult.value = Pair(GadgetInstallStatus.ERROR, "Empty response from bridge")
                    isFinished = true
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun executeWatchedHook(state: AppState, className: String, methodSig: String, scope: CoroutineScope) {
        val tempDir = "/tmp/barbatos"
        system("mkdir -p $tempDir")

        val tsPath = "$tempDir/exec.ts"
        val dtsPath = "$tempDir/barbatos.d.ts"

        // Fetch live instances before opening editor (best-effort, empty on failure)
        var instanceAddresses: List<String> = emptyList()
        try {
            instanceAddresses = kotlinx.coroutines.runBlocking {
                RpcClient.getInstanceAddresses(className)
            }
        } catch (_: Exception) {}

        val dtsContent = """
            declare interface BarbatosInstance {
                original: () => any;
            }
            declare interface BarbatosContext {
                Java: any;
                args: any[];
                instances: BarbatosInstance[];
                original: () => any;
                log: (msg: any) => void;
            }
        """.trimIndent()

        val dtsFile = fopen(dtsPath, "w")
        if (dtsFile != null) {
            fprintf(dtsFile, "%s", dtsContent)
            fclose(dtsFile)
        }

        val instanceComments = buildString {
            if (instanceAddresses.isNotEmpty()) {
                append("\n/* Available instances (newest → oldest):")
                instanceAddresses.forEachIndexed { i, addr ->
                    val marker = if (i == 0) "  ← most recent" else ""
                    append("\n   context.instances[$i]  $addr$marker")
                }
                append("\n*/")
            } else {
                append("\n/* (no live instances found at time of opening) */")
            }
        }

        val tsContent = """
            /// <reference path="./barbatos.d.ts" />

            /**
             * One-shot execution for $methodSig
             * This code runs ONCE and is NOT saved to the hook's implementation.
             *
             * context.instances[0]  → most recent live instance
             * context.original()    → shortcut for context.instances[0].original()
             * context.log(msg)      → log to Hook Watch view
             * context.Java          → Frida Java object
             */
            export default (context: BarbatosContext): any => {
                return context.instances[0].original();
            };
            $instanceComments
        """.trimIndent()

        val tsFile = fopen(tsPath, "w")
        if (tsFile != null) {
            fprintf(tsFile, "%s", tsContent)
            fclose(tsFile)
        }

        Terminal.disableRawMode()
        print(Ansi.DISABLE_MOUSE)
        print(Ansi.SHOW_CURSOR)
        Terminal.flush()

        val editor = getenv("EDITOR")?.toKString() ?: "vi"
        system("$editor $tsPath")

        Terminal.enableRawMode()
        print(Ansi.ENABLE_MOUSE)
        print(Ansi.HIDE_CURSOR)
        Terminal.flush()

        val newContent = buildString {
            val file = fopen(tsPath, "r") ?: return@buildString
            val buf = ByteArray(1024)
            while (fgets(buf.refTo(0), buf.size, file) != null) {
                append(buf.toKString())
            }
            fclose(file)
        }
        // Only proceed if content actually changed (ignoring whitespace)
        // if (newContent.trim() == tsContent.trim()) {
        //     remove(tsPath)
        //     remove(dtsPath)
        //     return
        // }

        val firstBrace = newContent.indexOf('{')
        val lastBrace = newContent.lastIndexOf('}')

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            val body = newContent.substring(firstBrace + 1, lastBrace).trim()
            if (body.isNotEmpty()) {
                scope.launch {
                    RpcClient.runOnce(className, methodSig, body)
                }
            }
        }

        remove(tsPath)
        remove(dtsPath)
    }

    @OptIn(ExperimentalForeignApi::class)
    fun executeMethodOverride(state: AppState, className: String, methodSig: String, scope: CoroutineScope) {
        val tempDir = "/tmp/barbatos"
        system("mkdir -p $tempDir")
        
        val tsPath = "$tempDir/override.ts"
        val dtsPath = "$tempDir/barbatos.d.ts"
        
        // Write d.ts
        val dtsContent = """
            declare interface BarbatosContext {
                Java: any;
                args: any[];
                original: () => any;
                log: (msg: any) => void;
            }
        """.trimIndent()
        
        val dtsFile = fopen(dtsPath, "w")
        if (dtsFile != null) {
            fprintf(dtsFile, "%s", dtsContent)
            fclose(dtsFile)
        }
        
        // Prepare initial TS content
        val existingHook = state.activeHooks.find { it.className == className && it.memberSignature == methodSig }
        val initialBody = existingHook?.implementation ?: "return context.original();"
        
        val tsContent = """
            /// <reference path="./barbatos.d.ts" />
            
            /**
             * Custom implementation for $methodSig
             * 
             * Available in 'context':
             * - context.Java: Frida Java object
             * - context.args: Array of arguments passed to the method
             * - context.original(): Call the original implementation
             * - context.log(msg): Log a message to the Hook Watch view
             */
            export default (context: BarbatosContext): any => {
                $initialBody
            };
        """.trimIndent()

        val tsFile = fopen(tsPath, "w")
        if (tsFile != null) {
            fprintf(tsFile, "%s", tsContent)
            fclose(tsFile)
        }
        
        // Disable TUI raw mode before launching editor
        Terminal.disableRawMode()
        print(Ansi.DISABLE_MOUSE)
        print(Ansi.SHOW_CURSOR)
        Terminal.flush()
        
        val editor = getenv("EDITOR")?.toKString() ?: "vi"
        system("$editor $tsPath")
        
        // Re-enable TUI raw mode
        Terminal.enableRawMode()
        print(Ansi.ENABLE_MOUSE)
        print(Ansi.HIDE_CURSOR)
        Terminal.flush()
        
        // Read back using fopen
        val newContent = buildString {
            val file = fopen(tsPath, "r") ?: return@buildString
            val buf = ByteArray(1024)
            while (fgets(buf.refTo(0), buf.size, file) != null) {
                append(buf.toKString())
            }
            fclose(file)
        }

        // Only proceed if content actually changed
        if (newContent.trim() == tsContent.trim()) {
            remove(tsPath)
            remove(dtsPath)
            return
        }
        
        // Extract body between first { and last }
        val firstBrace = newContent.indexOf('{')
        val lastBrace = newContent.lastIndexOf('}')
        
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            val body = newContent.substring(firstBrace + 1, lastBrace).trim()
            
            if (body.isNotEmpty()) {
                scope.launch {
                    val success = RpcClient.setMethodImplementation(className, methodSig, body)
                    if (success) {
                        val hook = state.activeHooks.find { it.className == className && it.memberSignature == methodSig }
                        if (hook != null) {
                            state.activeHooks.remove(hook)
                        }
                        // Adding with enabled=true ensures it behaves as a hook too [H]
                        state.activeHooks.add(HookTarget(className, methodSig, HookType.METHOD, true, body))
                        HookStore.save(state.appPackageName, state.activeHooks.toSet())
                    }
                }
            }
        }
        
        // Cleanup
        remove(tsPath)
        remove(dtsPath)
    }

    fun startIosInjection(appPath: String, state: AppState, scope: CoroutineScope) {
        state.gadgetInstallStatus = GadgetInstallStatus.WAITING_BRIDGE_SETUP
        state.gadgetErrorMessage = null
        state.gadgetSpinnerFrame = 0
        state.gadgetInjectionSteps = emptyList()
        state.pushMode(AppMode.IOS_REPACKAGE_SETUP)

        scope.launch {
            state.sharedGadgetResult.value = Pair(GadgetInstallStatus.WAITING_BRIDGE_SETUP, null)

            // Start bridge if not already running (iOS doesn't use --serial for adb)
            if (!RpcClient.ping()) {
                val logFile = "${CacheManager.cacheDir()}/bridge.log"
                val pidFile = "${CacheManager.cacheDir()}/bridge.pid"
                val bridgeCmd = getBridgeCommand("")
                system("PYTHONUNBUFFERED=1 $bridgeCmd > \"$logFile\" 2>&1 & echo \$! > \"$pidFile\"")
                delay(1000)
            }

            var bridgeReady = false
            for (i in 0..30) {
                if (RpcClient.ping()) {
                    bridgeReady = true
                    break
                }
                delay(500)
            }

            if (!bridgeReady) {
                state.sharedGadgetResult.value = Pair(GadgetInstallStatus.ERROR, "Bridge not ready. Is it running?")
                return@launch
            }

            // 1. Initial RPC to inject gadget and start monitor thread in bridge
            val (success, error) = RpcClient.patchAndInstallIosApp(appPath)
            if (!success) {
                state.sharedGadgetResult.value = Pair(GadgetInstallStatus.ERROR, error ?: "Failed to start iOS injection")
                return@launch
            }

            // 2. Poll for status until Success or Error (with timeout: 5 minutes)
            val startTime = currentTimeMillis()
            val timeoutMs = 5 * 60 * 1000L
            var consecutiveErrors = 0

            while (state.running && state.gadgetInstallStatus == GadgetInstallStatus.WAITING_BRIDGE_SETUP) {
                if (currentTimeMillis() - startTime > timeoutMs) {
                    state.sharedGadgetResult.value = Pair(GadgetInstallStatus.ERROR, "iOS injection timeout (5 min). Check if Xcode deploy is still running.")
                    break
                }

                val (status, result) = RpcClient.checkIosDeployStatus()

                if (status == GadgetInstallStatus.ERROR) {
                    state.sharedGadgetResult.value = Pair(status, null)
                    break
                }

                if (result != null) {
                    state.sharedGadgetSteps.value = result.steps
                    consecutiveErrors = 0
                } else {
                    consecutiveErrors++
                    if (consecutiveErrors > 20) {
                        state.sharedGadgetResult.value = Pair(GadgetInstallStatus.ERROR, "Lost connection to bridge. Check if bridge.py is still running.")
                        break
                    }
                }

                if (status == GadgetInstallStatus.SUCCESS) {
                    state.sharedGadgetResult.value = Pair(status, null)
                    break
                }

                delay(1000)
            }
        }
    }

    fun handleIosRepackage(state: AppState, scope: CoroutineScope) {
        if (state.iosIpaPath.isEmpty()) {
            state.iosRepackageError = "Please enter the path to the .app folder"
            return
        }
        state.iosRepackageError = null
        startIosInjection(state.iosIpaPath, state, scope)
    }
}
