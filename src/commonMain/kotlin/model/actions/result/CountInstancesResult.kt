package model.actions.result

import kotlinx.serialization.Serializable

@Serializable
data class CountInstancesResult(
    val count: Int,
): ActionResult()