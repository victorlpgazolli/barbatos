package ext

import bridge.FridaBridge
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.coroutines.resume

fun FridaBridge.collectListClasses(
    searchParam: String,
    appPackage: String,
    offset: Int,
    limit: Int,
    onChunk: suspend (JsonElement) -> Unit
) = fridaCoroutineScope.launch {
    suspendCancellableCoroutine { cont ->
        listClassesStream(
            searchParam = searchParam,
            appPackage = appPackage,
            offset = offset,
            limit = limit,
            onChunk = { chunk ->
                runBlocking { onChunk(jsonParser.encodeToJsonElement(chunk)) }
            },
            onComplete = {
                if (cont.isActive) cont.resume(Unit)
            }
        )
    }
}
