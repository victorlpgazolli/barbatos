package bridge

import rpc.ClassInspectionResult
import rpc.InspectInstanceResult
import rpc.ListInstancesResult

interface FridaBridge {
    // Methods will be added here as we migrate endpoints
    fun listClasses(searchParam: String, appPackage: String, offset: Int, limit: Int): List<String>
    fun countInstances(className: String): Int
    fun inspectClass(className: String): ClassInspectionResult
    fun listInstances(className: String): ListInstancesResult
    fun inspectInstance(className: String, id: String, offset: Int, limit: Int): InspectInstanceResult
}