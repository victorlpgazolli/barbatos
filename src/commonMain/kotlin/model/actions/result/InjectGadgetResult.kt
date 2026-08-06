package model.actions.result

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InjectGadgetResult(
    val status: String,
    val steps: List<InjectionStep>,
    val logs: List<String>? = null,
    @SerialName("error_message")
    val errorMessage: String? = null,
): ActionResult()

@Serializable
data class InjectionStep(
    val id: String,
    val title: String,
    val status: String,
)