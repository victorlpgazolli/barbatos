package model.actions.params

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ListClassesParams(
    @SerialName("search_param")
    val searchParam: String = "",
    @SerialName("app_package")
    val appPackage: String = "",
): ActionParam()