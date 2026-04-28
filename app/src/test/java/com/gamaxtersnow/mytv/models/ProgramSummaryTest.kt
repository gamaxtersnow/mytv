package com.gamaxtersnow.mytv.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramSummaryTest {
    @Test
    fun emptyEpgReturnsBlankSummary() {
        val summary = ProgramSummary.from(emptyList(), nowSeconds = 100)

        assertFalse(summary.hasEpg)
        assertEquals("", summary.currentTitle)
        assertNull(summary.progressPercent)
    }

    @Test
    fun returnsCurrentAndNextProgramme() {
        val summary = ProgramSummary.from(
            epg = listOf(
                EPG("朝闻天下", beginTime = 1_000, endTime = 1_600),
                EPG("生活早参考", beginTime = 1_600, endTime = 2_000)
            ),
            nowSeconds = 1_300
        )

        assertTrue(summary.hasEpg)
        assertEquals("朝闻天下", summary.currentTitle)
        assertEquals("生活早参考", summary.nextTitle)
        assertEquals(50, summary.progressPercent)
    }

    @Test
    fun progressIsClampedBetweenZeroAndHundred() {
        val summary = ProgramSummary.from(
            epg = listOf(EPG("测试节目", beginTime = 100, endTime = 200)),
            nowSeconds = 999
        )

        assertEquals(100, summary.progressPercent)
    }

    @Test
    fun missingNextProgrammeLeavesNextFieldsBlank() {
        val summary = ProgramSummary.from(
            epg = listOf(EPG("单个节目", beginTime = 100, endTime = 200)),
            nowSeconds = 150
        )

        assertEquals("单个节目", summary.currentTitle)
        assertEquals("", summary.nextTitle)
        assertEquals("", summary.nextStartTimeText)
    }
}
