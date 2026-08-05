package utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import model.actions.params.ActionParam

internal inline fun <reified T: ActionParam>decodeToOrThrow(params: JsonElement?): T =
    decodeToOrNull(params) ?: throw Exception("Missing params")

private val jsonParser = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal inline fun <reified T: ActionParam>decodeToOrNull(params: JsonElement?): T? =
    params?.let { jsonParser.decodeFromJsonElement<T>(it) }
