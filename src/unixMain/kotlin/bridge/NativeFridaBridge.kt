package bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import model.actions.params.*
import model.actions.result.*
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
expect class NativeFridaBridge() : FridaBridge, AutoCloseable {
    override val jsonParser: Json
    override val fridaCoroutineScope: CoroutineScope
    override fun listClassesStream(params: ListClassesParams, onChunk: suspend (partialResult: ListClassesPartialResult) -> Unit, onComplete: () -> Unit)
    override fun pingJava(): String
    override fun testRpc(): String
    override fun countInstances(params: CountInstancesParams): CountInstancesResult
    override fun inspectClass(params: InspectClassParams): InspectClassResult
    override fun listInstances(params: ListInstancesParams): ListInstancesResult
    override fun inspectInstance(params: InspectInstanceParams): InspectInstanceResult
    override fun setFieldValue(params: SetFieldValueParams): SetFieldValueResult
    override fun hookMethod(params: HookParams): HookMethodResult
    override fun getHookEvents(): HookEventsResult
    override fun setMethodImplementation(params: SetMethodImplementationParams): SetMethodImplementationResult
    override fun runOnce(params: RunOnceParams): RunOnceResult
    override fun getInstanceAddresses(params: GetInstanceAddressesParams): GetInstanceAddressesResult
    override fun prepareEnvironment(params: PrepareEnvParams): PrepareEnvResult
    override fun injectGadgetFromScratch(params: InjectGadgetParams): InjectGadgetResult
    override fun injectJdwp(params: InjectJdwpParams): InjectJdwpResult
    override fun healthCheck(): HealthCheckResult
    override fun close(): Unit
}