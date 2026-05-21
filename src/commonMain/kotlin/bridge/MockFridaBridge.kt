package bridge

import rpc.ClassInspectionResult
import rpc.HookEvent
import rpc.InspectInstanceResult
import rpc.InstanceAttribute
import rpc.InstanceInfo
import rpc.ListInstancesResult

class MockFridaBridge : FridaBridge {
    // Mock implementations will go here
    override fun listClasses(searchParam: String, appPackage: String, offset: Int, limit: Int): List<String> {
        val all = listOf("com.example.MainActivity", "java.lang.String")
        return all.filter { it.contains(searchParam, ignoreCase = true) }
    }

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

    override fun inspectInstance(className: String, id: String, offset: Int, limit: Int): InspectInstanceResult {
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

    override fun prepareEnvironment(): rpc.PrepareEnvResult {
        return rpc.PrepareEnvResult(1234, "com.example", 8080, "device1")
    }

    override fun injectGadgetFromScratch(withLogs: Boolean, limit: Int): rpc.InjectionProgressResult {
        return rpc.InjectionProgressResult("completed", listOf(rpc.InjectionStep("1", "Step 1", "done")))
    }

    override fun injectJdwp(target: String, port: Int, packageName: String): String {
        return "Success"
    }

    override fun healthCheck(): rpc.HealthCheckResponse {
        return rpc.HealthCheckResponse("ok", mapOf("bridge" to rpc.CheckResponse("ok", "Bridge is running")))
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

    override fun checkIosDeployStatus(): rpc.GenericStatusResult {
        return rpc.GenericStatusResult("completed")
    }
}