package model.actions.result

import kotlinx.serialization.Serializable

@Serializable
data class InspectInstanceResult(
    val attributes: List<InstanceAttribute>
): ActionResult()

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