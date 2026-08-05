package model.actions.result

import kotlinx.serialization.Serializable

@Serializable
data class HookEventsResult(
    val eventData: String = ""
): ActionResult()


