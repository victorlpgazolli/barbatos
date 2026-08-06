package bridge

import model.bridge.FridaBridge
object FridaRpcManager {
    var reqCounter = 0
    val pendingResponses = mutableMapOf<String, String?>()
    val pendingErrors = mutableMapOf<String, String>()

    var onChunkReceived: ((List<String>) -> Unit)? = null
    var isStreamCompleted = false

    fun generateReqId(): String = "req-${reqCounter++}"

    fun clear() {
        pendingResponses.clear()
        pendingErrors.clear()
        onChunkReceived = null
        isStreamCompleted = false
    }
}
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class NativeFridaBridge() : FridaBridge, AutoCloseable