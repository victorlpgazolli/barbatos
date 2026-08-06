package model.actions.params

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PrepareEnvParams(
    val pid: Int? = null,
    @SerialName("package_name")
    val packageName: String,
    val target: String? = null,
): ActionParam()