package rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RpcRequest(val jsonrpc: String, val method: String, val params: JsonElement? = null, val id: Int? = null)

@Serializable
data class RpcResponse(val jsonrpc: String = "2.0", val result: JsonElement? = null, val id: Int? = null)

@Serializable
data class RpcError(val status: String, val error_message: String)

@Serializable
data class RpcErrorResponse(val jsonrpc: String = "2.0", val error: RpcError, val id: Int? = null)

@Serializable data class ListClassesParams(val search_param: String = "", val app_package: String = "", val offset: Int = 0, val limit: Int = 200)
@Serializable data class CountInstancesParams(val className: String)

@Serializable data class InspectClassParams(val className: String)
@Serializable data class ListInstancesParams(val className: String)
@Serializable data class InspectInstanceParams(val className: String, val id: String, val offset: Int = 0, val limit: Int = 50)

@Serializable data class ClassInspectionResult(val staticAttributes: List<String>, val instanceAttributes: List<String>, val methods: List<String>)

@Serializable data class InstanceInfo(val id: String, val handle: String, val summary: String, val detectionMethod: String = "heap_scan")
@Serializable data class ListInstancesResult(val instances: List<InstanceInfo>, val totalCount: Int, val detectionMethod: String = "heap_scan")

@Serializable data class InstanceAttribute(val name: String, val type: String, val value: String, val childId: String? = null, val childClassName: String? = null, val isPagination: Boolean = false, val nextOffset: Int = 0)
@Serializable data class InspectInstanceResult(val attributes: List<InstanceAttribute>)

@Serializable data class SetFieldValueParams(val className: String, val id: String, val fieldName: String, val type: String, val newValue: String)
@Serializable data class HookParams(val className: String, val methodSig: String)
@Serializable data class HookEvent(val className: String, val methodSig: String, val eventData: String = "")
@Serializable data class SetMethodImplementationParams(val className: String, val methodSig: String, val code: String)
@Serializable data class RunOnceParams(val className: String, val methodSig: String, val code: String)
@Serializable data class GetInstanceAddressesParams(val className: String)