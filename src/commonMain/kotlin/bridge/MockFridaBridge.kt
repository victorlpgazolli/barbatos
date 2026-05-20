package bridge

import rpc.ClassInspectionResult
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
}