package bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
import model.actions.result.CheckResponse
import model.actions.result.CountInstancesResult
import model.actions.result.GetInstanceAddressesResult
import model.actions.result.HealthCheckResult
import model.actions.result.HookEventsResult
import model.actions.result.HookMethodResult
import model.actions.result.InjectGadgetResult
import model.actions.result.InjectJdwpResult
import model.actions.result.InjectionStep
import model.actions.result.InspectClassResult
import model.actions.result.InspectInstanceResult
import model.actions.result.InstanceAttribute
import model.actions.result.InstanceInfo
import model.actions.result.ListClassesPartialResult
import model.actions.result.ListInstancesResult
import model.actions.result.PrepareEnvResult
import model.actions.result.RunOnceResult
import model.actions.result.SetFieldValueResult
import model.actions.result.SetMethodImplementationResult
import model.bridge.FridaBridge

/**
 * Fully configurable FridaBridge for unit tests.
 *
 * Each method is backed by a mutable lambda. Override any lambda in the
 * constructor to control return values or simulate exceptions:
 *
 *   val bridge = FakeFridaBridge(
 *       pingJavaFn = { throw RuntimeException("bridge offline") }
 *   )
 *
 * Defaults mirror the behaviour of MockFridaBridge so existing tests
 * that do not need customisation can use FakeFridaBridge as a drop-in.
 */
class FakeFridaBridge(
    var listClassesStreamFn: (ListClassesParams, suspend (ListClassesPartialResult) -> Unit, () -> Unit) -> Unit = { params, onChunk, onComplete ->
        val all = listOf("com.example.MainActivity", "java.lang.String")
        val filtered = all.filter { it.contains(params.searchParam, ignoreCase = true) }
        CoroutineScope(Dispatchers.Default).launch {
            onChunk(ListClassesPartialResult(filtered))
        }
        onComplete()
    },
    var pingJavaFn: () -> String = { "Mock: Java OK" },
    var testRpcFn: () -> String = { "Mock: RPC OK" },
    var countInstancesFn: (CountInstancesParams) -> CountInstancesResult = { params ->
        CountInstancesResult(if (params.className == "com.example.MainActivity") 5 else 0)
    },
    var inspectClassFn: (InspectClassParams) -> InspectClassResult = { _ ->
        InspectClassResult(
            staticAttributes = listOf("public static final java.lang.String TAG"),
            instanceAttributes = listOf("private int mCount"),
            methods = listOf("public void onCreate(android.os.Bundle)")
        )
    },
    var listInstancesFn: (ListInstancesParams) -> ListInstancesResult = { _ ->
        ListInstancesResult(
            instances = listOf(InstanceInfo("123", "0x123", "com.example.MainActivity@123")),
            totalCount = 1
        )
    },
    var inspectInstanceFn: (InspectInstanceParams) -> InspectInstanceResult = { _ ->
        InspectInstanceResult(attributes = listOf(InstanceAttribute("mCount", "int", "5")))
    },
    var setFieldValueFn: (SetFieldValueParams) -> SetFieldValueResult = { params ->
        SetFieldValueResult("Success: ${params.fieldName} set to ${params.newValue}")
    },
    var hookMethodFn: (HookParams) -> HookMethodResult = { params ->
        HookMethodResult("Hooked ${params.className}.${params.methodSig}")
    },
    var getHookEventsFn: () -> HookEventsResult = {
        HookEventsResult(
            eventData = listOf("com.example.MainActivity", "onCreate(android.os.Bundle)").joinToString(",")
        )
    },
    var setMethodImplementationFn: (SetMethodImplementationParams) -> SetMethodImplementationResult = { params ->
        SetMethodImplementationResult("Implementation replaced for ${params.className}.${params.methodSig}")
    },
    var runOnceFn: (RunOnceParams) -> RunOnceResult = { _ ->
        RunOnceResult("Script executed")
    },
    var getInstanceAddressesFn: (GetInstanceAddressesParams) -> GetInstanceAddressesResult = { _ ->
        GetInstanceAddressesResult(listOf("0x123", "0x456"))
    },
    var prepareEnvironmentFn: (PrepareEnvParams) -> PrepareEnvResult = { params ->
        PrepareEnvResult("Attached to ${params.target ?: "unknown"}, ready to receive commands")
    },
    var injectGadgetFromScratchFn: (InjectGadgetParams) -> InjectGadgetResult = { _ ->
        InjectGadgetResult("completed", listOf(InjectionStep("1", "Step 1", "completed")))
    },
    var injectJdwpFn: (InjectJdwpParams) -> InjectJdwpResult = { _ ->
        InjectJdwpResult("Success")
    },
    var healthCheckFn: () -> HealthCheckResult = {
        HealthCheckResult("ok", mapOf("bridge" to CheckResponse("ok", "Bridge is running")))
    },
    var closeFn: () -> Unit = {}
) : FridaBridge {

    override val jsonParser: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val fridaCoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun listClassesStream(
        params: ListClassesParams,
        onChunk: suspend (partialResult: ListClassesPartialResult) -> Unit,
        onComplete: () -> Unit
    ) = listClassesStreamFn(params, onChunk, onComplete)

    override fun pingJava(): String = pingJavaFn()

    override fun testRpc(): String = testRpcFn()

    override fun countInstances(params: CountInstancesParams): CountInstancesResult = countInstancesFn(params)

    override fun inspectClass(params: InspectClassParams): InspectClassResult = inspectClassFn(params)

    override fun listInstances(params: ListInstancesParams): ListInstancesResult = listInstancesFn(params)

    override fun inspectInstance(params: InspectInstanceParams): InspectInstanceResult = inspectInstanceFn(params)

    override fun setFieldValue(params: SetFieldValueParams): SetFieldValueResult = setFieldValueFn(params)

    override fun hookMethod(params: HookParams): HookMethodResult = hookMethodFn(params)

    override fun getHookEvents(): HookEventsResult = getHookEventsFn()

    override fun setMethodImplementation(params: SetMethodImplementationParams): SetMethodImplementationResult = setMethodImplementationFn(params)

    override fun runOnce(params: RunOnceParams): RunOnceResult = runOnceFn(params)

    override fun getInstanceAddresses(params: GetInstanceAddressesParams): GetInstanceAddressesResult = getInstanceAddressesFn(params)

    override fun prepareEnvironment(params: PrepareEnvParams): PrepareEnvResult = prepareEnvironmentFn(params)

    override fun injectGadgetFromScratch(params: InjectGadgetParams): InjectGadgetResult = injectGadgetFromScratchFn(params)

    override fun injectJdwp(params: InjectJdwpParams): InjectJdwpResult = injectJdwpFn(params)

    override fun healthCheck(): HealthCheckResult = healthCheckFn()

    override fun close() = closeFn()
}