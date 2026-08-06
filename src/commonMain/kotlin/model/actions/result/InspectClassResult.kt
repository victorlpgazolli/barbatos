package model.actions.result

import kotlinx.serialization.Serializable
import model.actions.params.ActionParam

@Serializable
data class InspectClassResult(
    val staticAttributes: List<String>,
    val instanceAttributes: List<String>,
    val methods: List<String>
): ActionResult()