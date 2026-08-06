package model.actions.params

import kotlinx.serialization.Serializable

@Serializable
data class GetInstanceAddressesParams(val className: String): ActionParam()