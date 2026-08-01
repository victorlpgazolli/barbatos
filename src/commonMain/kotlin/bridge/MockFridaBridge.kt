package bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import rpc.model.*
class MockFridaBridge() : FridaBridge {
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
    ) {
        val all = listOf("com.example.MainActivity", "java.lang.String")
        val filtered = all.filter { it.contains(searchParam, ignoreCase = true) }
        fridaCoroutineScope.launch {
            onChunk(filtered)
        }
        onComplete()
    }

    override fun pingJava(): String = "Mock: Java OK"
    override fun testRpc(): String = "Mock: RPC OK"

    override fun countInstances(className: String): Int {
        return if (className == "com.example.MainActivity") 5 else 0
    }

    override fun inspectClass(className: String): ClassInspectionResult {
        return ClassInspectionResult(
            staticAttributes = listOf("public static final java.lang.String TAG"),
            instanceAttributes = listOf("private int mCount"),
            methods = listOf("public void onCreate(android.os.Bundle)")
        )
    }

    override fun listInstances(className: String): ListInstancesResult {
        return ListInstancesResult(
            instances = listOf(InstanceInfo("123", "0x123", "com.example.MainActivity@123")),
            totalCount = 1
        )
    }

    override fun inspectInstance(id: String, offset: Int, limit: Int): InspectInstanceResult {
        return InspectInstanceResult(
            attributes = listOf(InstanceAttribute("mCount", "int", "5"))
        )
    }

    override fun setFieldValue(className: String, id: String, fieldName: String, type: String, newValue: String): String {
        return "Success: $fieldName set to $newValue"
    }

    override fun hookMethod(className: String, methodSig: String): String {
        return "Hooked $className.$methodSig"
    }

    override fun getHookEvents(): List<HookEvent> {
        return listOf(HookEvent("com.example.MainActivity", "onCreate(android.os.Bundle)"))
    }

    override fun setMethodImplementation(className: String, methodSig: String, code: String): String {
        return "Implementation replaced for $className.$methodSig"
    }

    override fun runOnce(className: String, methodSig: String, code: String): String {
        return "Script executed"
    }

    override fun getInstanceAddresses(className: String): List<String> {
        return listOf("0x123", "0x456")
    }

    override fun prepareEnvironment(target: String, pid: Int?): PrepareEnvResult {
        return PrepareEnvResult(1234, "com.example", 8080, "Attached to $target")
    }

    override fun injectGadgetFromScratch(withLogs: Boolean, limit: Int): InjectionProgressResult {
        return InjectionProgressResult("completed", listOf(InjectionStep("1", "Step 1", "done")))
    }

    override fun injectJdwp(target: String, port: Int, packageName: String): String {
        return "Success"
    }

    override fun healthCheck(): HealthCheckResult {
        return HealthCheckResult("ok", mapOf("bridge" to CheckResponse("ok", "Bridge is running")))
    }

    override fun patchAndInstallIosApp(appPath: String): String {
        return "success"
    }

    override fun checkIosJailbreakStatus(serial: String): String {
        return "jailbroken"
    }

    override fun injectJailbrokenIos(serial: String): String {
        return "success"
    }

    override fun checkIosDeployStatus(): GenericStatusResult {
        return GenericStatusResult("completed")
    }

    override fun close() {
    }
}