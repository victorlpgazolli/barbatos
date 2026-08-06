package model.actions.params

import kotlinx.serialization.Serializable

@Serializable
data class SetFieldValueParams(
    val className: String,
    val id: String,
    val fieldName: String,
    val type: String,
    val newValue: String
): ActionParam()