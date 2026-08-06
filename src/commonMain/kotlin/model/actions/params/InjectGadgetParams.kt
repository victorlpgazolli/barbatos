package model.actions.params

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InjectGadgetParams(
    @SerialName("with_logs")
    val withLogs: Boolean = true,
    val limit: Int = 100,
    val serial: String? = null,
): ActionParam()