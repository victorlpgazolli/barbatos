package model.actions

import kotlinx.schema.generator.json.jsonSchemaOf
import kotlinx.schema.json.JsonSchema
import kotlinx.schema.json.jsonSchema
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import model.actions.params.ActionParam

@Serializable
data class ActionDescriptor(
    val name: String,
    val description: String,
    @SerialName("inputSchema")
    val scheme: JsonSchema = jsonSchema { },
) {
    companion object {
        inline fun <reified Param : ActionParam> create(
            name: String,
            description: String,
        ): ActionDescriptor = ActionDescriptor(
            name = name,
            description = description,
            scheme = jsonSchemaOf<Param>()
        )
        fun create(
            name: String,
            description: String,
        ): ActionDescriptor = ActionDescriptor(
            name = name,
            description = description,
            scheme = jsonSchema { }
        )
    }
}