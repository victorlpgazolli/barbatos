package model.actions.params

import kotlinx.serialization.Serializable

@Serializable
data class InspectInstanceParams(
    val className: String,
    val id: String,
): ActionParam()