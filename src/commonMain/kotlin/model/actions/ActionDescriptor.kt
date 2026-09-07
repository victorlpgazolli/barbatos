package model.actions

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.schema.generator.json.jsonSchemaOf
import kotlinx.schema.json.JsonSchema
import kotlinx.schema.json.encodeToJsonObject
import kotlinx.schema.json.jsonSchema
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import model.actions.params.ActionParam

@Serializable
data class ActionDescriptor(
    val name: String,
    val description: String,
    @SerialName("inputSchema")
    val scheme: JsonSchema = jsonSchema { },
    val isStreamingOutput: Boolean = false,
) {
    val mcpScheme: ToolSchema
        get() {
            val encoded = scheme.encodeToJsonObject()
            return ToolSchema(
                properties = encoded["properties"] as? JsonObject,
                required = (encoded["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                defs = encoded["\$defs"] as? JsonObject,
            )
        }
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