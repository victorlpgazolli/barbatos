package model.actions.result

import kotlinx.serialization.Serializable

@Serializable
data class HealthCheckResult(
    val overall: String,
    val checks: Map<String, CheckResponse>,
    val recommendation: String? = null
): ActionResult()

@Serializable
data class CheckResponse(
    val status: String,
    val message: String,
    val fix: String? = null,
    val `package`: String? = null,
    val pid: Int? = null,
    val debuggable: Boolean? = null
)
