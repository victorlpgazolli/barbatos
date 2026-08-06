package model.actions.params

import kotlinx.serialization.Serializable


@Serializable
data class SetMethodImplementationParams(
    val className: String,
    val methodSig: String,
    val code: String
): ActionParam()
