package com.micharger.usage

import com.micharger.ui.usage.UsageParser
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageParserTest {

    @Test
    fun parseExtractsUidPowerLines() {
        val output = """
            Estimated power use (mAh):
              Capacity: 3300, Computed: 250
              Uid u0a123: 45.2 ( cpu=40 screen=3 radio=1 )
              Uid 1000: 12.1 ( cpu=12 )
              Uid u0a5: 0.0 ( cpu=0 )
        """.trimIndent()
        val result = UsageParser.parse(output)
        assertEquals(2, result.size)
        assertEquals(10123, result[0].uid)
        assertEquals(45.2, result[0].mah, 0.001)
        assertEquals(1000, result[1].uid)
    }

    @Test
    fun parseReturnsEmptyForGarbage() {
        assertEquals(0, UsageParser.parse("no data here").size)
    }
}
