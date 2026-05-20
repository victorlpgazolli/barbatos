package bridge

class MockFridaBridge : FridaBridge {
    // Mock implementations will go here
    override fun listClasses(searchParam: String, appPackage: String, offset: Int, limit: Int): List<String> {
        val all = listOf("com.example.MainActivity", "java.lang.String")
        return all.filter { it.contains(searchParam, ignoreCase = true) }
    }

    override fun countInstances(className: String): Int {
        return if (className == "com.example.MainActivity") 5 else 0
    }
}