package bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import model.actions.params.CountInstancesParams
import model.actions.params.GetInstanceAddressesParams
import model.actions.params.HookParams
import model.actions.params.InjectGadgetParams
import model.actions.params.InjectJdwpParams
import model.actions.params.InspectClassParams
import model.actions.params.InspectInstanceParams
import model.actions.params.ListClassesParams
import model.actions.params.ListInstancesParams
import model.actions.params.PrepareEnvParams
import model.actions.params.RunOnceParams
import model.actions.params.SetFieldValueParams
import model.actions.params.SetMethodImplementationParams
import model.actions.result.CountInstancesResult
import model.actions.result.GetInstanceAddressesResult
import model.actions.result.HealthCheckResult
import model.actions.result.HookEventsResult
import model.actions.result.HookMethodResult
import model.actions.result.InjectGadgetResult
import model.actions.result.InjectJdwpResult
import model.actions.result.InspectClassResult
import model.actions.result.InspectInstanceResult
import model.actions.result.ListClassesPartialResult
import model.actions.result.ListInstancesResult
import model.actions.result.PrepareEnvResult
import model.actions.result.RunOnceResult
import model.actions.result.SetFieldValueResult
import model.actions.result.SetMethodImplementationResult
import model.bridge.FridaBridge

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class NativeFridaBridge : FridaBridge, AutoCloseable {
    override val jsonParser: Json
        get() = TODO("Not yet implemented")
    override val fridaCoroutineScope: CoroutineScope
        get() = TODO("Not yet implemented")

    override fun listClassesStream(
        params: ListClassesParams,
        onChunk: suspend (partialResult: ListClassesPartialResult) -> Unit,
        onComplete: () -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun pingJava(): String {
        TODO("Not yet implemented")
    }

    override fun testRpc(): String {
        TODO("Not yet implemented")
    }

    override fun countInstances(params: CountInstancesParams): CountInstancesResult {
        TODO("Not yet implemented")
    }

    override fun inspectClass(params: InspectClassParams): InspectClassResult {
        TODO("Not yet implemented")
    }

    override fun listInstances(params: ListInstancesParams): ListInstancesResult {
        TODO("Not yet implemented")
    }

    override fun inspectInstance(params: InspectInstanceParams): InspectInstanceResult {
        TODO("Not yet implemented")
    }

    override fun setFieldValue(params: SetFieldValueParams): SetFieldValueResult {
        TODO("Not yet implemented")
    }

    override fun hookMethod(params: HookParams): HookMethodResult {
        TODO("Not yet implemented")
    }

    override fun getHookEvents(): HookEventsResult {
        TODO("Not yet implemented")
    }

    override fun setMethodImplementation(params: SetMethodImplementationParams): SetMethodImplementationResult {
        TODO("Not yet implemented")
    }

    override fun runOnce(params: RunOnceParams): RunOnceResult {
        TODO("Not yet implemented")
    }

    override fun getInstanceAddresses(params: GetInstanceAddressesParams): GetInstanceAddressesResult {
        TODO("Not yet implemented")
    }

    override fun prepareEnvironment(params: PrepareEnvParams): PrepareEnvResult {
        TODO("Not yet implemented")
    }

    override fun injectGadgetFromScratch(params: InjectGadgetParams): InjectGadgetResult {
        TODO("Not yet implemented")
    }

    override fun injectJdwp(params: InjectJdwpParams): InjectJdwpResult {
        TODO("Not yet implemented")
    }

    override fun healthCheck(): HealthCheckResult {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}