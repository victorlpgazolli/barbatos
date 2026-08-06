package model.actions.result

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import model.actions.params.ActionParam

@Serializable
data class ListClassesPartialResult(
    val list: List<String>
): ActionResult()