package bridge

interface FridaBridge {
    // Methods will be added here as we migrate endpoints
    fun listClasses(searchParam: String, appPackage: String, offset: Int, limit: Int): List<String>
    fun countInstances(className: String): Int
}