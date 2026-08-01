package model.rpc

import kotlinx.serialization.Serializable

@Serializable
data class ListClassesParams(
    val search_param: String = "",
    val app_package: String = "",
    val offset: Int = 0,
    val limit: Int = 200
): RpcParams()

@Serializable
data class HookParams(val className: String, val methodSig: String): RpcParams()

@Serializable
data class SetMethodImplementationParams(
    val className: String,
    val methodSig: String,
    val code: String
): RpcParams()

@Serializable
data class RunOnceParams(val className: String, val methodSig: String, val code: String): RpcParams()
@Serializable
data class GetInstanceAddressesParams(val className: String): RpcParams()

@Serializable
data class PrepareEnvParams(val target: String): RpcParams()
@Serializable
data class InjectGadgetParams(val with_logs: Boolean = true, val limit: Int = 100): RpcParams()
@Serializable
data class InjectJdwpParams(val target: String, val port: Int, val package_name: String): RpcParams()

@Serializable
data class CountInstancesParams(val className: String): RpcParams()

@Serializable
data class InspectClassParams(val className: String): RpcParams()

@Serializable
data class ListInstancesParams(val className: String): RpcParams()

@Serializable
data class InspectInstanceParams(
    val className: String,
    val id: String,
    val offset: Int = 0,
    val limit: Int = 50
): RpcParams()


@Serializable
data class PatchAndInstallIosAppParams(val appPath: String): RpcParams()
@Serializable
data class IosJailbreakParams(val serial: String): RpcParams()

@Serializable
data class SetFieldValueParams(
    val className: String,
    val id: String,
    val fieldName: String,
    val type: String,
    val newValue: String
): RpcParams()
