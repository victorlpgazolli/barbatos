package model.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import model.actions.params.*
import model.actions.result.*

interface FridaBridge {
    val jsonParser: Json
    val fridaCoroutineScope: CoroutineScope
    // Methods will be added here as we migrate endpoints
    fun listClassesStream(params: ListClassesParams, onChunk: suspend (partialResult: ListClassesPartialResult) -> Unit, onComplete: () -> Unit)
    fun pingJava(): String
    fun testRpc(): String
    fun countInstances(params: CountInstancesParams): CountInstancesResult
    fun inspectClass(params: InspectClassParams): InspectClassResult
    fun listInstances(params: ListInstancesParams): ListInstancesResult
    fun inspectInstance(params: InspectInstanceParams): InspectInstanceResult

    fun setFieldValue(params: SetFieldValueParams): SetFieldValueResult
    fun hookMethod(params: HookParams): HookMethodResult
    fun getHookEvents(): HookEventsResult
    fun setMethodImplementation(params: SetMethodImplementationParams): SetMethodImplementationResult
    fun runOnce(params: RunOnceParams): RunOnceResult
    fun getInstanceAddresses(params: GetInstanceAddressesParams): GetInstanceAddressesResult

    fun prepareEnvironment(params: PrepareEnvParams): PrepareEnvResult
    fun injectGadgetFromScratch(params: InjectGadgetParams): InjectGadgetResult
    fun injectJdwp(params: InjectJdwpParams): InjectJdwpResult
    fun healthCheck(): HealthCheckResult
    fun close(): Unit
}