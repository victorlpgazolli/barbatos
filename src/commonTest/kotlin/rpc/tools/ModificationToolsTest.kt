package rpc.tools

import bridge.FakeFridaBridge
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import model.rpc.HookEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModificationToolsTest {

    // ─── SetFieldValueTool ────────────────────────────────────────────────────

    @Test
    fun setFieldValue_returnsSuccess_withAllParams() {
        val tool = SetFieldValueTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity")
            put("id", "123")
            put("fieldName", "mCount")
            put("type", "int")
            put("newValue", "10")
        }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("Success: mCount set to 10"))
    }

    @Test
    fun setFieldValue_returnsError_whenClassNameMissing() {
        val tool = SetFieldValueTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("id", "123"); put("fieldName", "mCount"); put("type", "int"); put("newValue", "10")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing className"))
    }

    @Test
    fun setFieldValue_returnsError_whenIdMissing() {
        val tool = SetFieldValueTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity"); put("fieldName", "mCount"); put("type", "int"); put("newValue", "10")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing id"))
    }

    @Test
    fun setFieldValue_returnsError_whenFieldNameMissing() {
        val tool = SetFieldValueTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity"); put("id", "123"); put("type", "int"); put("newValue", "10")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing fieldName"))
    }

    @Test
    fun setFieldValue_returnsError_whenTypeMissing() {
        val tool = SetFieldValueTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity"); put("id", "123"); put("fieldName", "mCount"); put("newValue", "10")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing type"))
    }

    @Test
    fun setFieldValue_returnsError_whenNewValueMissing() {
        val tool = SetFieldValueTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity"); put("id", "123"); put("fieldName", "mCount"); put("type", "int")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing newValue"))
    }

    @Test
    fun setFieldValue_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(setFieldValueFn = { _, _, _, _, _ -> throw RuntimeException("write failed") })
        val tool = SetFieldValueTool(bridge)
        val args = buildJsonObject {
            put("className", "com.example.MainActivity"); put("id", "123")
            put("fieldName", "mCount"); put("type", "int"); put("newValue", "10")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("write failed"))
    }

    // ─── HookMethodTool ───────────────────────────────────────────────────────

    @Test
    fun hookMethod_returnsHookedMessage_withAllParams() {
        val tool = HookMethodTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity")
            put("methodSig", "onCreate(android.os.Bundle)")
        }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("Hooked com.example.MainActivity.onCreate(android.os.Bundle)"))
    }

    @Test
    fun hookMethod_returnsError_whenClassNameMissing() {
        val tool = HookMethodTool(FakeFridaBridge())
        val args = buildJsonObject { put("methodSig", "onCreate(android.os.Bundle)") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing className"))
    }

    @Test
    fun hookMethod_returnsError_whenMethodSigMissing() {
        val tool = HookMethodTool(FakeFridaBridge())
        val args = buildJsonObject { put("className", "com.example.MainActivity") }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing methodSig"))
    }

    @Test
    fun hookMethod_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(hookMethodFn = { _, _ -> throw RuntimeException("hook failed") })
        val tool = HookMethodTool(bridge)
        val args = buildJsonObject {
            put("className", "com.example.MainActivity")
            put("methodSig", "onCreate(android.os.Bundle)")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("hook failed"))
    }

    // ─── GetHookEventsTool ────────────────────────────────────────────────────

    @Test
    fun getHookEvents_returnsEventList_withDefaultBridge() {
        val tool = GetHookEventsTool(FakeFridaBridge())
        val result = tool.invoke(null)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("com.example.MainActivity"))
        assertTrue(result.content.first().text!!.contains("onCreate(android.os.Bundle)"))
    }

    @Test
    fun getHookEvents_returnsMultipleEvents_whenBridgeOverridden() {
        val bridge = FakeFridaBridge(
            getHookEventsFn = {
                listOf(
                    HookEvent("com.foo.ClassA", "methodA()"),
                    HookEvent("com.bar.ClassB", "methodB(int)")
                )
            }
        )
        val tool = GetHookEventsTool(bridge)
        val result = tool.invoke(null)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("com.foo.ClassA"))
        assertTrue(result.content.first().text!!.contains("com.bar.ClassB"))
    }

    @Test
    fun getHookEvents_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(getHookEventsFn = { throw RuntimeException("frida disconnected") })
        val tool = GetHookEventsTool(bridge)
        val result = tool.invoke(null)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("frida disconnected"))
    }

    // ─── SetMethodImplementationTool ──────────────────────────────────────────

    @Test
    fun setMethodImplementation_returnsReplacedMessage_withAllParams() {
        val tool = SetMethodImplementationTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity")
            put("methodSig", "onCreate(android.os.Bundle)")
            put("code", "return null;")
        }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("Implementation replaced for com.example.MainActivity.onCreate"))
    }

    @Test
    fun setMethodImplementation_returnsError_whenClassNameMissing() {
        val tool = SetMethodImplementationTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("methodSig", "onCreate(android.os.Bundle)"); put("code", "return null;")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing className"))
    }

    @Test
    fun setMethodImplementation_returnsError_whenMethodSigMissing() {
        val tool = SetMethodImplementationTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity"); put("code", "return null;")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing methodSig"))
    }

    @Test
    fun setMethodImplementation_returnsError_whenCodeMissing() {
        val tool = SetMethodImplementationTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity"); put("methodSig", "onCreate(android.os.Bundle)")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing code"))
    }

    // ─── RunOnceTool ──────────────────────────────────────────────────────────

    @Test
    fun runOnce_returnsScriptExecuted_withAllParams() {
        val tool = RunOnceTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity")
            put("methodSig", "onCreate(android.os.Bundle)")
            put("code", "console.log('hi');")
        }
        val result = tool.invoke(args)
        assertFalse(result.isError)
        assertTrue(result.content.first().text!!.contains("Script executed"))
    }

    @Test
    fun runOnce_returnsError_whenClassNameMissing() {
        val tool = RunOnceTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("methodSig", "onCreate(android.os.Bundle)"); put("code", "console.log('hi');")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing className"))
    }

    @Test
    fun runOnce_returnsError_whenMethodSigMissing() {
        val tool = RunOnceTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity"); put("code", "console.log('hi');")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing methodSig"))
    }

    @Test
    fun runOnce_returnsError_whenCodeMissing() {
        val tool = RunOnceTool(FakeFridaBridge())
        val args = buildJsonObject {
            put("className", "com.example.MainActivity"); put("methodSig", "onCreate(android.os.Bundle)")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("Missing code"))
    }

    @Test
    fun runOnce_returnsError_whenBridgeThrows() {
        val bridge = FakeFridaBridge(runOnceFn = { _, _, _ -> throw RuntimeException("script error") })
        val tool = RunOnceTool(bridge)
        val args = buildJsonObject {
            put("className", "com.example.MainActivity")
            put("methodSig", "onCreate(android.os.Bundle)")
            put("code", "bad code")
        }
        val result = tool.invoke(args)
        assertTrue(result.isError)
        assertTrue(result.content.first().text!!.contains("script error"))
    }
}
