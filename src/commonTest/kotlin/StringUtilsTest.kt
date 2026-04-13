import kotlin.test.Test
import kotlin.test.assertEquals

class StringUtilsTest {
    @Test
    fun testExtractParams() {
        val sig = "public static void com.pkg.Class.methodName(int, java.lang.String)"
        assertEquals("int, String", StringUtils.extractParams(sig))
    }

    @Test
    fun testExtractParamsEmpty() {
        val sig = "public static void com.pkg.Class.methodName()"
        assertEquals("", StringUtils.extractParams(sig))
    }

    @Test
    fun testExtractParamsNoParens() {
        val sig = "public static int com.pkg.Class.fieldName"
        assertEquals("", StringUtils.extractParams(sig))
    }

    @Test
    fun testExtractMemberName() {
        val sig = "public static void com.pkg.Class.methodName(int, java.lang.String)"
        assertEquals("methodName", StringUtils.extractMemberName(sig))
    }

    @Test
    fun testExtractMemberNameField() {
        val sig = "public static int com.pkg.Class.fieldName"
        assertEquals("fieldName", StringUtils.extractMemberName(sig))
    }

    @Test
    fun testSplitTopLevelCommas() {
        val input = "a=1, b=Test{x=1, y=2}, c=3"
        val parts = StringUtils.splitTopLevelCommas(input)
        assertEquals(3, parts.size)
        assertEquals("a=1", parts[0])
        assertEquals("b=Test{x=1, y=2}", parts[1])
        assertEquals("c=3", parts[2])
    }

    @Test
    fun testSplitTopLevelCommasNested() {
        val input = "x=Foo(a=1, b=2), y=3"
        val parts = StringUtils.splitTopLevelCommas(input)
        assertEquals(2, parts.size)
        assertEquals("x=Foo(a=1, b=2)", parts[0])
        assertEquals("y=3", parts[1])
    }

    @Test
    fun testSplitTopLevelCommasSingle() {
        val input = "value"
        val parts = StringUtils.splitTopLevelCommas(input)
        assertEquals(1, parts.size)
        assertEquals("value", parts[0])
    }
}
