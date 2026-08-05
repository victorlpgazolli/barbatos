package model.actions.params

import kotlinx.serialization.Serializable

@Serializable
data class InspectInstanceParams(
    val className: String,
    val id: String,
    val offset: Int = 0,
    val limit: Int = 50
): ActionParam()