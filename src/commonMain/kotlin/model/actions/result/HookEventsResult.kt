package model.actions.result

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HookEventsResult(
    val events: List<HookEvent> = emptyList()
): ActionResult()

@Serializable
data class HookEvent(
    val timestamp: Long = 0,
    val target: HookEventTarget? = null,
    val data: JsonElement? = null
)

@Serializable
data class HookEventTarget(
    val className: String = "",
    val memberSignature: String = "",
    val type: String = ""
)
