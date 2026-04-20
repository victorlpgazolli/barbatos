object Renderer {
    private const val C_ORANGE   = "\u001b[38;5;208m"  // keywords / modifiers
    private const val C_PURPLE   = "\u001b[38;5;176m"  // field / attribute (soft purple like AS)
    private const val C_BLUE     = "\u001b[38;5;75m"   // object reference / Barbatos brand
    private const val C_GREEN    = "\u001b[38;5;71m"   // active instance / live count
    private const val C_CYAN     = "\u001b[38;5;39m"   // custom implementation / override
    private const val C_DARK_GRAY = "\u001b[38;5;238m" // destroyed instances
    private const val C_MID_GRAY  = "\u001b[38;5;244m" // secondary text
    private const val C_SEP      = "\u001b[38;5;237m"  // separator lines

    // Header background (simulated via bold separator)
    private const val C_HEADER_BG = "\u001b[48;5;235m" // dark bg for header bar

    // Keep existing (used elsewhere)
    private const val DIM_GRAY = "\u001b[90m"
    private const val WHITE = "\u001b[97m"
    private const val LIGHT_GRAY = "\u001b[38;5;250m"
    private const val PROPERTY_NAME = "\u001b[38;5;252m"
    private const val TYPE_BOOLEAN = "\u001b[38;5;39m"
    private const val TYPE_NUMBER = "\u001b[38;5;114m"
    private const val TYPE_STRING = "\u001b[38;5;173m"
    private const val TYPE_OTHER = "\u001b[38;5;43m"
    private const val RESET = "\u001b[0m"
    private const val J_MODIFIER = "\u001b[38;5;208m"
    private const val J_TYPE = "\u001b[38;5;114m"
    private const val J_PACKAGE = "\u001b[90m"
    private const val J_CLASS = "\u001b[97m"
    private const val J_METHOD = "\u001b[38;5;222m"
    private const val J_NUMBER = "\u001b[38;5;173m"
    private const val ITALIC = "\u001b[3m"

    private const val C_SKY_GRAY = "\u001b[38;5;240m"
    private val SKY_BLUES = listOf(18, 19, 20, 21, 27, 33, 39, 45, 51)
    private val SKY_LOGO_LINES = listOf(
        """ __/\\\\\\\\\\\\\_________________________________/\\\________________________________________________________________        """,
        """ _\/\\\/////////\\\______________________________\/\\\________________________________________________________________       """,
        """  _\/\\\_______\/\\\______________________________\/\\\___________________________/\\\_________________________________      """,
        """   _\/\\\\\\\\\\\\\\___/\\\\\\\\\_____/\\/\\\\\\\__\/\\\_________/\\\\\\\\\_____/\\\\\\\\\\\_____/\\\\\_____/\\\\\\\\\\_     """,
        """    _\/\\\/////////\\\_\////////\\\___\/\\\/////\\\_\/\\\\\\\\\__\////////\\\___\////\\\////____/\\\///\\\__\/\\\//////__    """,
        """     _\/\\\_______\/\\\___/\\\\\\\\\\__\/\\\___\///__\/\\\////\\\___/\\\\\\\\\\_____\/\\\_______/\\\__\//\\\_\/\\\\\\\\\\_   """,
        """      _\/\\\_______\/\\\__/\\\/////\\\__\/\\\_________\/\\\__\/\\\__/\\\/////\\\_____\/\\\_/\\__\//\\\__/\\\__\////////\\\_  """,
        """       _\/\\\\\\\\\\\\\/__\//\\\\\\\\/\\_\/\\\_________\/\\\\\\\\\__\//\\\\\\\\/\\____\//\\\\\____\///\\\\\/____/\\\\\\\\\\_ """,
        """        _\/////////////_____\////////\//__\///__________\/////////____\////////\//______\/////_______\/////_____\//////////__"""
    )
    private const val INSTRUCTIONS_TEXT = """
    
    Type your command and press Enter to execute it.
    Try typing 'debug' to start a new debug session.
    Press Ctrl+C to exit.
"""

    private fun renderHeader(buf: StringBuilder, state: AppState, termWidth: Int) {
        val pkg = state.appPackageName.ifEmpty { "no package" }
        val (statusText, isError) = when {
            (state.gadgetInstallStatus == GadgetInstallStatus.SUCCESS || state.gadgetInstallStatus == GadgetInstallStatus.IDLE)
                    && state.rpcError == null -> Pair("● connected", false)
            else -> Pair("● disconnected", true)
        }
        val leftText = " Barbatos"

        // Visible character counts (no escape codes)
        val leftLen  = leftText.length          // 4
        val rightLen = statusText.length + 1    // + trailing space

        buf.append(C_HEADER_BG)
        buf.append(C_BLUE).append(leftText).append(RESET).append(C_HEADER_BG)
        buf.append(C_MID_GRAY).append("  ·  ").append(RESET).append(C_HEADER_BG)
        buf.append(C_MID_GRAY).append(pkg).append(RESET).append(C_HEADER_BG)
        buf.append(C_MID_GRAY).append("  ·  ").append(RESET).append(C_HEADER_BG)

        // Pad to push status to the right
        val visibleSoFar = leftLen + 5 + pkg.length + 5
        val padding = maxOf(1, termWidth - visibleSoFar - rightLen)
        buf.append(" ".repeat(padding))

        buf.append(
            if (isError) Ansi.RED else C_GREEN
        )
        buf.append(statusText)
        buf.append(
            if (isError) Ansi.RESET else RESET
        ).append(C_HEADER_BG)

        buf.append(" ")
        buf.append(RESET).append("\n")

        // Separator
        buf.append(C_SEP).append("─".repeat(termWidth)).append(RESET).append("\n")
    }

    private fun renderBreadcrumb(buf: StringBuilder, state: AppState, termWidth: Int) {
        val currentStage = when (state.mode) {
            AppMode.DEBUG_CLASS_FILTER -> 0
            AppMode.DEBUG_INSPECT_CLASS, AppMode.DEBUG_EDIT_ATTRIBUTE -> 1
            AppMode.DEBUG_HOOK_WATCH -> 2
            else -> -1
        }

        val stages = listOf("Classes", "Inspect", "Watch")
        val sep = " › "

        buf.append(" ")
        stages.forEachIndexed { index, name ->
            if (index > 0) buf.append(C_SEP).append(sep).append(RESET)
            if (index == currentStage) {
                buf.append(WHITE).append(name).append(RESET)
            } else {
                buf.append(DIM_GRAY).append(name).append(RESET)
            }
        }
        buf.append("\n")
        buf.append(C_SEP).append("─".repeat(termWidth)).append(RESET).append("\n")
    }

    private fun renderClassPackageSubtitle(buf: StringBuilder, state: AppState, termWidth: Int) {
        val fullName = state.inspectTargetClassName
        val lastDot  = fullName.lastIndexOf('.')
        val pkg      = if (lastDot != -1) fullName.substring(0, lastDot) else ""
        val name     = if (lastDot != -1) fullName.substring(lastDot + 1) else fullName

        buf.append(" ").append(WHITE).append(name).append(RESET)
        if (pkg.isNotEmpty()) {
            buf.append(" ").append(DIM_GRAY).append("(").append(pkg).append(")").append(RESET)
        }
        buf.append("\n")
        buf.append(C_SEP).append("─".repeat(termWidth)).append(RESET).append("\n")
    }

    private fun ansiVisibleLength(text: String): Int {
        var count = 0
        var i = 0
        while (i < text.length) {
            if (text[i] == '\u001b') {
                i++
                if (i < text.length && text[i] == '[') {
                    i++
                    while (i < text.length && text[i] !in 'a'..'z' && text[i] !in 'A'..'Z') {
                        i++
                    }
                    i++
                }
            } else {
                count++
                i++
            }
        }
        return count
    }

    fun render(state: AppState) {
        val (termWidth, termHeight) = Terminal.getSize()
        val width = if (termWidth > 4) termWidth - 2 else 70
        val buf = StringBuilder()

        buf.append(Ansi.CLEAR_SCREEN)
        buf.append(Ansi.CURSOR_HOME)
        buf.append(Ansi.HIDE_CURSOR)

        val hasInputBox: Boolean
        if (state.mode == AppMode.DEFAULT) {
            renderLogo(buf)
            renderWelcome(buf)
            renderHistory(buf, state)
            renderCtrlCWarning(buf, state)
            renderInputBox(buf, state, width)
            renderSuggestions(buf, state)
            hasInputBox = true
        } else if (state.mode == AppMode.DEBUG_CLASS_FILTER) {
            renderHeader(buf, state, termWidth)
            renderBreadcrumb(buf, state, termWidth)
            renderClassFetchStatus(buf, state)
            renderInputBox(buf, state, termWidth - 2)
            renderClassList(buf, state, termWidth, termHeight)
            hasInputBox = true
        } else if (state.mode == AppMode.DEBUG_INSPECT_CLASS || state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) {
            renderHeader(buf, state, termWidth)
            renderBreadcrumb(buf, state, termWidth)
            renderClassPackageSubtitle(buf, state, termWidth)
            renderCtrlCWarning(buf, state)
            if (state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) {
                renderInputBox(buf, state, termWidth - 2)
            }
            renderInspectClassList(buf, state, termWidth, termHeight)
            hasInputBox = (state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE)
        } else if (state.mode == AppMode.DEBUG_ENTRYPOINT) {
            renderLogo(buf)
            renderWelcome(buf)
            renderCtrlCWarning(buf, state)
            renderDebugEntrypoint(buf, state)
            hasInputBox = false
        } else if (state.mode == AppMode.DEBUG_HOOK_WATCH) {
            renderHeader(buf, state, termWidth)
            renderBreadcrumb(buf, state, termWidth)
            renderHookWatchMode(buf, state, termWidth, termHeight)
            hasInputBox = false
        } else {
            hasInputBox = false
        }

        renderStatusMessage(buf, state, termWidth)
        renderFooter(buf, state, termWidth, termHeight)

        if (hasInputBox) {
            buf.append(Ansi.RESTORE_CURSOR)
        }
        buf.append(Ansi.SHOW_CURSOR)

        print(buf.toString())
        Terminal.flush()
    }

    private fun renderStatusMessage(buf: StringBuilder, state: AppState, termWidth: Int) {
        val message = state.statusMessage ?: return
        val elapsed = currentTimeMillis() - state.statusMessageTimestamp
        if (elapsed > 2000) {
            state.statusMessage = null
            return
        }

        val padding = maxOf(0, (termWidth - message.length - 4) / 2)
        buf.append("\n")
        buf.append(" ".repeat(padding))
        buf.append(C_GREEN).append("╭").append("─".repeat(message.length + 2)).append("╮").append(RESET).append("\n")
        buf.append(" ".repeat(padding))
        buf.append(C_GREEN).append("│ ").append(WHITE).append(message).append(C_GREEN).append(" │").append(RESET).append("\n")
        buf.append(" ".repeat(padding))
        buf.append(C_GREEN).append("╰").append("─".repeat(message.length + 2)).append("╯").append(RESET).append("\n")
    }

    private fun renderFooter(buf: StringBuilder, state: AppState, termWidth: Int, termHeight: Int) {
        data class FooterKey(val key: String, val label: String)

        val keys: List<FooterKey> = when (state.mode) {
            AppMode.DEFAULT -> listOf(
                FooterKey("↑↓", "History"),
                FooterKey("Tab", "Autocomplete"),
                FooterKey("Enter", "Execute"),
                FooterKey("Alt+C", "Copy"),
                FooterKey("Ctrl+C", "Quit")
            )
            AppMode.DEBUG_ENTRYPOINT -> listOf(
                FooterKey("↑↓", "Navigate"),
                FooterKey("Enter", "Select"),
                FooterKey("R", "Restart Bridge"),
                FooterKey("Ctrl+C", "Quit")
            )
            AppMode.DEBUG_CLASS_FILTER -> listOf(
                FooterKey("↑↓", "Navigate"),
                FooterKey("Enter", "Inspect"),
                FooterKey("\\", "Count Instances"),
                FooterKey("]", if (state.showSyntheticClasses) "Hide Synthetic Classes" else "Show Synthetic Classes"),
                FooterKey("Alt+C", "Copy"),
                FooterKey("Esc", "Back"),
                FooterKey("Ctrl+C", "Quit")
            )
            AppMode.DEBUG_INSPECT_CLASS -> {
                val rows = state.buildInspectRows()
                val selectedRow = rows.getOrNull(state.selectedClassIndex)
                val eLabel = when (selectedRow) {
                    is InspectRow.StaticMethodRow -> "Override"
                    is InspectRow.InstanceAttributeRow, is InspectRow.StaticAttributeRow -> "Edit Value"
                    else -> "Edit"
                }
                listOf(
                    FooterKey("↑↓", "Navigate"),
                    FooterKey("H", "Hook"),
                    FooterKey("E", eLabel),
                    FooterKey("W", "Watch"),
                    FooterKey("I", "Inspect"),
                    FooterKey("Alt+C", "Copy"),
                    FooterKey("Esc", "Back"),
                    FooterKey("Ctrl+C", "Quit")
                )
            }
            AppMode.DEBUG_HOOK_WATCH -> listOf(
                FooterKey("↑↓", "Select"),
                FooterKey("E", "Edit Impl"),
                FooterKey("X", "Run Once"),
                FooterKey("←→", "Scroll"),
                FooterKey("I", "Inspect"),
                FooterKey("Space", "Toggle"),
                FooterKey("Alt+C", "Copy"),
                FooterKey("Del", "Remove"),
                FooterKey("C", "Clear"),
                FooterKey("Esc", "Back")
            )

            AppMode.DEBUG_EDIT_ATTRIBUTE -> listOf(
                FooterKey("Enter", "Save"),
                FooterKey("Esc", "Cancel"),
                FooterKey("Ctrl+C", "Quit")
            )
        }

        buf.append(Ansi.moveTo(termHeight, 1))
        buf.append(C_HEADER_BG)
        buf.append(" ")
        var visibleLen = 1
        for (k in keys) {
            // Colored key label + dim description
            buf.append(WHITE).append(k.key).append(RESET).append(C_HEADER_BG)
            buf.append(C_MID_GRAY).append(" ${k.label}").append(RESET).append(C_HEADER_BG)
            buf.append("   ")
            visibleLen += k.key.length + 1 + k.label.length + 3
        }
        buf.append(" ".repeat(maxOf(0, termWidth - visibleLen)))
        buf.append(RESET)
    }

    private fun renderLogo(buf: StringBuilder) {
        for (line in SKY_LOGO_LINES) {
            val trimmed = line.trimEnd()
            val width = trimmed.length
            for ((x, char) in line.withIndex()) {
                when (char) {
                    '_' -> {
                        buf.append(C_SKY_GRAY).append(char).append(RESET)
                    }
                    ' ', '\n' -> {
                        buf.append(char)
                    }
                    else -> {
                        if (width > 0) {
                            val idx = ((x.toDouble() / width) * (SKY_BLUES.size - 1)).toInt()
                                .coerceIn(0, SKY_BLUES.size - 1)
                            val colorCode = SKY_BLUES[idx]
                            buf.append("\u001b[38;5;${colorCode}m").append(char).append(RESET)
                        } else {
                            buf.append(char)
                        }
                    }
                }
            }
            buf.append("\n")
        }
        buf.append("\n")
        buf.append(C_SKY_GRAY).append("      [ BARBATOS - Android Runtime Debugger w/ MCP server support. ]").append(RESET).append("\n")
        buf.append("\n")
    }

    private fun renderWelcome(buf: StringBuilder) {
        buf.append(" ")
        buf.append(Ansi.WHITE)
        buf.append(INSTRUCTIONS_TEXT)
        buf.append(Ansi.RESET)
        buf.append("\n\n")
    }

    private fun renderDebugEntrypoint(buf: StringBuilder, state: AppState) {
        val options = listOf(
            "Search & inspect classes instances",
            "Hook methods & watch changes"
        )
        
        buf.append("  ${Ansi.WHITE}Select debug action:${Ansi.RESET}\n")
        
        for ((index, option) in options.withIndex()) {
            val isSelected = index == state.debugEntrypointIndex
            val prefix = ListRenderer.selectionPrefix(isSelected, "  ")
            val color = if (isSelected) Ansi.WHITE else Ansi.DIM
            buf.append(prefix).append(color).append(option).append(Ansi.RESET).append("\n")
        }

        buf.appendBridgeLogBox(state.bridgeLogs)
    }

    private fun StringBuilder.appendBridgeLogBox(logs: List<String>, logWidth: Int = 100) {
        if (logs.isEmpty()) return
        
        append("\n")
        val topBorder = "╭" + "─".repeat(logWidth) + "╮"
        val bottomBorder = "╰" + "─".repeat(logWidth) + "╯"

        append(Ansi.DIM)
        append("  ").append(topBorder).append("\n")
        append("  │ ").append("Bridge Activity".padEnd(logWidth - 1)).append("│\n")
        append("  ├").append("─".repeat(logWidth)).append("┤\n")

        // Show only the last 10 lines of logs to keep it compact and prevent UI glitches
        val displayLogs = logs.takeLast(10)
        for (logLine in displayLogs) {
            val truncatedLog = if (logLine.length > logWidth - 2) logLine.substring(0, logWidth - 5) + "..." else logLine
            append("  │ ")
            append("\u001b[38;5;244m") // Discrete gray color
            append(truncatedLog.padEnd(logWidth - 1))
            append(Ansi.DIM)
            append("│\n")
        }
        
        // Fill remaining space if less than 3 logs (minimum size)
        if (displayLogs.size < 3) {
            for (i in 0 until (3 - displayLogs.size)) {
                append("  │ ").append(" ".repeat(logWidth - 1)).append("│\n")
            }
        }

        append("  ").append(bottomBorder).append(Ansi.RESET).append("\n")
    }

    private fun renderHistory(buf: StringBuilder, state: AppState) {
        val lastDebugIndex = state.commandHistory.lastIndexOf("debug")
        for ((index, cmd) in state.commandHistory.withIndex()) {
            buf.append(Ansi.DIM)
            buf.append(" > ")
            buf.append(Ansi.RESET)
            buf.append(Ansi.WHITE)
            buf.append(cmd)
            buf.append(Ansi.RESET)
            buf.append("\n")

            // Show gadget install status only below the LAST "debug" command
            if (index == lastDebugIndex && state.gadgetInstallStatus != GadgetInstallStatus.IDLE && state.gadgetInstallStatus != GadgetInstallStatus.SUCCESS) {
                renderGadgetStatus(buf, state)
            }
        }
    }

    private fun renderGadgetStatus(buf: StringBuilder, state: AppState) {
        val status = state.gadgetInstallStatus
        val frame = ListRenderer.spinnerFrame(state.gadgetSpinnerFrame)

        if (status != GadgetInstallStatus.IDLE) {
            buf.append("\n   ${Ansi.WHITE}Initializing Debugging Session:${Ansi.RESET}\n")
            
            if (state.gadgetInjectionSteps.isEmpty()) {
                buf.append("   [${LIGHT_GRAY}$frame${Ansi.RESET}] ${Ansi.WHITE}Connecting to bridge...${Ansi.RESET}\n")
            } else {
                for (step in state.gadgetInjectionSteps) {
                    when (step.status) {
                        "completed" -> buf.append("   [${Ansi.GREEN}✓${Ansi.RESET}] ${Ansi.DIM}${step.title}${Ansi.RESET}\n")
                        "skipped" -> buf.append("   [${Ansi.DIM}S${Ansi.RESET}] ${Ansi.DIM}${Ansi.STRIKETHROUGH}${step.title}${Ansi.RESET}\n")
                        "error" -> buf.append("   [${Ansi.RED}✗${Ansi.RESET}] ${Ansi.RED}${step.title}${Ansi.RESET}\n")
                        "running" -> buf.append("   [${LIGHT_GRAY}$frame${Ansi.RESET}] ${Ansi.WHITE}${step.title}${Ansi.RESET}\n")
                        else -> buf.append("   [ ] ${Ansi.DIM}${step.title}${Ansi.RESET}\n")
                    }
                }
            }
            
            buf.appendBridgeLogBox(state.bridgeLogs)
        }

        if (status == GadgetInstallStatus.ERROR) {
            val errorMsg = state.gadgetErrorMessage ?: "Unknown error"
            buf.append("   ${Ansi.RED}Setup failed: $errorMsg${Ansi.RESET}\n")
        }
    }

    private fun renderClassFetchStatus(buf: StringBuilder, state: AppState) {
        if (state.isFetchingClasses) {
            val frame = ListRenderer.spinnerFrame(state.gadgetSpinnerFrame)
            val suffix = if (state.inputBuffer.length < 2) " (this could take a while)" else ""
            buf.append("   $LIGHT_GRAY$frame Fetching available classes$suffix$RESET\n")
        } else if (state.isFetchingInstances) {
            val frame = ListRenderer.spinnerFrame(state.gadgetSpinnerFrame)
            buf.append("   $LIGHT_GRAY$frame Searching instances...$RESET\n")
        }
    }

    private fun renderCtrlCWarning(buf: StringBuilder, state: AppState) {
        if (state.ctrlCPressed) {
            buf.append(Ansi.YELLOW)
            buf.append(" Press Ctrl+C again to exit.")
            buf.append(Ansi.RESET)
            buf.append("\n")
        }
    }

    private fun renderInputBox(buf: StringBuilder, state: AppState, width: Int) {
        val innerWidth = width - 2
        val topBorder = "╭" + "─".repeat(innerWidth) + "╮"
        val bottomBorder = "╰" + "─".repeat(innerWidth) + "╯"

        val placeholder = if (state.mode == AppMode.DEFAULT) "Type your command" else "Search classes..."

        val visibleInputLength = if (state.inputBuffer.isEmpty()) {
            " >   $placeholder".length
        } else {
            " > ${state.inputBuffer}".length
        }
        
        val padding = maxOf(0, innerWidth - visibleInputLength)

        buf.append(Ansi.DIM)
        buf.append(" ")
        buf.append(topBorder)
        buf.append(Ansi.RESET)
        buf.append("\n")

        buf.append(Ansi.DIM)
        buf.append(" │")
        buf.append(Ansi.RESET)
        buf.append("${Ansi.WHITE} > ${Ansi.RESET}")
        
        if (state.inputBuffer.isEmpty()) {
            buf.append(Ansi.SAVE_CURSOR)
            buf.append("${Ansi.DIM}  $placeholder${Ansi.RESET}")
        } else {
            val textBeforeCursor = state.inputBuffer.substring(0, state.cursorPosition)
            val textAfterCursor = state.inputBuffer.substring(state.cursorPosition)
            buf.append("${Ansi.WHITE}$textBeforeCursor")
            buf.append(Ansi.SAVE_CURSOR)
            buf.append("$textAfterCursor${Ansi.RESET}")
        }

        buf.append(" ".repeat(padding))
        buf.append(Ansi.DIM)
        buf.append("│")
        buf.append(Ansi.RESET)
        buf.append("\n")

        buf.append(Ansi.DIM)
        buf.append(" ")
        buf.append(bottomBorder)
        buf.append(Ansi.RESET)
        buf.append("\n")
    }

    private fun renderSuggestions(buf: StringBuilder, state: AppState) {
        if (state.suggestions.isEmpty()) return

        for ((index, cmd) in state.suggestions.withIndex()) {
            val isSelected = index == state.selectedSuggestionIndex
            val prefix = ListRenderer.selectionPrefix(isSelected, "  ")
            val nameColor = if (isSelected) Ansi.GREEN else Ansi.DIM
            val descColor = Ansi.DIM

            buf.append(prefix)
            buf.append(nameColor)
            buf.append(cmd.name.padEnd(17))
            buf.append(Ansi.RESET)
            buf.append(descColor)
            buf.append(cmd.description)
            buf.append(Ansi.RESET)
            buf.append("\n")
        }
    }

    private fun renderClassList(buf: StringBuilder, state: AppState, termWidth: Int, termHeight: Int) {
        if (state.rpcError != null) {
            buf.append(Ansi.RED).append("  Error: ${state.rpcError}").append(RESET).append("\n")
            return
        }

        if (state.isFetchingClasses && state.displayedClasses.isEmpty()) {
            return // spinner shown by renderClassFetchStatus above
        }

        if (state.displayedClasses.isEmpty() && state.inputBuffer.isNotEmpty() && !state.isFetchingClasses) {
            buf.append(DIM_GRAY).append("  No matches found.\n").append(RESET)
            return
        }

        val isFetching = state.isFetchingClasses || state.isFetchingInstances
        val actualFixedLines = 2 + 2 + (if (isFetching) 1 else 0) + 3 // Header(2) + Breadcrumb(2) + Fetch(1|0) + Input(3)
        val maxItems = maxOf(3, termHeight - actualFixedLines - 2) // -1 for footer, -1 for scroll indicator

        val (startIdx, endIdx) = ListRenderer.computeViewport(
            state.displayedClasses.size, state.selectedClassIndex, maxItems
        )

        for (i in startIdx until endIdx) {
            val className  = state.displayedClasses[i]
            val isSelected = i == state.selectedClassIndex
            val prefix     = ListRenderer.selectionPrefix(isSelected, "  ")

            val lastDot     = className.lastIndexOf('.')
            val packagePart = if (lastDot != -1) className.substring(0, lastDot) else ""
            val namePart    = if (lastDot != -1) className.substring(lastDot + 1) else className

            val query           = state.lastSearchedParam
            val nameBaseColor   = if (isSelected) WHITE else LIGHT_GRAY
            val packageBaseColor = DIM_GRAY

            fun highlight(text: String, baseColor: String): String {
                if (query.isEmpty() || !text.contains(query, ignoreCase = true)) return "$baseColor$text"
                val sb  = StringBuilder()
                var pos = 0
                val lo  = text.lowercase()
                val lq  = query.lowercase()
                while (true) {
                    val start = lo.indexOf(lq, pos)
                    if (start == -1) { sb.append(baseColor).append(text.substring(pos)); break }
                    sb.append(baseColor).append(text.substring(pos, start))
                    sb.append("\u001b[38;5;220m").append(text.substring(start, start + query.length)) // yellow highlight
                    pos = start + query.length
                }
                return sb.toString()
            }

            // Count badge
            val count = state.instanceCounts[className]
            val countBadge = when {
                count == null -> ""
                count > 0     -> "  ${C_GREEN}$count inst${RESET}"
                else          -> "  ${DIM_GRAY}0 inst${RESET}"
            }

            val prefixVisible = ansiVisibleLength(prefix)
            val countVisible = ansiVisibleLength(countBadge)
            val availableLen = maxOf(0, termWidth - 1 - prefixVisible - countVisible)

            val fullString = buildString {
                append(namePart)
                if (packagePart.isNotEmpty()) {
                    append(" (").append(packagePart).append(")")
                }
            }

            val displayName: String
            val displayPkg: String
            if (fullString.length > availableLen) {
                if (namePart.length > availableLen) {
                    displayName = namePart.substring(0, maxOf(0, availableLen - 3)) + "..."
                    displayPkg = ""
                } else {
                    displayName = namePart
                    val pkgAvail = availableLen - namePart.length - 3
                    if (pkgAvail > 3) {
                        displayPkg = packagePart.substring(0, pkgAvail - 3) + "..."
                    } else {
                        displayPkg = ""
                    }
                }
            } else {
                displayName = namePart
                displayPkg = packagePart
            }

            // Row: Name (package ...)
            buf.append(prefix)
            buf.append(highlight(displayName, nameBaseColor))
            if (displayPkg.isNotEmpty()) {
                buf.append(" ").append(DIM_GRAY).append("(").append(highlight(displayPkg, packageBaseColor)).append(")").append(RESET)
            }
            buf.append(countBadge)
            buf.append(RESET).append("\n")
        }

        ListRenderer.renderScrollIndicator(buf, startIdx, endIdx, state.displayedClasses.size, termWidth)
    }

    private fun renderInspectHeader(buf: StringBuilder, state: AppState, width: Int) {
        val innerWidth = width - 2
        val topBorder = "╭" + "─".repeat(innerWidth) + "╮"
        val bottomBorder = "╰" + "─".repeat(innerWidth) + "╯"

        val maxTitleLen = maxOf(10, innerWidth - 15)
        val displayClassName = if (state.inspectTargetClassName.length > maxTitleLen) {
            "..." + state.inspectTargetClassName.takeLast(maxTitleLen - 3)
        } else {
            state.inspectTargetClassName
        }
        val title = " Inspecting: $displayClassName "
        val padding = maxOf(0, innerWidth - title.length)

        buf.append(Ansi.DIM).append(" ").append(topBorder).append(Ansi.RESET).append("\n")
        buf.append(Ansi.DIM).append(" │").append(Ansi.RESET)
        buf.append(WHITE).append(title).append(Ansi.RESET)
        buf.append(" ".repeat(padding)).append(Ansi.DIM).append("│").append(Ansi.RESET).append("\n")
        
        val methodCount = state.activeHooks.count { it.type == HookType.METHOD }
        val fieldCount = state.activeHooks.count { it.type == HookType.FIELD }
        
        val methodLabel = if (methodCount == 1) "method" else "methods"
        val fieldLabel = if (fieldCount == 1) "field" else "fields"
        
        val hooksPrefix = " Hooks: [ "
        val methodStr = "$methodCount $methodLabel"
        val separator = ", "
        val fieldStr = "$fieldCount $fieldLabel"
        val hooksSuffix = " ] "
        
        val visibleHooksLen = hooksPrefix.length + methodStr.length + separator.length + fieldStr.length + hooksSuffix.length
        val hooksPadding = maxOf(0, innerWidth - visibleHooksLen)
        
        buf.append(Ansi.DIM).append(" │").append(Ansi.RESET)
        buf.append(Ansi.WHITE).append(hooksPrefix).append(Ansi.RESET)
        buf.append(J_METHOD).append(methodStr).append(Ansi.RESET)
        buf.append(Ansi.WHITE).append(separator).append(Ansi.RESET)
        buf.append(C_PURPLE).append(fieldStr).append(Ansi.RESET)
        buf.append(Ansi.WHITE).append(hooksSuffix).append(Ansi.RESET)
        buf.append(" ".repeat(hooksPadding)).append(Ansi.DIM).append("│").append(Ansi.RESET).append("\n")

        buf.append(Ansi.DIM).append(" ").append(bottomBorder).append(Ansi.RESET).append("\n")
    }

    private fun renderInspectClassList(buf: StringBuilder, state: AppState, termWidth: Int, termHeight: Int) {
        if (state.rpcError != null) {
            buf.append(Ansi.RED).append("  Error: ${state.rpcError}").append(Ansi.RESET).append("\n")
            return
        }

        if (state.isFetchingInspection && state.inspectStaticAttributes.isEmpty() && state.inspectInstanceAttributes.isEmpty() && state.inspectMethods.isEmpty()) {
            buf.append(Ansi.DIM).append("  Parsing class structure via Frida...").append(Ansi.RESET).append("\n")
            return
        }

        val rows = state.buildInspectRows()
        if (rows.isEmpty()) {
            val emptyBox = """
                ${Ansi.DIM}╭─────────────────────────────╮${Ansi.RESET}
                ${Ansi.DIM}│${Ansi.RESET}    No items to display      ${Ansi.DIM}│${Ansi.RESET}
                ${Ansi.DIM}╰─────────────────────────────╯${Ansi.RESET}
            """.trimIndent()
            for (line in emptyBox.lines()) {
                buf.append("   ").append(line).append("\n")
            }
            return
        }

        // Fixed lines: Header(2) + Breadcrumb(2) + Subtitle(2) + Warning(1) + ScrollIndicator(1) + Footer(1) = 9
        // If editing: + InputBox(3) = 12
        val fixedLines = if (state.mode == AppMode.DEBUG_EDIT_ATTRIBUTE) 12 else 9
        val maxItems = maxOf(3, termHeight - fixedLines)
        val (startIdx, endIdx) = ListRenderer.computeViewport(rows.size, state.selectedClassIndex, maxItems)

        for (i in startIdx until endIdx) {
            val row = rows[i]
            val isSelected = i == state.selectedClassIndex
            val selectionMarker = ListRenderer.selectionPrefix(isSelected, "    ")
            
            var visualIndent = 8
            val baseIndentStr = when (row) {
                is InspectRow.StaticAttributeRow, is InspectRow.StaticMethodRow -> {
                    visualIndent += 4
                    "    "
                }
                is InspectRow.InstanceRow -> {
                    visualIndent += 8
                    "        "
                }
                is InspectRow.InstanceAttributeRow -> {
                    val indentBuilder = StringBuilder("        ")
                    row.treeFlags.forEachIndexed { index, isLast ->
                        visualIndent += 4
                        if (index == row.treeFlags.lastIndex) {
                            indentBuilder.append(if (isLast) "└── " else "├── ")
                        } else {
                            indentBuilder.append(if (isLast) "    " else "│   ")
                        }
                    }
                    indentBuilder.toString()
                }
                is InspectRow.InfoRow -> {
                    val indentBuilder = StringBuilder("        ")
                    row.treeFlags.forEach { isLast ->
                        visualIndent += 4
                        indentBuilder.append(if (isLast) "    " else "│   ")
                    }
                    visualIndent += 4
                    indentBuilder.append("    ")
                    indentBuilder.toString()
                }
                else -> ""
            }
            val prefix = "$baseIndentStr$selectionMarker"
            val prefixVisible = ansiVisibleLength(prefix)
            
            when (row) {
                is InspectRow.SectionStaticRow -> {
                    val arrow  = if (row.isExpanded) "▾" else "▸"
                    val detail = if (row.isExpanded) "" else "  ${DIM_GRAY}(press Enter to expand)${RESET}"
                    buf.append(prefix)
                        .append(C_MID_GRAY).append("$arrow Static members").append(RESET)
                        .append(detail).append(RESET).append("\n")
                }
                is InspectRow.SectionInstancesRow -> {
                    val arrow     = if (row.isExpanded) "▾" else "▸"
                    val countInfo = state.inspectInstancesList?.let { " ${C_GREEN}${state.inspectInstancesTotalCount} found${RESET}" } ?: ""
                    buf.append(prefix)
                        .append(C_MID_GRAY).append("$arrow Instances").append(RESET)
                        .append(countInfo).append(RESET).append("\n")
                    if (row.isExpanded && state.inspectInstancesList != null) {
                        val (badgeColor, badgeLabel) = when (state.instancesDetectionMethod) {
                            "stateflow" -> Pair(C_GREEN,    "● StateFlow  — showing latest instances of this class")
                            "livedata"  -> Pair(C_BLUE,     "● LiveData   — showing latest instances of this class")
                            else        -> Pair(C_MID_GRAY, "○ Heap scan  — showing all instances of this class (caution: it may contain stale references)")
                        }
                        buf.append(prefix).append("  $badgeColor$badgeLabel$RESET\n")
                    }
                }
                is InspectRow.StaticAttributeRow -> {
                    val memberName = StringUtils.extractMemberName(row.attribute)
                    val matchingHook = state.activeHooks.find {
                        it.className == state.inspectTargetClassName && it.memberSignature == row.attribute
                    }
                    val isHooked = matchingHook != null
                    val hookMarker = if (isHooked) "${C_ORANGE}[H] ${RESET}" else ""
                    val hookMarkerLen = if (isHooked) 4 else 0

                    val maxLen = maxOf(0, termWidth - 1 - prefixVisible - 2 - hookMarkerLen)
                    val displayMember = if (memberName.length > maxLen) memberName.take(maxOf(0, maxLen - 3)) + "..." else memberName

                    val nameStr    = "${C_PURPLE}$displayMember${RESET}"

                    // Right-align padding calculation (no more hint on right)
                    val pad = maxOf(1, termWidth - prefixVisible - 2 - hookMarkerLen - displayMember.length)

                    buf.append(prefix).append("  ")
                        .append(hookMarker)
                        .append(nameStr)
                        .append(" ".repeat(pad))
                        .append(RESET).append("\n")
                }
                is InspectRow.StaticMethodRow -> {
                    val memberName = StringUtils.extractMemberName(row.method)
                    val params     = StringUtils.extractParams(row.method)
                    val matchingHook = state.activeHooks.find {
                        it.className == state.inspectTargetClassName && it.memberSignature == row.method
                    }
                    val isHooked = matchingHook != null
                    val isOverridden = matchingHook?.implementation != null
                    
                    val hookMarker = if (isHooked) "${C_ORANGE}[H] ${RESET}" else ""
                    val hookMarkerLen = if (isHooked) 4 else 0
                    val overrideMarker = if (isOverridden) "${C_CYAN}[C] ${RESET}" else ""
                    val overrideMarkerLen = if (isOverridden) 4 else 0

                    val markersLen = hookMarkerLen + overrideMarkerLen
                    val maxLen = maxOf(0, termWidth - 1 - prefixVisible - 2 - markersLen)
                    
                    val displayMember: String
                    val displayParams: String
                    if (memberName.length + 2 + params.length > maxLen) {
                        if (memberName.length > maxLen) {
                            displayMember = memberName.take(maxOf(0, maxLen - 3)) + "..."
                            displayParams = ""
                        } else {
                            displayMember = memberName
                            val pAvail = maxLen - memberName.length - 2
                            if (pAvail > 3) {
                                displayParams = params.take(pAvail - 3) + "..."
                            } else {
                                displayParams = ""
                            }
                        }
                    } else {
                        displayMember = memberName
                        displayParams = params
                    }

                    val nameStr    = "${J_METHOD}$displayMember${RESET}"
                    val paramsStr  = if (displayParams.isNotEmpty() || params.isEmpty()) "${LIGHT_GRAY}($displayParams)${RESET}" else ""

                    val visibleLen = displayMember.length + (if (displayParams.isNotEmpty() || params.isEmpty()) displayParams.length + 2 else 0)
                    val pad = maxOf(1, termWidth - prefixVisible - 2 - markersLen - visibleLen)

                    buf.append(prefix).append("  ")
                        .append(hookMarker).append(overrideMarker)
                        .append(nameStr).append(paramsStr)
                        .append(" ".repeat(pad))
                        .append(RESET).append("\n")
                }
                is InspectRow.InstanceRow -> {
                    // Detect destroyed state from summary heuristic
                    val isDestroyed = row.instance.summary.contains("destroyed", ignoreCase = true)
                        || row.instance.summary.contains("isDestroyed=true", ignoreCase = true)

                    val treeLine   = if (row.isLast) "└ " else "├ "
                    val idxLabel   = "inst#${row.instance.id.takeLast(4)}"
                    val hashLabel  = "@${row.instance.handle.take(8)}"
                    if (isDestroyed) {
                        buf.append(prefix)
                            .append(C_DARK_GRAY).append(treeLine).append(idxLabel)
                            .append(" · ").append(hashLabel)
                            .append(" · destroyed").append(RESET).append("\n")
                    } else {
                        buf.append(prefix)
                            .append(DIM_GRAY).append(treeLine).append(RESET)
                            .append(WHITE).append(idxLabel).append(RESET)
                            .append(DIM_GRAY).append(" · ").append(RESET)
                            .append(DIM_GRAY).append(hashLabel).append(RESET)
                            .append(DIM_GRAY).append(" · ").append(RESET)
                            .append(C_GREEN).append("active").append(RESET).append("\n")
                    }
                }
                is InspectRow.InstanceAttributeRow -> {
                    if (row.attribute.isPagination) {
                        buf.append(prefix).append(DIM_GRAY).append("··· ").append(row.attribute.value).append(RESET).append("\n")
                    } else {
                        val attrName = row.attribute.name
                        val attrType = row.attribute.type
                        val attrVal  = row.attribute.value

                        val isObjectRef = row.attribute.childId != null
                        
                        // Calculate space for value
                        val labelLen = if (isObjectRef) {
                            attrName.length + 3 + attrType.length + 6 // "name (Type)  → I"
                        } else {
                            attrName.length + 2 // "name: "
                        }
                        val maxValLen = maxOf(0, termWidth - 1 - prefixVisible - labelLen - 5)
                        val displayVal = if (attrVal.length > maxValLen) attrVal.take(maxOf(0, maxValLen - 3)) + "..." else attrVal

                        buf.append(prefix)
                        if (isObjectRef) {
                            // Object reference: purple name + (Type) in dim gray + value preview
                            buf.append(C_PURPLE).append(attrName).append(RESET)
                            buf.append(" ").append(DIM_GRAY).append("(").append(attrType).append(")").append(RESET)
                            buf.append(DIM_GRAY).append(": ").append(RESET)
                            buf.append(C_MID_GRAY).append(displayVal).append(RESET)
                            buf.append(C_ORANGE).append("  → I").append(RESET)
                        } else {
                            // Primitive / string
                            val valColor = when (attrType.lowercase()) {
                                "boolean", "bool"                                    -> TYPE_BOOLEAN
                                "int", "long", "float", "double", "short", "byte"   -> TYPE_NUMBER
                                "string", "char", "charsequence"                    -> TYPE_STRING
                                else                                                 -> TYPE_OTHER
                            }
                            val valDisplay = if (attrType.lowercase() == "string" && attrVal != "null") "\"$displayVal\"" else displayVal
                            val valColorFinal = if (attrVal == "null") Ansi.RED else valColor

                            buf.append(C_PURPLE).append(attrName).append(RESET)
                            buf.append(DIM_GRAY).append(": ").append(RESET)
                            buf.append(valColorFinal).append(valDisplay).append(RESET)
                        }
                        buf.append(RESET).append("\n")
                    }
                }
                is InspectRow.InfoRow -> {
                    val color = if (row.isError) Ansi.RED else if (row.isDim) DIM_GRAY else RESET
                    val text  = if (row.text.contains("loading", ignoreCase = true)) {
                        "${ListRenderer.spinnerFrame(state.gadgetSpinnerFrame)} ${row.text}"
                    } else {
                        row.text
                    }
                    buf.append(prefix).append(color).append(text).append(RESET).append("\n")
                }
            }
        }

        ListRenderer.renderScrollIndicator(buf, startIdx, endIdx, rows.size, termWidth)
    }

    private fun renderHookWatchMode(buf: StringBuilder, state: AppState, termWidth: Int, termHeight: Int) {
        // Header and breadcrumb are rendered by render() before this call.
        // Available height: total height minus header(2) + breadcrumb(2) + footer(1) = 5 lines overhead.
        val contentHeight = termHeight - 5

        val leftWidth  = (termWidth * 0.22).toInt().coerceAtLeast(12)
        val rightWidth = termWidth - leftWidth - 1  // 1 for separator

        // ── Column headers ──────────────────────────────────────────────────────
        val leftHeader  = " HOOKED"
        val rightHeader = " EVENT LOG"
        buf.append(C_MID_GRAY)
        buf.append(leftHeader.padEnd(leftWidth))
        buf.append("│")
        buf.append(rightHeader)
        buf.append(RESET).append("\n")
        buf.append(C_SEP)
        buf.append("─".repeat(leftWidth)).append("┼").append("─".repeat(rightWidth))
        buf.append(RESET).append("\n")

        val availableHeight = contentHeight - 2  // subtract column header rows

        // ── Build event log lines ────────────────────────────────────────────────
        // Events are displayed newest-first (bottom of list = oldest).
        val eventLines = mutableListOf<String>()
        for (event in state.hookEvents.asReversed()) {
            eventLines.addAll(formatEventBlock(event, rightWidth - 2))
        }
        // Apply scroll offset
        val logStart  = state.hookLogScrollOffset.coerceIn(0, maxOf(0, eventLines.size - availableHeight))
        val logSlice  = eventLines.drop(logStart).take(availableHeight)

        val activeHooksList = state.activeHooks.toList()

        // ── Render rows ──────────────────────────────────────────────────────────
        for (y in 0 until availableHeight) {
            // Left: HOOKED list
            if (y < activeHooksList.size) {
                val hook       = activeHooksList[y]
                val isSelected = y == state.selectedHookIndex
                val selMark    = if (isSelected) "› " else "  "
                val statusMark = if (hook.enabled) "[${Ansi.GREEN}✓${RESET}] " else "[ ] "
                val overrideMark = if (hook.implementation != null) "${C_CYAN}[C]${RESET} " else ""
                val color      = if (hook.type == HookType.METHOD) J_METHOD else C_PURPLE
                val name       = StringUtils.extractMemberName(hook.memberSignature)
                val selColor   = if (isSelected) WHITE else DIM_GRAY

                val cell = "$selColor$selMark$RESET$statusMark$overrideMark$color$name$RESET"
                val visLen = 2 + 4 + (if (hook.implementation != null) 4 else 0) + name.length // 2 (selMark) + 4 ("[✓] ") + 4 ("[C] ") + name
                buf.append(cell)
                buf.append(" ".repeat(maxOf(0, leftWidth - visLen)))
            } else {
                buf.append(" ".repeat(leftWidth))
            }

            buf.append(C_SEP).append("│").append(RESET)

            // Right: event log
            val line = logSlice.getOrNull(y) ?: ""
            buf.append(line)
            // No need to pad — newline follows
            buf.append("\n")
        }
    }

    private fun formatTime(timestamp: Long): String {
        // Simple time formatting for Kotlin Native without extra libs
        // Assuming timestamp is in ms.
        val seconds = (timestamp / 1000) % 60
        val minutes = (timestamp / (1000 * 60)) % 60
        val hours = (timestamp / (1000 * 60 * 60) + 24 - 3) % 24 // Quick hack for UTC-3, or just keep it simple
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

    /**
     * Formats a hook event into a list of display lines (no ANSI in length calculations).
     * Each element is a Pair<String, Int> = (ansi-formatted line, visible character count).
     */
    private fun formatEventBlock(event: HookEvent, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()

        val time       = formatTime(event.timestamp)
        var memberName = StringUtils.extractMemberName(event.target.memberSignature)
        if (event.target.type == HookType.METHOD) {
            val params = StringUtils.extractParams(event.target.memberSignature)
            memberName += "($params)"
        }
        val instanceId = event.data["instanceId"] ?: event.data["handle"] ?: ""
        val hashSuffix = if (instanceId.isNotEmpty()) "  ${DIM_GRAY}@${instanceId.take(7)}${RESET}" else ""
        val countSuffix = if (event.count > 1) " ${DIM_GRAY}×${event.count}${RESET}" else ""

        when (event.target.type) {
            HookType.FIELD -> {
                val value = event.data["value"] ?: ""
                val badgeColor = C_PURPLE
                val badge = "${badgeColor}FIELD${RESET}"
                val header = "${DIM_GRAY}$time${RESET}  $badge  ${WHITE}$memberName${RESET}$hashSuffix$countSuffix"
                lines.add(header)

                // old → new from value string (bridge may send "old|new" or just new)
                val parts = value.split("|")
                if (parts.size == 2) {
                    lines.add("  ${Ansi.RED}${parts[0].trim()}${RESET}  ${DIM_GRAY}→${RESET}  ${C_GREEN}${parts[1].trim()}${RESET}")
                } else {
                    lines.add("  ${C_GREEN}$value${RESET}")
                }
            }
            HookType.METHOD -> {
                val args   = event.data["args"] ?: ""
                val ret    = event.data["return"] ?: "void"
                val badgeColor = if (event.target.implementation != null) C_CYAN else J_METHOD
                val badge = if (event.target.implementation != null) "${badgeColor}OVERRIDE${RESET}" else "${badgeColor}METHOD${RESET}"
                val header = "${DIM_GRAY}$time${RESET}  $badge  ${WHITE}$memberName${RESET}$hashSuffix$countSuffix"
                lines.add(header)

                // Args
                if (args.isNotEmpty() && args != "{}") {
                    lines.add("  ${DIM_GRAY}args:${RESET}")
                    val argList = args.split(",").map { it.trim() }
                    argList.forEach { arg ->
                        val truncated = if (arg.length > maxWidth - 6) arg.take(maxOf(0, maxWidth - 9)) + "..." else arg
                        lines.add("    ${C_MID_GRAY}$truncated${RESET}")
                    }
                }

                // Return value
                if (ret != "void" && ret.isNotEmpty()) {
                    lines.add("  ${DIM_GRAY}returned:${RESET}")
                    val retLines = formatValue(ret, 4, maxWidth)
                    lines.addAll(retLines)
                }
            }
            HookType.LOG -> {
                val message = event.data["message"] ?: ""
                val badgeColor = C_MID_GRAY
                val badge = "${badgeColor}LOG${RESET}"
                val header = "${DIM_GRAY}$time${RESET}  $badge  ${WHITE}$memberName${RESET}$hashSuffix$countSuffix"
                lines.add(header)
                lines.add("  ${C_MID_GRAY}$message${RESET}")
            }
            HookType.EXEC -> {
                val isError = event.data["error"] != null
                val message = event.data["message"] ?: event.data["result"] ?: event.data["error"] ?: ""
                val badgeColor = if (isError) Ansi.RED else C_BLUE
                val badge = "${badgeColor}EXEC${RESET}"
                val header = "${DIM_GRAY}$time${RESET}  $badge  ${WHITE}$memberName${RESET}$hashSuffix$countSuffix"
                lines.add(header)
                val valColor = if (isError) Ansi.RED else C_MID_GRAY
                lines.add("  ${valColor}$message${RESET}")
            }
        }

        lines.add("") // blank separator between events
        return lines
    }

    /**
     * Formats a value string with indentation. Detects ClassName{k=v, ...} patterns
     * and renders them with one field per line. Nested objects beyond depth 1 collapse
     * as `{ ··· }` or `( ··· )`.
     */
    private fun formatValue(value: String, indent: Int, maxWidth: Int): List<String> {
        val pad = " ".repeat(indent)
        val trimmed = value.trim()
        
        val isBraces = trimmed.endsWith("}")
        val isParens = trimmed.endsWith(")")
        
        val objectPattern = if (isBraces) {
            Regex("""^(\w[\w.${'$'}]*)\{(.*)\}$""", RegexOption.DOT_MATCHES_ALL)
        } else if (isParens) {
            Regex("""^(\w[\w.${'$'}]*)\((.*)\)$""", RegexOption.DOT_MATCHES_ALL)
        } else null

        val match = objectPattern?.matchEntire(trimmed)

        if (match != null) {
            val className = match.groupValues[1]
            val body      = match.groupValues[2]
            val lines     = mutableListOf<String>()
            val openChar  = if (isBraces) "{" else "("
            val closeChar = if (isBraces) "}" else ")"
            
            lines.add("$pad${C_BLUE}$className${RESET}${DIM_GRAY}$openChar${RESET}")

            // Split fields by ", " but be careful with nested braces
            val fields = StringUtils.splitTopLevelCommas(body)
            fields.forEach { field ->
                val eqIdx = field.indexOf('=')
                if (eqIdx != -1) {
                    val k = field.substring(0, eqIdx).trim()
                    val v = field.substring(eqIdx + 1).trim()
                    // Collapse any nested objects
                    val vDisplay = if (v.contains('{') || v.contains('(')) {
                        val firstParen = v.indexOf('(')
                        val firstBrace = v.indexOf('{')
                        val firstOpen = listOf(firstParen, firstBrace).filter { it != -1 }.minOrNull() ?: -1
                        if (firstOpen != -1) {
                            val openBracket = v[firstOpen]
                            val closeBracket = if (openBracket == '{') "}" else ")"
                            "${v.substring(0, firstOpen).trim()}${DIM_GRAY}$openBracket ··· $closeBracket${RESET}"
                        } else v
                    } else v
                    val truncated = if (vDisplay.length > maxWidth - indent - k.length - 4) vDisplay.take(maxOf(0, maxWidth - indent - k.length - 7)) + "..." else vDisplay
                    lines.add("$pad  ${C_PURPLE}$k${RESET}${DIM_GRAY}: ${RESET}$truncated")
                } else {
                    val truncatedField = if (field.length > maxWidth - indent - 2) field.take(maxOf(0, maxWidth - indent - 5)) + "..." else field
                    lines.add("$pad  ${C_MID_GRAY}${truncatedField}${RESET}")
                }
            }
            lines.add("$pad${DIM_GRAY}$closeChar${RESET}")
            return lines
        }

        // Primitive / string
        val truncated = if (value.length > maxWidth - indent) value.take(maxOf(0, maxWidth - indent - 3)) + "..." else value
        return listOf("$pad${C_MID_GRAY}$truncated${RESET}")
    }

    private fun highlightJavaSignature(signature: String): String {
        val modifiers = setOf("public", "private", "protected", "static", "final", "native", "synchronized", "transient", "volatile", "abstract")
        val primitives = setOf("int", "long", "boolean", "byte", "short", "float", "double", "void", "char")
        
        val sb = StringBuilder()
        val tokens = signature.split(" ", "(", ")", ",").filter { it.isNotEmpty() }
        
        // This is a simple tokenizer/highlighter. For a full TUI we might want something more robust.
        var currentIdx = 0
        val words = signature.split(Regex("(?<=[\\s(),])|(?=[\\s(),])"))
        
        for (word in words) {
            when {
                word.trim() in modifiers -> sb.append(J_MODIFIER).append(word)
                word.trim() in primitives -> sb.append(J_TYPE).append(word)
                word.trim().isEmpty() -> sb.append(word)
                word == "(" || word == ")" || word == "," -> sb.append(WHITE).append(word)
                else -> {
                    // Check if it's a qualified name
                    if (word.contains('.')) {
                        val lastDot = word.lastIndexOf('.')
                        val pkg = word.substring(0, lastDot + 1)
                        val name = word.substring(lastDot + 1)
                        sb.append(J_PACKAGE).append(pkg).append(J_CLASS).append(name)
                    } else {
                        // Probably a method name or something else
                        if (word.any { it.isDigit() }) {
                           sb.append(J_NUMBER).append(word)
                        } else {
                           sb.append(J_METHOD).append(word)
                        }
                    }
                }
            }
        }
        sb.append(RESET)
        return sb.toString()
    }
}
