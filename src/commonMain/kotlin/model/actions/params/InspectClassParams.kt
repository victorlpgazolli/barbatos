package model.actions.params

import kotlinx.serialization.Serializable

@Serializable
data class InspectClassParams(
    val className: String,
): ActionParam()