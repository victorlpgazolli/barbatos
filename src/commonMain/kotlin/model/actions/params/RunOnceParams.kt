package model.actions.params

import kotlinx.serialization.Serializable

@Serializable
data class RunOnceParams(
    val className: String,
    val methodSig: String,
    val code: String,
): ActionParam()