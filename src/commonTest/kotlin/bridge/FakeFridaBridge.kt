package bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import model.bridge.FridaBridge
import model.rpc.CheckResponse
import model.rpc.ClassInspectionResult
import model.rpc.GenericStatusResult
import model.rpc.HealthCheckResult
import model.rpc.HookEvent
import model.rpc.InjectionProgressResult
import model.rpc.InjectionStep
import model.rpc.InspectInstanceResult
import model.rpc.InstanceAttribute
import model.rpc.InstanceInfo
import model.rpc.ListInstancesResult
import model.rpc.PrepareEnvResult

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
    var listClassesStreamFn: (String, String, Int, Int, suspend (List<String>) -> Unit, () -> Unit) -> Unit = { searchParam, _, _, _, onChunk, onComplete ->
        val all = listOf("com.example.MainActivity", "java.lang.String")
        val filtered = all.filter { it.contains(searchParam, ignoreCase = true) }
        CoroutineScope(Dispatchers.Default).launch { onChunk(filtered) }
        onComplete()
    },
    var pingJavaFn: () -> String = { "Mock: Java OK" },
    var testRpcFn: () -> String = { "Mock: RPC OK" },
    var countInstancesFn: (String) -> Int = { className ->
        if (className == "com.example.MainActivity") 5 else 0
    },
    var inspectClassFn: (String) -> ClassInspectionResult = { _ ->
        ClassInspectionResult(
            staticAttributes = listOf("public static final java.lang.String TAG"),
            instanceAttributes = listOf("private int mCount"),
            methods = listOf("public void onCreate(android.os.Bundle)")
        )
    },
    var listInstancesFn: (String) -> ListInstancesResult = { _ ->
        ListInstancesResult(
            instances = listOf(InstanceInfo("123", "0x123", "com.example.MainActivity@123")),
            totalCount = 1
        )
    },
    var inspectInstanceFn: (String, Int, Int) -> InspectInstanceResult = { _, _, _ ->
        InspectInstanceResult(attributes = listOf(InstanceAttribute("mCount", "int", "5")))
    },
    var setFieldValueFn: (String, String, String, String, String) -> String = { _, _, fieldName, _, newValue ->
        "Success: $fieldName set to $newValue"
    },
    var hookMethodFn: (String, String) -> String = { className, methodSig ->
        "Hooked $className.$methodSig"
    },
    var getHookEventsFn: () -> List<HookEvent> = {
        listOf(HookEvent("com.example.MainActivity", "onCreate(android.os.Bundle)"))
    },
    var setMethodImplementationFn: (String, String, String) -> String = { className, methodSig, _ ->
        "Implementation replaced for $className.$methodSig"
    },
    var runOnceFn: (String, String, String) -> String = { _, _, _ -> "Script executed" },
    var getInstanceAddressesFn: (String) -> List<String> = { _ -> listOf("0x123", "0x456") },
    var prepareEnvironmentFn: (String, Int?) -> PrepareEnvResult = { target, _ ->
        PrepareEnvResult(1234, "com.example", 8080, "Attached to $target")
    },
    var injectGadgetFromScratchFn: (Boolean, Int) -> InjectionProgressResult = { _, _ ->
        InjectionProgressResult("completed", listOf(InjectionStep("1", "Step 1", "done")))
    },
    var injectJdwpFn: (String, Int, String) -> String = { _, _, _ -> "Success" },
    var healthCheckFn: () -> HealthCheckResult = {
        HealthCheckResult("ok", mapOf("bridge" to CheckResponse("ok", "Bridge is running")))
    },
    var patchAndInstallIosAppFn: (String) -> String = { _ -> "success" },
    var checkIosJailbreakStatusFn: (String) -> String = { _ -> "jailbroken" },
    var injectJailbrokenIosFn: (String) -> String = { _ -> "success" },
    var checkIosDeployStatusFn: () -> GenericStatusResult = { GenericStatusResult("completed") },
    var closeFn: () -> Unit = {}
) : FridaBridge {

    override val jsonParser: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val fridaCoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun listClassesStream(
        searchParam: String,
        appPackage: String,
        offset: Int,
        limit: Int,
        onChunk: suspend (List<String>) -> Unit,
        onComplete: () -> Unit
    ) = listClassesStreamFn(searchParam, appPackage, offset, limit, onChunk, onComplete)

    override fun pingJava(): String = pingJavaFn()
    override fun testRpc(): String = testRpcFn()
    override fun countInstances(className: String): Int = countInstancesFn(className)
    override fun inspectClass(className: String): ClassInspectionResult = inspectClassFn(className)
    override fun listInstances(className: String): ListInstancesResult = listInstancesFn(className)
    override fun inspectInstance(id: String, offset: Int, limit: Int): InspectInstanceResult = inspectInstanceFn(id, offset, limit)
    override fun setFieldValue(className: String, id: String, fieldName: String, type: String, newValue: String): String =
        setFieldValueFn(className, id, fieldName, type, newValue)
    override fun hookMethod(className: String, methodSig: String): String = hookMethodFn(className, methodSig)
    override fun getHookEvents(): List<HookEvent> = getHookEventsFn()
    override fun setMethodImplementation(className: String, methodSig: String, code: String): String =
        setMethodImplementationFn(className, methodSig, code)
    override fun runOnce(className: String, methodSig: String, code: String): String = runOnceFn(className, methodSig, code)
    override fun getInstanceAddresses(className: String): List<String> = getInstanceAddressesFn(className)
    override fun prepareEnvironment(target: String, pid: Int?): PrepareEnvResult = prepareEnvironmentFn(target, pid)
    override fun injectGadgetFromScratch(serial: String?, withLogs: Boolean, limit: Int): InjectionProgressResult = injectGadgetFromScratchFn(withLogs, limit)
    override fun injectJdwp(target: String, port: Int, packageName: String): String = injectJdwpFn(target, port, packageName)
    override fun healthCheck(): HealthCheckResult = healthCheckFn()
    override fun patchAndInstallIosApp(appPath: String): String = patchAndInstallIosAppFn(appPath)
    override fun checkIosJailbreakStatus(serial: String): String = checkIosJailbreakStatusFn(serial)
    override fun injectJailbrokenIos(serial: String): String = injectJailbrokenIosFn(serial)
    override fun checkIosDeployStatus(): GenericStatusResult = checkIosDeployStatusFn()
    override fun close() = closeFn()
}
