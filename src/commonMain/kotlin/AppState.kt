import kotlin.concurrent.AtomicReference
import kotlinx.serialization.Serializable

enum class AppMode {
    DEFAULT,
    DEBUG_ENTRYPOINT,
    DEBUG_CLASS_FILTER,
    DEBUG_INSPECT_CLASS,
    DEBUG_HOOK_WATCH,
    DEBUG_EDIT_ATTRIBUTE
}

enum class GadgetInstallStatus {
    IDLE,
    WAITING_BRIDGE_SETUP,
    SUCCESS,
    ERROR
}

data class Command(val name: String, val description: String)

@Serializable
data class ClassInspectionResult(
    val staticAttributes: List<String>,
    val instanceAttributes: List<String>,
    val methods: List<String>
)

@Serializable
enum class HookType { METHOD, FIELD }

@Serializable
data class HookTarget(
    val className: String,
    val memberSignature: String,
    val type: HookType,
    val enabled: Boolean = true
)

@Serializable
data class HookEvent(
    val timestamp: Long,
    val target: HookTarget,
    val data: Map<String, String>,
    var count: Int = 1
)

data class ClassAnimationState(
    val oldText: String,
    val newText: String,
    val startTime: Long
) {
    fun getProgress(now: Long): Double = 
        ((now - startTime).toDouble() / 500.0).coerceIn(0.0, 1.0)
    
    fun isFinished(now: Long): Boolean = (now - startTime) >= 500
}

sealed class InspectRow {
    data class SectionStaticRow(val isExpanded: Boolean) : InspectRow()
    data class StaticAttributeRow(val attribute: String) : InspectRow()
    data class StaticMethodRow(val method: String) : InspectRow()
    data class SectionInstancesRow(val isExpanded: Boolean) : InspectRow()
    data class InstanceRow(val instance: InstanceInfo, val isExpanded: Boolean, val isLast: Boolean = false) : InspectRow()
    data class InstanceAttributeRow(val instanceId: String, val attribute: InstanceAttribute, val isExpanded: Boolean = false, val treeFlags: List<Boolean> = emptyList()) : InspectRow()
    data class InfoRow(val text: String, val treeFlags: List<Boolean>, val isError: Boolean = false, val isDim: Boolean = true) : InspectRow()
}

