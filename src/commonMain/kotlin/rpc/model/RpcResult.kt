package rpc.model

import kotlinx.serialization.Serializable

@Serializable
data class PrepareEnvResult(
    val pid: Int,
    val package_name: String,
    val port: Int,
    val target: String
)

@Serializable
data class InjectionStep(val id: String, val title: String, val status: String)

@Serializable
data class InjectionProgressResult(
    val status: String,
    val steps: List<InjectionStep>,
    val logs: List<String>? = null,
    val error_message: String? = null
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
data class InspectInstanceResult(val attributes: List<InstanceAttribute>)

@Serializable
data class InstanceInfo(
    val id: String,
    val handle: String,
    val summary: String,
    val detectionMethod: String = "heap_scan"
)

@Serializable
data class ListInstancesResult(
    val instances: List<InstanceInfo>,
    val totalCount: Int,
    val detectionMethod: String = "heap_scan"
)

@Serializable
data class ClassInspectionResult(
    val staticAttributes: List<String>,
    val instanceAttributes: List<String>,
    val methods: List<String>
)

@Serializable
data class GenericStatusResult(val status: String, val error_message: String? = null)

@Serializable
data class CheckResponse(
    val status: String,
    val message: String,
    val fix: String? = null,
    val `package`: String? = null,
    val pid: Int? = null,
    val debuggable: Boolean? = null
)

@Serializable
data class HealthCheckResult(
    val overall: String,
    val checks: Map<String, CheckResponse>,
    val recommendation: String? = null
)

@Serializable
data class HookEvent(val className: String, val methodSig: String, val eventData: String = "")