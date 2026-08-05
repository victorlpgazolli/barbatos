package model.actions.result

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import model.actions.params.ActionParam

@Serializable
data class PrepareEnvResult(
   val status: String
): ActionResult()