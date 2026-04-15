import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
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
            return "python3 ./bridge/bridge.py$serialArg"
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
        
        state.gadgetInstallStatus = GadgetInstallStatus.WAITING_BRIDGE_SETUP
        state.gadgetErrorMessage = null
        state.gadgetSpinnerFrame = 0
        state.gadgetInjectionSteps = emptyList()

        scope.launch {
            state.sharedGadgetResult.value = Pair(GadgetInstallStatus.WAITING_BRIDGE_SETUP, null)

            // Smart check: if bridge is already responding, don't restart it
            if (!RpcClient.ping()) {
                restartBridge(state, scope)
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
}
