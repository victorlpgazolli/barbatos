package model.actions.params

import kotlinx.serialization.Serializable

@Serializable
data class CountInstancesParams(
    val className: String,
): ActionParam()