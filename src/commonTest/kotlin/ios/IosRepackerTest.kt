package ios

import kotlin.test.Test
import kotlin.test.assertEquals

class IosRepackerTest {
    @Test
    fun testParseDeviceId() {
        val output = "00008101-000123456789ABCD (USB)\n"
        val id = IosRepacker.parseDeviceId(output)
        assertEquals("00008101-000123456789ABCD", id)
    }
}
