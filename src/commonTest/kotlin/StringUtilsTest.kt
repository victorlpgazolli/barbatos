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

    @Test
    fun testGetScrambledTextProgress0() {
        val old = "OLDTEXT"
        val new = "NEWTEXT"
        val result = StringUtils.getScrambledText(old, new, 0.0)
        assertEquals(old.length, result.length)
        // At 0.0, the first character is scrambled: pivot = -1, window = [-2, 0], i=0 in window
        assertEquals(old.substring(1), result.substring(1))
    }

    @Test
    fun testGetScrambledTextProgress1() {
        val old = "OLDTEXT"
        val new = "NEWTEXT"
        val result = StringUtils.getScrambledText(old, new, 1.0)
        assertEquals(new, result)
    }

    @Test
    fun testGetScrambledTextProgressMid() {
        val old = "OLDTEXT"
        val new = "NEWTEXT"
        val result = StringUtils.getScrambledText(old, new, 0.5)
        assertEquals(old.length, result.length)
        assertEquals(new.substring(0, 3), result.substring(0, 3))
        assertEquals(old.substring(6, 7), result.substring(6, 7))
    }

    @Test
    fun testGetScrambledTextDifferentLengths() {
        val old = "SHORT"
        val new = "LONGSTRING"
        val result = StringUtils.getScrambledText(old, new, 0.5)
        assertEquals(10, result.length)
        assertEquals(new.substring(0, 4), result.substring(0, 4))
        assertEquals("   ", result.substring(7, 10))
    }
}
