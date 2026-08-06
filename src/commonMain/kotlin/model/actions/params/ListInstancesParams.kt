package model.actions.params

import kotlinx.serialization.Serializable

@Serializable
data class ListInstancesParams(
    val className: String,
): ActionParam()