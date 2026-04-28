package com.gamaxtersnow.mytv.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgPanelStateTest {
    @Test
    fun channelsWithCurrentAndNextProgrammeProduceRows() {
        val state = EpgPanelState.fromGroups(
            groups = listOf(
                ChannelPanelGroup(
                    TVListViewModel.ALL_GROUP_KEY,
                    listOf(
                        row(
                            id = 0,
                            title = "CCTV-1",
                            summary = ProgramSummary(
                                currentTitle = "朝闻天下",
                                currentStartTimeText = "06:00",
                                currentEndTimeText = "08:36",
                                nextTitle = "生活早参考",
                                hasEpg = true
                            )
                        )
                    )
                )
            ),
            currentPlayingId = 0
        )

        assertEquals("朝闻天下  06:00-08:36", state.rows[0].currentText)
        assertEquals("生活早参考", state.rows[0].nextText)
    }

    @Test
    fun emptyEpgProducesChannelOnlyRows() {
        val state = EpgPanelState.fromGroups(
            groups = listOf(ChannelPanelGroup(TVListViewModel.ALL_GROUP_KEY, listOf(row(0, "CCTV-1")))),
            currentPlayingId = 0
        )

        assertEquals("CCTV-1", state.rows[0].currentText)
    }

    @Test
    fun groupOrderRemainsStable() {
        val groups = TVListViewModel.buildChannelPanelGroups(
            channels = listOf(
                ChannelPanelSource(1, "CCTV-2", "", "央视"),
                ChannelPanelSource(0, "CCTV-1", "", "央视")
            ),
            currentPlayingId = 0
        )

        val state = EpgPanelState.fromGroups(groups, currentPlayingId = 0)

        assertEquals(listOf(0, 1), state.rows.map { it.id })
    }

    @Test
    fun currentPlayingChannelIsSelected() {
        val state = EpgPanelState.fromGroups(
            groups = listOf(ChannelPanelGroup(TVListViewModel.ALL_GROUP_KEY, listOf(row(0, "A"), row(1, "B")))),
            currentPlayingId = 1
        )

        assertEquals(1, state.selectedIndex)
        assertTrue(state.rows[1].isPlaying)
    }

    private fun row(
        id: Int,
        title: String,
        summary: ProgramSummary = ProgramSummary()
    ): ChannelPanelRow {
        return ChannelPanelRow(
            id = id,
            displayNumber = "%03d".format(id + 1),
            title = title,
            logo = "",
            programSummary = summary,
            isPlaying = false
        )
    }
}
