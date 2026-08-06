package model.actions.result

import kotlinx.serialization.Serializable
import model.actions.params.ActionParam


@Serializable
data class SetMethodImplementationResult(
    val status: String,
): ActionResult()
