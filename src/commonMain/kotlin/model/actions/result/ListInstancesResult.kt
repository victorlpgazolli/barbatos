package model.actions.result

import kotlinx.serialization.Serializable

@Serializable
data class ListInstancesResult(
    val instances: List<InstanceInfo>,
    val totalCount: Int,
    val detectionMethod: String = "heap_scan"
): ActionResult()

@Serializable
data class InstanceInfo(
    val id: String,
    val handle: String,
    val summary: String,
    val detectionMethod: String = "heap_scan"
)
