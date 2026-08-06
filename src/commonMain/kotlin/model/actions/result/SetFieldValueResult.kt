package model.actions.result

import kotlinx.serialization.Serializable

@Serializable
data class SetFieldValueResult(
    val status: String
): ActionResult()