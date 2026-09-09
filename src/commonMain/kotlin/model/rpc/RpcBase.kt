package model.rpc

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(val code: Int, val message: String)

@Serializable
data class ApiErrorResponse(val error: ApiError)