package model.actions.result

import kotlinx.serialization.Serializable

@Serializable
data class HookMethodResult(
    val status: String,
)