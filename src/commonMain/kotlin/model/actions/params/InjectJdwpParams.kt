package model.actions.params

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InjectJdwpParams(
    val target: String,
    val port: Int,
    @SerialName("package_name")
    val packageName: String
): ActionParam()