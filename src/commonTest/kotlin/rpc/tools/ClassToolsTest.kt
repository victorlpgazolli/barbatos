package rpc.tools

import bridge.FakeFridaBridge
import model.rpc.ClassInspectionResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassToolsTest {

    // ─── InspectClassTool ─────────────────────────────────────────────────────

    @Test
    fun inspectClass_returnsMethodsAndAttributes_withClassName() {
        val tool = InspectClassTool(FakeFridaBridge())
        val args = buildJsonObject { put("className", "com.example.MainActivity") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("onCreate"))
        assertTrue(result.content.first().text!!.contains("TAG"))
        assertTrue(result.content.first().text!!.contains("mCount"))
    }

    @Test
    fun inspectClass_returnsCustomInspection_whenBridgeOverridden() {
        val bridge = FakeFridaBridge(
            inspectClassFn = { _ ->
                ClassInspectionResult(
                    staticAttributes = listOf("public static int VERSION"),
                    instanceAttributes = listOf("private String mName"),
                    methods = listOf("public String getName()")
                )
            }
        )
        val tool = InspectClassTool(bridge)
        val args = buildJsonObject { put("className", "com.custom.MyClass") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("VERSION"))
        assertTrue(result.content.first().text!!.contains("mName"))
        assertTrue(result.content.first().text!!.contains("getName"))
    }

    @Test
    fun inspectClass_returnsError_whenClassNameMissing() {
        val tool = InspectClassTool(FakeFridaBridge())
        val result = tool.invoke(null)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing className"))
    }

    // ─── ListClassesTool ──────────────────────────────────────────────────────

    @Test
    fun listClasses_returnsFilteredResults_withSearchParam() {
        val tool = ListClassesTool(FakeFridaBridge())
        val args = buildJsonObject { put("search_param", "MainActivity") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("com.example.MainActivity"))
    }

    @Test
    fun listClasses_returnsAllClasses_whenSearchParamEmpty() {
        val tool = ListClassesTool(FakeFridaBridge())
        // no search_param → should match all
        val result = tool.invoke(null)
        assertFalse(result.isError)
        // Default FakeFridaBridge returns both classes when search_param is ""
        assertTrue(result.content.first().text!!.contains("com.example.MainActivity"))
        assertTrue(result.content.first().text!!.contains("java.lang.String"))
    }

    @Test
    fun listClasses_returnsEmptyResult_whenNoClassMatchesFilter() {
        val tool = ListClassesTool(FakeFridaBridge())
        val args = buildJsonObject { put("search_param", "com.nonexistent") }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        // The default bridge filters, so nothing should match
        val text = result.content.first().text ?: ""
        assertFalse(text.contains("com.example.MainActivity"))
    }
}
