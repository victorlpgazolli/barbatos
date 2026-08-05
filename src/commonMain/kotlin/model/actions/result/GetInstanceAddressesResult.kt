package model.actions.result

import kotlinx.serialization.Serializable
import model.actions.params.ActionParam

@Serializable
data class GetInstanceAddressesResult(
    val addresses: List<String>
): ActionResult()