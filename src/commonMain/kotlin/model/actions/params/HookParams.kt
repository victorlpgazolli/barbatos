package model.actions.params

import kotlinx.serialization.Serializable

@Serializable
data class HookParams(
    val className: String,
    val methodSig: String
): ActionParam()