package model.actions.params

import kotlinx.serialization.Serializable

@Serializable
data class InjectGadgetParams(
    val serial: String? = null,
): ActionParam()