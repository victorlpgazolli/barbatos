package bridge

import rpc.ClassInspectionResult
import rpc.HookEvent
import rpc.InspectInstanceResult
import rpc.ListInstancesResult

interface FridaBridge {
    // Methods will be added here as we migrate endpoints
    fun listClasses(searchParam: String, appPackage: String, offset: Int, limit: Int): List<String>
    fun countInstances(className: String): Int
    fun inspectClass(className: String): ClassInspectionResult
    fun listInstances(className: String): ListInstancesResult
    fun inspectInstance(className: String, id: String, offset: Int, limit: Int): InspectInstanceResult
    
    fun setFieldValue(className: String, id: String, fieldName: String, type: String, newValue: String): String
    fun hookMethod(className: String, methodSig: String): String
    fun getHookEvents(): List<HookEvent>
    fun setMethodImplementation(className: String, methodSig: String, code: String): String
    fun runOnce(className: String, methodSig: String, code: String): String
    fun getInstanceAddresses(className: String): List<String>
    
    fun prepareEnvironment(): rpc.PrepareEnvResult
    fun injectGadgetFromScratch(withLogs: Boolean, limit: Int): rpc.InjectionProgressResult
    fun injectJdwp(target: String, port: Int, packageName: String): String
    fun healthCheck(): rpc.HealthCheckResponse
}