data class AppState(
    var adbSerial: String? = null,
    var inputBuffer: String = "",
    var cursorPosition: Int = 0,
    var suggestions: List<Command> = emptyList(),
    var selectedSuggestionIndex: Int = -1,
    var commandHistory: MutableList<String> = mutableListOf(),
    var historyNavigationIndex: Int = -1,
    var savedInputBeforeHistory: String = "",
    var ctrlCPressed: Boolean = false,
    var ctrlCTimestamp: Long = 0L,
    var running: Boolean = true,
    
    // Class Filter Mode additions
    var mode: AppMode = AppMode.DEFAULT,
    var debugEntrypointIndex: Int = 0,
    var appPackageName: String = "",
    val sharedAppPackageName: AtomicReference<String?> = AtomicReference(null),
    
    var lastInputTimestamp: Long = 0L,
    var lastSearchedParam: String = "",
    val sharedFetchedClasses: AtomicReference<List<String>?> = AtomicReference(null),
    val sharedRpcError: AtomicReference<String?> = AtomicReference(null),
    var allFetchedClasses: List<String> = emptyList(),
    var displayedClasses: List<String> = emptyList(),
    var selectedClassIndex: Int = -1,
    var isFetchingClasses: Boolean = false,
    var showSyntheticClasses: Boolean = false,
    var rpcError: String? = null,
    
    var instanceCounts: MutableMap<String, Int> = mutableMapOf(),
    var isFetchingInstances: Boolean = false,
    val sharedInstanceCountResult: AtomicReference<Pair<String, Int>?> = AtomicReference(null),
    val sharedInstanceCountError: AtomicReference<String?> = AtomicReference(null),
    
    // Inspect Mode additions
    var inspectTargetClassName: String = "",
    val sharedInspectResult: AtomicReference<ClassInspectionResult?> = AtomicReference(null),
    var inspectStaticAttributes: List<String> = emptyList(),
    var inspectInstanceAttributes: List<String> = emptyList(),
    var inspectMethods: List<String> = emptyList(),
    var isFetchingInspection: Boolean = false,

    var bridgeLogs: List<String> = emptyList(),
    val sharedBridgeLogs: AtomicReference<List<String>?> = AtomicReference(null),

    var inspectStaticExpanded: Boolean = false,
    var inspectInstancesExpanded: Boolean = false,
    var inspectInstancesList: List<InstanceInfo>? = null,
    var inspectInstancesTotalCount: Int = 0,
    var instancesDetectionMethod: String = "heap_scan",
    var isFetchingInstancesList: Boolean = false,
    val sharedInstancesListResult: AtomicReference<ListInstancesResult?> = AtomicReference(null),
    var inspectExpandedInstances: MutableMap<String, List<InstanceAttribute>?> = mutableMapOf(),
    var inspectExpandedInstancesError: MutableMap<String, String> = mutableMapOf(),
    val sharedInspectInstanceResult: AtomicReference<Triple<String, List<InstanceAttribute>, Boolean>?> = AtomicReference(null),
    val sharedInspectInstanceError: AtomicReference<Pair<String, String>?> = AtomicReference(null),


    // Gadget Install Status
    var gadgetInstallStatus: GadgetInstallStatus = GadgetInstallStatus.IDLE,
    var gadgetErrorMessage: String? = null,
    var gadgetInjectionSteps: List<InjectionStep> = emptyList(),
    val sharedGadgetResult: AtomicReference<Pair<GadgetInstallStatus, String?>?> = AtomicReference(null),
    val sharedGadgetSteps: AtomicReference<List<InjectionStep>?> = AtomicReference(null),
    var gadgetSpinnerFrame: Int = 0,
    var isSubPane: Boolean = false,
    var startedAsInspectPane: Boolean = false,
    var inspectBackStack: MutableList<String> = mutableListOf(),
    var hooksLoaded: Boolean = false,
    var activeHooks: MutableList<HookTarget> = mutableListOf(),
    var hookEvents: MutableList<HookEvent> = mutableListOf(),
    val sharedHookEvents: AtomicReference<List<HookEvent>?> = AtomicReference(null),
    var selectedHookIndex: Int = 0,
    var hookLogScrollOffset: Int = 0,

    var editingInstanceId: String = "",
    var editingAttribute: InstanceAttribute? = null,
    var navigationStack: MutableList<AppMode> = mutableListOf(),
    var classAnimations: MutableMap<Int, ClassAnimationState> = mutableMapOf(),
    var experimentalListScramble: Boolean = false
) {
    fun pushMode(newMode: AppMode, force: Boolean = false) {
        if (force || mode != newMode) {
            navigationStack.add(mode)
            mode = newMode
        }
    }

    fun popMode(): AppMode? {
        if (navigationStack.isNotEmpty()) {
            val prev = navigationStack.removeAt(navigationStack.size - 1)
            mode = prev
            return mode
        }
        return null
    }

    fun addHookEvent(event: HookEvent) {
        val last = hookEvents.lastOrNull()
        if (last != null && last.target == event.target && event.timestamp - last.timestamp < 500 && last.data == event.data) {
            last.count++
        } else {
            hookEvents.add(event)
            if (hookEvents.size > 100) {
                hookEvents.removeAt(0)
            }
        }
    }

    fun buildInspectRows(): List<InspectRow> {
        val rows = mutableListOf<InspectRow>()
        
        rows.add(InspectRow.SectionStaticRow(inspectStaticExpanded))
        if (inspectStaticExpanded) {
            inspectStaticAttributes.forEach { rows.add(InspectRow.StaticAttributeRow(it)) }
            inspectMethods.forEach { rows.add(InspectRow.StaticMethodRow(it)) }
        }

        rows.add(InspectRow.SectionInstancesRow(inspectInstancesExpanded))
        if (inspectInstancesExpanded) {
            if (isFetchingInstancesList) {
                rows.add(InspectRow.InfoRow("Fetching instances...", emptyList()))
            }
            val list = inspectInstancesList
            if (list != null) {
                list.forEachIndexed { index, instance ->
                    val isExpanded = inspectExpandedInstances.containsKey(instance.id)
                    val isLast = index == list.lastIndex
                    rows.add(InspectRow.InstanceRow(instance, isExpanded, isLast))
                    if (isExpanded) {
                        val errorStr = inspectExpandedInstancesError[instance.id]
                        val childFlags = listOf(isLast)
                        if (errorStr != null) {
                            rows.add(InspectRow.InfoRow("Error loading attributes: $errorStr", childFlags, isError = true))
                        } else if (inspectExpandedInstances[instance.id] == null) {
                            rows.add(InspectRow.InfoRow("Loading attributes...", childFlags))
                        } else {
                            rows.add(InspectRow.InfoRow("Attributes of this instance: (press R to refresh)", childFlags, isDim = true))
                            appendAttributesRecursively(instance.id, childFlags, rows, setOf(instance.id))
                        }
                    }
                }
            }
        }
        return rows
    }

    private fun appendAttributesRecursively(nodeId: String, ancestorIsLast: List<Boolean>, rows: MutableList<InspectRow>, visited: Set<String>) {
        val attrs = inspectExpandedInstances[nodeId]
        if (attrs != null) {
            attrs.forEachIndexed { index, attr ->
                val isLastChild = index == attrs.lastIndex
                val canExpand = attr.childId != null
                val isCycle = canExpand && attr.childId!! in visited
                val isExpanded = canExpand && !isCycle && inspectExpandedInstances.containsKey(attr.childId!!)
                
                val treeFlags = ancestorIsLast + isLastChild
                val actualExpanded = if (isCycle) false else isExpanded

                rows.add(InspectRow.InstanceAttributeRow(nodeId, attr, actualExpanded, treeFlags))
                if (actualExpanded) {
                    val errorStr = inspectExpandedInstancesError[attr.childId!!]
                    if (errorStr != null) {
                        rows.add(InspectRow.InfoRow("Error loading attributes: $errorStr", treeFlags, isError = true))
                    } else if (inspectExpandedInstances[attr.childId!!] == null) {
                        rows.add(InspectRow.InfoRow("Loading attributes...", treeFlags))
                    } else {
                        rows.add(InspectRow.InfoRow("Attributes of this instance: (press R to refresh)", treeFlags, isDim = true))
                        appendAttributesRecursively(attr.childId!!, treeFlags, rows, visited + attr.childId!!)
                    }
                }
            }
        }
    }
}
