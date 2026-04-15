
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

fun onInputChanged(state: AppState, resetCtrlC: Boolean = true) {
    if (resetCtrlC) state.ctrlCPressed = false
    state.historyNavigationIndex = -1
    
    if (state.mode == AppMode.DEFAULT) {
        state.suggestions = CommandRegistry.search(state.inputBuffer)
        state.selectedSuggestionIndex = if (state.suggestions.isNotEmpty()) 0 else -1
    } else {
        state.lastInputTimestamp = currentTimeMillis()
    }
}

fun loadAndSyncHooks(state: AppState, scope: CoroutineScope, packageName: String) {
    if (state.hooksLoaded) return
    state.hooksLoaded = true
    state.appPackageName = packageName
    val loaded = HookStore.load(packageName)
    state.activeHooks.clear()
    state.activeHooks.addAll(loaded)
    scope.launch {
        RpcClient.syncAllHooks(state.activeHooks)
    }
}

fun main(args: Array<String>) {
    CacheManager.ensureCacheDir()

    var initialMode = AppMode.DEFAULT
    var inspectTarget = ""
    var adbSerial: String? = null

    if (args.contains("--serial") && args.indexOf("--serial") + 1 < args.size) {
        adbSerial = args[args.indexOf("--serial") + 1]
    }

    if (args.contains("--mode") && args.indexOf("--mode") + 1 < args.size) {
        val modeIdx = args.indexOf("--mode")
        val modeStr = args[modeIdx + 1]
        if (modeStr == "debug_entrypoint") {
            initialMode = AppMode.DEBUG_ENTRYPOINT
        } else if (modeStr == "debug_class_filter") {
            initialMode = AppMode.DEBUG_CLASS_FILTER
        } else if (modeStr == "debug_inspect_class") {
            initialMode = AppMode.DEBUG_INSPECT_CLASS
            if (modeIdx + 2 < args.size) {
                inspectTarget = args[modeIdx + 2]
            }
        }
    }

    if (initialMode == AppMode.DEFAULT && !TmuxManager.checkTmux()) {
        println("${Ansi.RED}tmux is unaccessible by barbatos, verify exec permissions and if tmux is installed${Ansi.RESET}")
        return
    }

    val state = AppState(adbSerial = adbSerial, mode = initialMode)
    if (args.contains("--mode")) {
        state.isSubPane = true
    }
    if (initialMode == AppMode.DEBUG_INSPECT_CLASS) {
        state.startedAsInspectPane = true
    }
    state.commandHistory = HistoryStore.load()
    val scope = CoroutineScope(Dispatchers.Default)

    // Try to fetch package name at startup to load hooks early
    scope.launch {
        val ok = RpcClient.ping()
        if (ok) {
            val (pkg, _) = RpcClient.getPackageName()
            if (pkg != null) {
                loadAndSyncHooks(state, scope, pkg)
            }
        }
    }

    // Bridge logs background reading
    scope.launch {
        val logFile = "${CacheManager.cacheDir()}/bridge.log"
        while (state.running) {
            val canShowBridgeLogs = when (state.mode) {
                AppMode.DEBUG_ENTRYPOINT,
                AppMode.DEFAULT -> {
                    // Only use file reading if NOT actively polling via RPC in CommandExecutor
                    state.gadgetInstallStatus == GadgetInstallStatus.IDLE || 
                    state.gadgetInstallStatus == GadgetInstallStatus.SUCCESS || 
                    state.gadgetInstallStatus == GadgetInstallStatus.ERROR
                }
                else -> false
            }
            if (canShowBridgeLogs) {
                val file = platform.posix.fopen(logFile, "r")
                if (file != null) {
                    val lines = mutableListOf<String>()
                    val buffer = ByteArray(1024)
                    while (platform.posix.fgets(buffer.refTo(0), buffer.size, file) != null) {
                        lines.add(buffer.toKString().trimEnd('\n', '\r'))
                    }
                    platform.posix.fclose(file)
                    val newLogs = lines.takeLast(20)
                    if (newLogs != state.bridgeLogs && newLogs != state.sharedBridgeLogs.value) {
                        state.sharedBridgeLogs.value = newLogs
                    }
                }
            }
            delay(500)
        }
    }

    // Task 7: Start background polling for hook events every 250ms
    scope.launch {
        while (state.running) {
            if (state.mode == AppMode.DEBUG_HOOK_WATCH) {
                val events = RpcClient.getHookEvents()
                if (events.isNotEmpty()) {
                    state.sharedHookEvents.value = events
                }
            }
            delay(250)
        }
    }

    if (initialMode == AppMode.DEBUG_CLASS_FILTER) {
        CommandExecutor.initDebugClassFilter(state, scope)
    } else if (initialMode == AppMode.DEBUG_INSPECT_CLASS) {
        state.inspectTargetClassName = inspectTarget
        state.isFetchingInspection = true
        scope.launch {
            val ok = RpcClient.ping()
            if (!ok) {
                state.sharedRpcError.value = "Frida bridge is not running on 127.0.0.1:8080. Start bridge.py"
                state.isFetchingInspection = false
            } else {
                val (pkg, _) = RpcClient.getPackageName()
                if (pkg != null) {
                    loadAndSyncHooks(state, scope, pkg)
                }
                val (result, error) = RpcClient.inspectClass(inspectTarget)
                state.sharedInspectResult.value = result
                state.sharedRpcError.value = error
                state.isFetchingInspection = false
            }
        }
    }

    Terminal.enableRawMode()
    print(Ansi.ENABLE_MOUSE)
    Terminal.flush()
    Renderer.render(state)

    while (state.running) {
        if (state.ctrlCPressed) {
            val elapsed = currentTimeMillis() - state.ctrlCTimestamp
            if (elapsed > 1500) {
                state.ctrlCPressed = false
                Renderer.render(state)
            }
        }

        val key = InputHandler.readKey()

        when (key) {
            is KeyEvent.CtrlC -> {
                if (state.ctrlCPressed) {
                    state.running = false
                } else {
                    state.ctrlCPressed = true
                    state.ctrlCTimestamp = currentTimeMillis()
                    Renderer.render(state)
                }
            }

            is KeyEvent.Char -> {
                if (state.mode == AppMode.DEBUG_HOOK_WATCH) {
                    when (key.c) {
                        ' ', 's', 'S' -> {
                            val hooks = state.activeHooks
                            if (state.selectedHookIndex in hooks.indices) {
                                val oldHook = hooks[state.selectedHookIndex]
                                val newHook = oldHook.copy(enabled = !oldHook.enabled)
                                hooks[state.selectedHookIndex] = newHook
                                scope.launch {
                                    RpcClient.toggleHook(newHook.className, newHook.memberSignature, newHook.enabled, newHook.implementation)
                                }

                                HookStore.save(state.appPackageName, state.activeHooks.toSet())
                                Renderer.render(state)
                            }
                        }
                        'i', 'I' -> {
                            val hooks = state.activeHooks
                            if (state.selectedHookIndex in hooks.indices) {
                                val target = hooks[state.selectedHookIndex]
                                state.inspectTargetClassName = target.className
                                state.isFetchingInspection = true
                                state.inspectStaticAttributes = emptyList()
                                state.inspectInstanceAttributes = emptyList()
                                state.inspectMethods = emptyList()
                                state.inspectExpandedInstances.clear()
                                state.inspectInstancesList = null
                                state.inspectInstancesTotalCount = 0
                                state.inspectInstancesExpanded = false
                                state.selectedClassIndex = 0
                                state.pushMode(AppMode.DEBUG_INSPECT_CLASS)
                                Renderer.render(state)
                                scope.launch {
                                    val (result, error) = RpcClient.inspectClass(target.className)
                                    state.sharedInspectResult.value = result
                                    state.sharedRpcError.value = error
                                    state.isFetchingInspection = false
                                }
                            }
                        }
                        'c', 'C' -> {
                            state.hookEvents.clear()
                            Renderer.render(state)
                        }
                        'e', 'E' -> {
                            val hooks = state.activeHooks
                            if (state.selectedHookIndex in hooks.indices) {
                                val target = hooks[state.selectedHookIndex]
                                if (target.type == HookType.METHOD) {
                                    CommandExecutor.executeMethodOverride(state, target.className, target.memberSignature, scope)
                                    Renderer.render(state)
                                }
                            }
                        }
                    }

                } else if (state.mode == AppMode.DEBUG_CLASS_FILTER && (key.c == ']')) {
                    state.showSyntheticClasses = !state.showSyntheticClasses
                    state.displayedClasses = CommandExecutor.sortClasses(state.allFetchedClasses, state.appPackageName, state.lastSearchedParam, state.showSyntheticClasses)
                    state.selectedClassIndex = if (state.displayedClasses.isNotEmpty()) 0 else -1
                    Renderer.render(state)
                } else if (state.mode == AppMode.DEBUG_CLASS_FILTER && key.c == '\\') {
                    if (state.displayedClasses.isNotEmpty() && state.selectedClassIndex >= 0 && !state.isFetchingInstances) {
                        val selectedClass = state.displayedClasses[state.selectedClassIndex]
                        state.isFetchingInstances = true
                        Renderer.render(state)
                        scope.launch {
                            val (res, err) = RpcClient.countInstances(selectedClass)
                            state.sharedInstanceCountResult.value = if (res != null) Pair(selectedClass, res) else null
                            state.sharedInstanceCountError.value = err
                        }
                    }
                } else if (state.mode == AppMode.DEBUG_INSPECT_CLASS && (key.c == 'w' || key.c == 'W')) {
                    state.pushMode(AppMode.DEBUG_HOOK_WATCH)
                    state.activeHooks.clear()
                    state.activeHooks.addAll(HookStore.load(state.appPackageName))
                    Renderer.render(state)
                } else if (state.mode == AppMode.DEBUG_INSPECT_CLASS && (key.c == 'h' || key.c == 'H')) {
                    val rows = state.buildInspectRows()
                    if (rows.isNotEmpty() && state.selectedClassIndex in rows.indices) {
                        val row = rows[state.selectedClassIndex]
                        if (row is InspectRow.StaticAttributeRow || row is InspectRow.StaticMethodRow) {
                            val signature = if (row is InspectRow.StaticAttributeRow) row.attribute else (row as InspectRow.StaticMethodRow).method
                            val type = if (row is InspectRow.StaticAttributeRow) HookType.FIELD else HookType.METHOD
                            
                            val existing = state.activeHooks.find { it.className == state.inspectTargetClassName && it.memberSignature == signature }
                            if (existing != null) {
                                state.activeHooks.remove(existing)
                                scope.launch {
                                    RpcClient.toggleHook(state.inspectTargetClassName, signature, false)
                                }
                            } else {
                                // Add a new standard hook
                                val target = HookTarget(state.inspectTargetClassName, signature, type)
                                state.activeHooks.add(target)
                                scope.launch {
                                    RpcClient.toggleHook(state.inspectTargetClassName, signature, true)
                                }
                            }
                            HookStore.save(state.appPackageName, state.activeHooks.toSet())
                            Renderer.render(state)
                        }
                    }
                } else if (state.mode == AppMode.DEBUG_INSPECT_CLASS && (key.c == 'i' || key.c == 'I')) {
                    val rows = state.buildInspectRows()
                    if (rows.isNotEmpty() && state.selectedClassIndex in rows.indices) {
                        val row = rows[state.selectedClassIndex]
                        var targetClassName: String? = null
                        
                        if (row is InspectRow.InstanceRow) {
                            targetClassName = state.inspectTargetClassName
                        } else if (row is InspectRow.InstanceAttributeRow) {
                            targetClassName = row.attribute.childClassName
                        }
                        
                        if (targetClassName != null) {
                            state.inspectBackStack.add(state.inspectTargetClassName)
                            state.pushMode(AppMode.DEBUG_INSPECT_CLASS, force = true)
                            state.inspectTargetClassName = targetClassName
                            state.isFetchingInspection = true
                            state.inspectStaticAttributes = emptyList()
                            state.inspectInstanceAttributes = emptyList()
                            state.inspectMethods = emptyList()
                            state.inspectExpandedInstances.clear()
                            state.inspectExpandedInstancesError.clear()
                            state.inspectInstancesList = null
                            state.inspectInstancesTotalCount = 0
                            state.inspectInstancesExpanded = false
                            state.selectedClassIndex = 0
                            state.rpcError = null
                            Renderer.render(state)
                            scope.launch {
                                val (result, error) = RpcClient.inspectClass(targetClassName)
                                state.sharedInspectResult.value = result
                                state.sharedRpcError.value = error
                                state.isFetchingInspection = false
                            }
                        }
                    }
                } else if (state.mode == AppMode.DEBUG_INSPECT_CLASS && (key.c == 'e' || key.c == 'E')) {
                    val rows = state.buildInspectRows()
                    if (rows.isNotEmpty() && state.selectedClassIndex in rows.indices) {
                        val row = rows[state.selectedClassIndex]
                        if (row is InspectRow.InstanceAttributeRow) {
                            val isBasicType = setOf("int", "long", "boolean", "byte", "short", "float", "double", "string", "charsequence", "char").contains(row.attribute.type.lowercase())
                            if (isBasicType) {
                                state.pushMode(AppMode.DEBUG_EDIT_ATTRIBUTE)
                                state.editingInstanceId = row.instanceId
                                state.editingAttribute = row.attribute
                                val currentValue = if (row.attribute.value == "null") "" else row.attribute.value.removeSurrounding("\"")
                                state.inputBuffer = currentValue
                                state.cursorPosition = state.inputBuffer.length
                                onInputChanged(state)
                                Renderer.render(state)
                            }
                        } else if (row is InspectRow.StaticMethodRow) {
                            CommandExecutor.executeMethodOverride(state, state.inspectTargetClassName, row.method, scope)
                            Renderer.render(state)
                        }
                    }
                } else if (state.mode == AppMode.DEBUG_INSPECT_CLASS && (key.c == 'R' || key.c == 'r')) {
                    val rows = state.buildInspectRows()
                    if (rows.isNotEmpty() && state.selectedClassIndex in rows.indices) {
                        val row = rows[state.selectedClassIndex]
                        if (row is InspectRow.InstanceRow || row is InspectRow.InstanceAttributeRow) {
                            var targetId: String
                            var targetClassName: String
                            
                            if (row is InspectRow.InstanceRow) {
                                targetId = row.instance.id
                                targetClassName = state.inspectTargetClassName
                            } else {
                                val attrRow = row as InspectRow.InstanceAttributeRow
                                if (attrRow.attribute.childId != null && attrRow.isExpanded) {
                                    targetId = attrRow.attribute.childId!!
                                    targetClassName = attrRow.attribute.childClassName ?: ""
                                } else {
                                    targetId = attrRow.instanceId
                                    targetClassName = state.inspectTargetClassName 
                                }
                            }
                            
                            state.inspectExpandedInstances[targetId] = null
                            Renderer.render(state)
                            scope.launch {
                                val (attrs, err) = RpcClient.inspectInstance(targetClassName, targetId)
                                if (err == null && attrs != null) {
                                    state.sharedInspectInstanceResult.value = Triple(targetId, attrs, false)
                                } else {
                                    state.sharedRpcError.value = err ?: "Unknown error"
                                }
                            }
                        }
                    }
                } else if (state.mode == AppMode.DEBUG_ENTRYPOINT && (key.c == 'R' || key.c == 'r')) {
                    val pidFile = "${CacheManager.cacheDir()}/bridge.pid"
                    platform.posix.system("if [ -f \"$pidFile\" ]; then kill -9 \$(cat \"$pidFile\") 2>/dev/null; rm \"$pidFile\"; fi")
                    CommandExecutor.restartBridge(state, scope)
                } else {
                    state.inputBuffer = state.inputBuffer.substring(0, state.cursorPosition) +
                            key.c +
                            state.inputBuffer.substring(state.cursorPosition)
                    state.cursorPosition++
                    onInputChanged(state)
                    Renderer.render(state)
                }
            }

            is KeyEvent.Backspace -> {
                if (state.cursorPosition > 0) {
                    state.inputBuffer = state.inputBuffer.substring(0, state.cursorPosition - 1) +
                            state.inputBuffer.substring(state.cursorPosition)
                    state.cursorPosition--
                    onInputChanged(state)
                    Renderer.render(state)
                }
            }

            is KeyEvent.OptionBackspace -> {
                if (state.cursorPosition > 0) {
                    var pos = state.cursorPosition - 1
                    while (pos >= 0 && !state.inputBuffer[pos].isLetterOrDigit()) pos--
                    while (pos >= 0 && state.inputBuffer[pos].isLetterOrDigit()) pos--
                    val newCursor = pos + 1
                    
                    state.inputBuffer = state.inputBuffer.substring(0, newCursor) + 
                                        state.inputBuffer.substring(state.cursorPosition)
                    state.cursorPosition = newCursor
                    onInputChanged(state)
                    Renderer.render(state)
                }
            }

            is KeyEvent.OptionLeft -> {
                var pos = state.cursorPosition - 1
                if (pos >= 0) {
                    while (pos >= 0 && !state.inputBuffer[pos].isLetterOrDigit()) pos--
                    while (pos >= 0 && state.inputBuffer[pos].isLetterOrDigit()) pos--
                    state.cursorPosition = pos + 1
                    onInputChanged(state)
                    Renderer.render(state)
                }
            }

            is KeyEvent.OptionRight -> {
                var pos = state.cursorPosition
                if (pos < state.inputBuffer.length) {
                    while (pos < state.inputBuffer.length && !state.inputBuffer[pos].isLetterOrDigit()) pos++
                    while (pos < state.inputBuffer.length && state.inputBuffer[pos].isLetterOrDigit()) pos++
                    state.cursorPosition = pos
                    onInputChanged(state)
                    Renderer.render(state)
                }
            }

            is KeyEvent.CmdLeft, is KeyEvent.CtrlA -> {
                state.cursorPosition = 0
                onInputChanged(state)
                Renderer.render(state)
            }

            is KeyEvent.CmdRight, is KeyEvent.CtrlE -> {
                state.cursorPosition = state.inputBuffer.length
                onInputChanged(state)
                Renderer.render(state)
            }

            is KeyEvent.CmdBackspace -> {
                if (state.cursorPosition > 0) {
                    state.inputBuffer = state.inputBuffer.substring(state.cursorPosition)
                    state.cursorPosition = 0
                    onInputChanged(state)
                    Renderer.render(state)
                }
            }

            is KeyEvent.ArrowDown, is KeyEvent.MouseScrollDown -> {
                if (state.mode == AppMode.DEBUG_HOOK_WATCH) {
                    val hooks = state.activeHooks.toList()
                    if (hooks.isNotEmpty()) {
                        state.selectedHookIndex = (state.selectedHookIndex + 1).coerceAtMost(hooks.size - 1)
                        Renderer.render(state)
                    }
                } else if (state.mode == AppMode.DEBUG_ENTRYPOINT) {
                    state.debugEntrypointIndex = (state.debugEntrypointIndex + 1).coerceAtMost(1)
                    Renderer.render(state)
                } else if (state.mode == AppMode.DEFAULT) {
                    if (state.suggestions.isNotEmpty()) {
                        state.selectedSuggestionIndex =
                            (state.selectedSuggestionIndex + 1).coerceAtMost(state.suggestions.size - 1)
                        Renderer.render(state)
                    } else if (state.historyNavigationIndex > -1) {
                        state.historyNavigationIndex--
                        if (state.historyNavigationIndex < 0) {
                            state.historyNavigationIndex = -1
                            state.inputBuffer = state.savedInputBeforeHistory
                        } else {
                            state.inputBuffer = state.commandHistory[state.commandHistory.size - 1 - state.historyNavigationIndex]
                        }
                        state.cursorPosition = state.inputBuffer.length
                        Renderer.render(state)
                    }
                } else if (state.mode == AppMode.DEBUG_CLASS_FILTER) {
                    if (state.displayedClasses.isNotEmpty()) {
                        state.selectedClassIndex =
                            (state.selectedClassIndex + 1).coerceAtMost(state.displayedClasses.size - 1)
                        Renderer.render(state)
                    }
                } else if (state.mode == AppMode.DEBUG_INSPECT_CLASS) {
                    val rows = state.buildInspectRows()
                    if (rows.isNotEmpty()) {
                        state.selectedClassIndex =
                            (state.selectedClassIndex + 1).coerceAtMost(rows.size - 1)
                        Renderer.render(state)
                    }
                }
            }

            is KeyEvent.ArrowUp, is KeyEvent.MouseScrollUp -> {
                if (state.mode == AppMode.DEBUG_HOOK_WATCH) {
                    val hooks = state.activeHooks.toList()
                    if (hooks.isNotEmpty()) {
                        state.selectedHookIndex = (state.selectedHookIndex - 1).coerceAtLeast(0)
                        Renderer.render(state)
                    }
                } else if (state.mode == AppMode.DEBUG_ENTRYPOINT) {
                    state.debugEntrypointIndex = (state.debugEntrypointIndex - 1).coerceAtLeast(0)
                    Renderer.render(state)
                } else if (state.mode == AppMode.DEFAULT) {
                    if (state.suggestions.isNotEmpty()) {
                        state.selectedSuggestionIndex =
                            (state.selectedSuggestionIndex - 1).coerceAtLeast(0)
                        Renderer.render(state)
                    } else if (state.commandHistory.isNotEmpty()) {
                        if (state.historyNavigationIndex == -1) {
                            state.savedInputBeforeHistory = state.inputBuffer
                        }
                        state.historyNavigationIndex = (state.historyNavigationIndex + 1).coerceAtMost(state.commandHistory.size - 1)
                        state.inputBuffer = state.commandHistory[state.commandHistory.size - 1 - state.historyNavigationIndex]
                        state.cursorPosition = state.inputBuffer.length
                        Renderer.render(state)
                    }
                } else if (state.mode == AppMode.DEBUG_CLASS_FILTER) {
                    if (state.displayedClasses.isNotEmpty()) {
                        state.selectedClassIndex =
                            (state.selectedClassIndex - 1).coerceAtLeast(0)
                        Renderer.render(state)
                    }
                } else if (state.mode == AppMode.DEBUG_INSPECT_CLASS) {
                    val rows = state.buildInspectRows()
                    if (rows.isNotEmpty()) {
                        state.selectedClassIndex =
                            (state.selectedClassIndex - 1).coerceAtLeast(0)
                        Renderer.render(state)
                    }
                }
            }

            is KeyEvent.Tab -> {
                if (state.mode == AppMode.DEFAULT && state.suggestions.isNotEmpty() && state.selectedSuggestionIndex >= 0) {
                    val selected = state.suggestions[state.selectedSuggestionIndex]
                    state.inputBuffer = selected.name
                    state.cursorPosition = state.inputBuffer.length
                    onInputChanged(state)
                    Renderer.render(state)
                } else if (state.mode == AppMode.DEBUG_CLASS_FILTER && state.displayedClasses.isNotEmpty() && state.selectedClassIndex >= 0) {
                    state.inputBuffer = state.displayedClasses[state.selectedClassIndex]
                    state.cursorPosition = state.inputBuffer.length
                    onInputChanged(state)
                    Renderer.render(state)
                }
            }

            is KeyEvent.Enter -> {
                if (state.mode == AppMode.DEBUG_HOOK_WATCH) {
                    val hooks = state.activeHooks
                    if (state.selectedHookIndex in hooks.indices) {
                        val oldHook = hooks[state.selectedHookIndex]
                        val newHook = oldHook.copy(enabled = !oldHook.enabled)
                        hooks[state.selectedHookIndex] = newHook
                        scope.launch {
                            RpcClient.toggleHook(newHook.className, newHook.memberSignature, newHook.enabled, newHook.implementation)
                        }

                        HookStore.save(state.appPackageName, state.activeHooks.toSet())
                        Renderer.render(state)
                    }
                } else if (state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) {
                    val newValue = state.inputBuffer
                    val attr = state.editingAttribute
                    val instId = state.editingInstanceId
                    state.inputBuffer = ""
                    state.cursorPosition = 0
                    state.popMode()
                    Renderer.render(state)
                    
                    if (attr != null && instId.isNotEmpty()) {
                        scope.launch {
                            val err = RpcClient.setFieldValue(
                                state.inspectTargetClassName,
                                instId,
                                attr.name,
                                attr.type,
                                newValue
                            )
                            if (err == null) {
                                // Refresh instance
                                val (attrs, refreshErr) = RpcClient.inspectInstance(state.inspectTargetClassName, instId)
                                if (refreshErr == null && attrs != null) {
                                    state.sharedInspectInstanceResult.value = Triple(instId, attrs, false)
                                } else {
                                    state.sharedRpcError.value = refreshErr ?: "Unknown error"
                                }
                            } else {
                                state.sharedRpcError.value = err
                            }
                        }
                    }
                } else if (state.mode == AppMode.DEBUG_ENTRYPOINT) {
                    CommandExecutor.handleDebugEntrypoint(state, scope)
                    Renderer.render(state)
                } else if (state.mode == AppMode.DEFAULT) {
                    val commandToExecute = if (state.suggestions.isNotEmpty() && state.selectedSuggestionIndex != -1) {
                        state.suggestions[state.selectedSuggestionIndex].name
                    } else {
                        state.inputBuffer
                    }

                    if (commandToExecute != state.inputBuffer) {
                        state.inputBuffer = commandToExecute
                        state.cursorPosition = state.inputBuffer.length
                        onInputChanged(state)
                        Renderer.render(state)
                    } else if (commandToExecute.isNotBlank()) {
                        HistoryStore.append(commandToExecute)
                        state.commandHistory.add(commandToExecute)
                        
                        state.inputBuffer = ""
                        state.cursorPosition = 0
                        state.suggestions = emptyList()
                        state.selectedSuggestionIndex = -1
                        state.ctrlCPressed = false
                        state.historyNavigationIndex = -1
                        
                        Renderer.render(state)
                        CommandExecutor.execute(commandToExecute, state, scope)
                        Renderer.render(state)
                    }
                } else if (state.mode == AppMode.DEBUG_CLASS_FILTER) {
                    if (state.displayedClasses.isNotEmpty() && state.selectedClassIndex >= 0) {
                        val selectedClass = state.displayedClasses[state.selectedClassIndex]
                        
                        // Set up state for inspection
                        state.inspectTargetClassName = selectedClass
                        state.isFetchingInspection = true
                        state.inspectStaticAttributes = emptyList()
                        state.inspectInstanceAttributes = emptyList()
                        state.inspectMethods = emptyList()
                        state.inspectExpandedInstances.clear()
                        state.inspectExpandedInstancesError.clear()
                        state.inspectInstancesList = null
                        state.inspectInstancesTotalCount = 0
                        state.inspectInstancesExpanded = false
                        state.selectedClassIndex = 0
                        state.rpcError = null
                        
                        state.pushMode(AppMode.DEBUG_INSPECT_CLASS)
                        Renderer.render(state)
                        
                        // Start fetching in background
                        scope.launch {
                            val (result, error) = RpcClient.inspectClass(selectedClass)
                            state.sharedInspectResult.value = result
                            state.sharedRpcError.value = error
                            state.isFetchingInspection = false
                        }
                    }
                } else if (state.mode == AppMode.DEBUG_INSPECT_CLASS) {
                    val rows = state.buildInspectRows()
                    if (rows.isNotEmpty() && state.selectedClassIndex in rows.indices) {
                        when (val row = rows[state.selectedClassIndex]) {
                            is InspectRow.SectionStaticRow -> {
                                state.inspectStaticExpanded = !state.inspectStaticExpanded
                                Renderer.render(state)
                            }
                            is InspectRow.SectionInstancesRow -> {
                                state.inspectInstancesExpanded = !state.inspectInstancesExpanded
                                if (state.inspectInstancesExpanded && state.inspectInstancesList == null) {
                                    state.isFetchingInstancesList = true
                                    Renderer.render(state)
                                    scope.launch {
                                        val (res, err) = RpcClient.listInstances(state.inspectTargetClassName)
                                        if (err == null && res != null) {
                                            state.sharedInstancesListResult.value = res
                                        } else {
                                            state.sharedRpcError.value = err ?: "Unknown error fetching instances"
                                            state.isFetchingInstancesList = false
                                        }
                                    }
                                } else {
                                    Renderer.render(state)
                                }
                            }
                            is InspectRow.InstanceRow -> {
                                val id = row.instance.id
                                if (state.inspectExpandedInstances.containsKey(id)) {
                                    state.inspectExpandedInstances.remove(id)
                                    Renderer.render(state)
                                } else {
                                    state.inspectExpandedInstances[id] = null
                                    Renderer.render(state)
                                    scope.launch {
                                        val (attrs, err) = RpcClient.inspectInstance(state.inspectTargetClassName, id)
                                        if (err == null && attrs != null) {
                                            state.sharedInspectInstanceResult.value = Triple(id, attrs, false)
                                        } else {
                                            state.sharedRpcError.value = err ?: "Unknown error"
                                        }
                                    }
                                }
                            }
                            is InspectRow.InstanceAttributeRow -> {
                                val attr = row.attribute
                                if (attr.isPagination) {
                                    val parentId = row.instanceId
                                    // Fetch more items for the SAME parent
                                    scope.launch {
                                        val (moreAttrs, err) = RpcClient.inspectInstance(state.inspectTargetClassName, parentId, offset = attr.nextOffset)
                                        if (err == null && moreAttrs != null) {
                                            state.sharedInspectInstanceResult.value = Triple(parentId, moreAttrs, true)
                                        } else {
                                            state.sharedRpcError.value = err ?: "Error fetching more items"
                                        }
                                    }
                                } else {
                                    val childId = attr.childId
                                    val childClassName = attr.childClassName
                                    if (childId != null && childClassName != null) {
                                        if (state.inspectExpandedInstances.containsKey(childId)) {
                                            state.inspectExpandedInstances.remove(childId)
                                            Renderer.render(state)
                                        } else {
                                            state.inspectExpandedInstances[childId] = null
                                            Renderer.render(state)
                                            scope.launch {
                                                val (attrs, err) = RpcClient.inspectInstance(childClassName, childId)
                                                if (err == null && attrs != null) {
                                                    state.sharedInspectInstanceResult.value = Triple(childId, attrs, false)
                                                } else {
                                                    state.sharedRpcError.value = err ?: "Unknown error"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            is KeyEvent.ArrowLeft -> {
                if (state.mode == AppMode.DEBUG_HOOK_WATCH) {
                    state.hookLogScrollOffset = maxOf(0, state.hookLogScrollOffset - 5)
                    Renderer.render(state)
                } else if (state.cursorPosition > 0) {
                    state.cursorPosition--
                    onInputChanged(state)
                    Renderer.render(state)
                }
            }

            is KeyEvent.ArrowRight -> {
                if (state.mode == AppMode.DEBUG_HOOK_WATCH) {
                    state.hookLogScrollOffset += 5
                    Renderer.render(state)
                } else if (state.cursorPosition < state.inputBuffer.length) {
                    state.cursorPosition++
                    onInputChanged(state)
                    Renderer.render(state)
                }
            }

            is KeyEvent.Esc -> {
                if (state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) {
                    state.inputBuffer = ""
                    state.cursorPosition = 0
                    state.popMode()
                    Renderer.render(state)
                } else if (state.mode == AppMode.DEBUG_INSPECT_CLASS && state.inspectBackStack.isNotEmpty()) {
                    val prevClass = state.inspectBackStack.removeAt(state.inspectBackStack.size - 1)
                    state.inspectTargetClassName = prevClass
                    state.popMode()
                    state.isFetchingInspection = true
                    state.inspectStaticAttributes = emptyList()
                    state.inspectInstanceAttributes = emptyList()
                    state.inspectMethods = emptyList()
                    state.inspectExpandedInstances.clear()
                    state.inspectExpandedInstancesError.clear()
                    state.inspectInstancesList = null
                    state.inspectInstancesTotalCount = 0
                    state.inspectInstancesExpanded = false
                    state.selectedClassIndex = 0
                    state.rpcError = null
                    Renderer.render(state)
                    scope.launch {
                        val (result, error) = RpcClient.inspectClass(prevClass)
                        state.sharedInspectResult.value = result
                        state.sharedRpcError.value = error
                        state.isFetchingInspection = false
                    }
                } else {
                    val prevMode = state.popMode()
                    if (prevMode == null && state.startedAsInspectPane) {
                        state.running = false
                    }
                    Renderer.render(state)
                }
            }

            is KeyEvent.Delete -> {
                if (state.mode == AppMode.DEBUG_HOOK_WATCH) {
                    val hooks = state.activeHooks
                    if (state.selectedHookIndex in hooks.indices) {
                        val target = hooks.removeAt(state.selectedHookIndex)
                        scope.launch {
                            RpcClient.toggleHook(target.className, target.memberSignature, false)
                        }
                        HookStore.save(state.appPackageName, state.activeHooks.toSet())
                        state.selectedHookIndex = state.selectedHookIndex.coerceAtMost(hooks.size - 1).coerceAtLeast(0)
                        Renderer.render(state)
                    }
                } else {
                    if (state.cursorPosition < state.inputBuffer.length) {
                        state.inputBuffer = state.inputBuffer.substring(0, state.cursorPosition) +
                                state.inputBuffer.substring(state.cursorPosition + 1)
                        onInputChanged(state)
                        Renderer.render(state)
                    }
                }
            }

            is KeyEvent.Timeout -> {
                var needsRender = false

                val updatedLogs = state.sharedBridgeLogs.value
                if (updatedLogs != null) {
                    state.sharedBridgeLogs.value = null
                    state.bridgeLogs = updatedLogs
                    // Always render logs if we are in a mode that shows them or during setup
                    if (state.mode == AppMode.DEBUG_ENTRYPOINT || state.gadgetInstallStatus == GadgetInstallStatus.WAITING_BRIDGE_SETUP) {
                        needsRender = true
                    }
                }

                if (Terminal.resized) {
                    Terminal.resized = false
                    needsRender = true
                }

                if (state.ctrlCPressed) {
                    val elapsed = currentTimeMillis() - state.ctrlCTimestamp
                    if (elapsed > 1500) {
                        state.ctrlCPressed = false
                        needsRender = true
                    }
                }

                // Gadget install status polling
                if (state.gadgetInstallStatus == GadgetInstallStatus.WAITING_BRIDGE_SETUP) {
                    state.gadgetSpinnerFrame++
                    needsRender = true
                    
                    val stepsUpdate = state.sharedGadgetSteps.value
                    if (stepsUpdate != null) {
                        state.gadgetInjectionSteps = stepsUpdate
                        needsRender = true
                    }

                    val gadgetUpdate = state.sharedGadgetResult.value
                    if (gadgetUpdate != null) {
                        state.sharedGadgetResult.value = null
                        state.gadgetInstallStatus = gadgetUpdate.first
                        state.gadgetErrorMessage = gadgetUpdate.second

                        if (gadgetUpdate.first == GadgetInstallStatus.SUCCESS) {
                            // Reset gadget state and proceed with tmux
                            state.gadgetInstallStatus = GadgetInstallStatus.IDLE
                            state.gadgetErrorMessage = null
                            
                            // Re-apply active hooks to the new session
                            scope.launch { RpcClient.syncAllHooks(state.activeHooks) }
                            
                            Renderer.render(state)
                            CommandExecutor.proceedWithTmux(state)
                            state.mode = AppMode.DEBUG_ENTRYPOINT
                            needsRender = true
                        }
                    }
                }

                // Task 7: Handle polled hook events
                val polledEvents = state.sharedHookEvents.value
                if (polledEvents != null) {
                    polledEvents.forEach { state.addHookEvent(it) }
                    state.sharedHookEvents.value = null
                    if (state.mode == AppMode.DEBUG_HOOK_WATCH) {
                        needsRender = true
                    }
                }

                if ((state.mode == AppMode.DEBUG_CLASS_FILTER && state.isFetchingClasses) || state.isFetchingInstances || state.isFetchingInstancesList) {
                    state.gadgetSpinnerFrame++
                    needsRender = true
                }

                if (state.isFetchingInstances) {
                    val countResult = state.sharedInstanceCountResult.value
                    val countError = state.sharedInstanceCountError.value
                    
                    if (countResult != null) {
                        state.instanceCounts[countResult.first] = countResult.second
                        state.sharedInstanceCountResult.value = null
                        state.isFetchingInstances = false
                        needsRender = true
                    }
                    
                    if (countError != null) {
                        state.rpcError = countError
                        state.sharedInstanceCountError.value = null
                        state.isFetchingInstances = false
                        needsRender = true
                    }
                }

                if (state.mode == AppMode.DEBUG_CLASS_FILTER) {
                    if (currentTimeMillis() - state.lastInputTimestamp > 500 && state.inputBuffer != state.lastSearchedParam) {
                        state.lastSearchedParam = state.inputBuffer
                        state.isFetchingClasses = true
                        needsRender = true
                        
                        scope.launch {
                            val (res, err) = RpcClient.listClasses(state.lastSearchedParam, state.appPackageName, 0, 200)
                            state.sharedFetchedClasses.value = res ?: emptyList()
                            state.sharedRpcError.value = err
                        }
                    }
                    
                    val fetched = state.sharedFetchedClasses.value
                    if (fetched != null) {
                        state.allFetchedClasses = fetched
                        state.displayedClasses = CommandExecutor.sortClasses(fetched, state.appPackageName, state.lastSearchedParam, state.showSyntheticClasses)
                        state.selectedClassIndex = if (state.displayedClasses.isNotEmpty()) 0 else -1
                        state.sharedFetchedClasses.value = null
                        state.isFetchingClasses = false
                        needsRender = true
                    }
                    
                    val err = state.sharedRpcError.value
                    if (err != null) {
                        state.rpcError = err
                        state.sharedRpcError.value = null
                        state.isFetchingClasses = false
                        needsRender = true
                    }

                    val updatedPkg = state.sharedAppPackageName.value
                    if (updatedPkg != null) {
                        state.sharedAppPackageName.value = null
                        loadAndSyncHooks(state, scope, updatedPkg)
// uncomment if you want to start to fill the input buffer with the package name
//                        if (state.inputBuffer.isEmpty()) {
//                            state.inputBuffer = updatedPkg
//                            state.cursorPosition = updatedPkg.length
//                            onInputChanged(state)
//                        }

                        if (state.displayedClasses.isNotEmpty()) {
                            state.displayedClasses = CommandExecutor.sortClasses(state.displayedClasses, state.appPackageName, state.lastSearchedParam, state.showSyntheticClasses)
                            needsRender = true
                        }
                    }
                }

                if (state.mode == AppMode.DEBUG_INSPECT_CLASS) {
                    val prevInspectStatus = state.sharedInspectResult.value
                    if (prevInspectStatus != null) {
                        state.inspectStaticAttributes = prevInspectStatus.staticAttributes
                        state.inspectInstanceAttributes = prevInspectStatus.instanceAttributes
                        state.inspectMethods = prevInspectStatus.methods
                        state.sharedInspectResult.value = null
                        needsRender = true
                    }
                    
                    val listInstancesResult = state.sharedInstancesListResult.value
                    if (listInstancesResult != null) {
                        state.inspectInstancesList = listInstancesResult.instances
                        state.inspectInstancesTotalCount = listInstancesResult.totalCount
                        state.instancesDetectionMethod = listInstancesResult.detectionMethod
                        state.isFetchingInstancesList = false
                        state.sharedInstancesListResult.value = null
                        needsRender = true
                    }
                    
                    val inspectInstanceResult = state.sharedInspectInstanceResult.value
                    if (inspectInstanceResult != null) {
                        val (id, newAttrs, isAppend) = inspectInstanceResult
                        if (isAppend) {
                            val current = state.inspectExpandedInstances[id] ?: emptyList()
                            // Remove the pagination marker before appending new attributes
                            val filtered = current.filter { !it.isPagination }
                            state.inspectExpandedInstances[id] = filtered + newAttrs
                        } else {
                            state.inspectExpandedInstances[id] = newAttrs
                        }
                        state.sharedInspectInstanceResult.value = null
                        needsRender = true
                    }

                    val inspectInstanceError = state.sharedInspectInstanceError.value
                    if (inspectInstanceError != null) {
                        state.inspectExpandedInstancesError[inspectInstanceError.first] = inspectInstanceError.second
                        state.sharedInspectInstanceError.value = null
                        needsRender = true
                    }

                    val previousError = state.sharedRpcError.value
                    if (previousError != null) {
                        state.rpcError = previousError
                        state.sharedRpcError.value = null
                        state.isFetchingInstancesList = false
                        needsRender = true
                    }
                }

                if (needsRender) {
                    Renderer.render(state)
                }
            }

            is KeyEvent.Unknown -> {}
        }
    }

    print(Ansi.DISABLE_MOUSE)
    Terminal.disableRawMode()
    HistoryStore.save(state.commandHistory)

    if (state.appPackageName.isNotEmpty()) {
        val disabledHooks = HookStore.load(state.appPackageName).map { it.copy(enabled = false) }.toSet()
        HookStore.save(state.appPackageName, disabledHooks)
    }

    if (initialMode == AppMode.DEFAULT) {
        val pidFile = "${CacheManager.cacheDir()}/bridge.pid"
        platform.posix.system("if [ -f \"$pidFile\" ]; then kill -9 \$(cat \"$pidFile\") 2>/dev/null; rm \"$pidFile\"; fi")
    }

    print(Ansi.SHOW_CURSOR)
    print(Ansi.CLEAR_SCREEN)
    print(Ansi.CURSOR_HOME)
    print(Ansi.RESET)
    Terminal.flush()
}
