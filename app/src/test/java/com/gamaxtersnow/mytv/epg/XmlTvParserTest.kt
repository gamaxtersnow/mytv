package com.gamaxtersnow.mytv.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class XmlTvParserTest {
    private val parser = XmlTvParser()

    @Test
    fun parsesChannelsAndProgrammesFromXmltv() {
        val data = parser.parse(sampleXml.toByteArray())

        assertEquals(listOf("CCTV-1", "CCTV1"), data.channels.first().displayNames)
        assertEquals(2, data.programmes.size)
        assertEquals("1", data.programmes.first().channelId)
        assertEquals("朝闻天下", data.programmes.first().title)
        assertEquals(1714262400L, data.programmes.first().startTime)
        assertEquals(1714272960L, data.programmes.first().stopTime)
    }

    @Test
    fun parsesGzipXmltvPayload() {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(sampleXml.toByteArray()) }

        val data = parser.parse(output.toByteArray())

        assertEquals(1, data.channels.size)
        assertEquals(2, data.programmes.size)
    }

    @Test
    fun ignoresMalformedProgrammeRows() {
        val xml = """
            <tv>
              <channel id="x"><display-name>Test</display-name></channel>
              <programme start="20240428080000 +0800" stop="" channel="x"><title>Bad</title></programme>
              <programme start="20240428090000 +0800" stop="20240428100000 +0800" channel="x"><title>Good</title></programme>
            </tv>
        """.trimIndent()

        val data = parser.parse(xml.toByteArray())

        assertEquals(1, data.programmes.size)
        assertEquals("Good", data.programmes.first().title)
        assertTrue(data.channels.isNotEmpty())
    }

    private val sampleXml = """
        <tv>
          <channel id="1">
            <display-name>CCTV-1</display-name>
            <display-name>CCTV1</display-name>
          </channel>
          <programme start="20240428080000 +0800" stop="20240428105600 +0800" channel="1">
            <title>朝闻天下</title>
          </programme>
          <programme start="20240428105600 +0800" stop="20240428113000 +0800" channel="1">
            <title>生活早参考</title>
          </programme>
        </tv>
    """.trimIndent()
}
