package com.gamaxtersnow.mytv.models

import com.gamaxtersnow.mytv.TV
import com.gamaxtersnow.mytv.api.FEPG
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChannelPanelStateTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun buildsAllGroupAndOriginalGroupsWithStableNumbers() {
        val groups = TVListViewModel.buildChannelPanelGroups(
            channels = listOf(
                channel(0, "CCTV-1", "央视"),
                channel(1, "CCTV-2", "央视"),
                channel(2, "湖南卫视", "卫视"),
            ),
            currentPlayingId = 1,
        )

        assertEquals(TVListViewModel.ALL_GROUP_KEY, groups[0].title)
        assertEquals(listOf("001", "002", "003"), groups[0].rows.map { it.displayNumber })
        assertEquals("央视", groups[1].title)
        assertEquals("卫视", groups[2].title)
        assertTrue(groups[0].rows[1].isPlaying)
        assertFalse(groups[0].rows[0].isPlaying)
    }

    @Test
    fun blankGroupFallsBackToCustom() {
        val groups = TVListViewModel.buildChannelPanelGroups(
            channels = listOf(
                channel(0, "未分组频道", ""),
            ),
            currentPlayingId = 0,
        )

        assertEquals(TVListViewModel.FALLBACK_GROUP_KEY, groups[1].title)
        assertEquals("未分组频道", groups[1].rows[0].title)
    }

    @Test
    fun missingCurrentChannelFallsBackToFirstPlayableChannel() {
        val groups = TVListViewModel.buildChannelPanelGroups(
            channels = listOf(
                channel(4, "CCTV-5", "央视"),
                channel(8, "CCTV-9", "央视"),
            ),
            currentPlayingId = 99,
        )

        assertTrue(groups[0].rows[0].isPlaying)
        assertFalse(groups[0].rows[1].isPlaying)
    }

    @Test
    fun preservesLogoAndCurrentProgramInRows() {
        val groups = TVListViewModel.buildChannelPanelGroups(
            channels = listOf(
                channel(
                    id = 0,
                    title = "CCTV-1",
                    group = "央视",
                    logo = "https://example.com/logo.png",
                    currentProgram = "新闻联播"
                ),
            ),
            currentPlayingId = 0,
        )

        assertEquals("https://example.com/logo.png", groups[0].rows[0].logo)
        assertEquals("新闻联播", groups[0].rows[0].currentProgram)
    }

    @Test
    fun reusesPanelGroupsUntilEpgVersionChanges() {
        val tvListViewModel = TVListViewModel()
        val tvViewModel = TVViewModel(
            TV(
                id = 0,
                title = "CCTV-1",
                videoUrl = listOf("http://example.com/live.m3u8"),
                channel = "央视",
            )
        )

        tvViewModel.addFEPG(listOf(FEPG("旧节目", "2020-01-01T00:00:00Z")))
        tvListViewModel.addTVViewModel(tvViewModel)

        val firstGroups = tvListViewModel.channelPanelGroups(currentPlayingId = 0)
        val secondGroups = tvListViewModel.channelPanelGroups(currentPlayingId = 0)

        assertSame(firstGroups, secondGroups)

        tvViewModel.addFEPG(listOf(FEPG("新节目", "2020-01-01T01:00:00Z")))
        val refreshedGroups = tvListViewModel.channelPanelGroups(currentPlayingId = 0)

        assertNotSame(firstGroups, refreshedGroups)
        assertEquals("新节目", refreshedGroups[0].rows[0].currentProgram)
    }

    private fun channel(
        id: Int,
        title: String,
        group: String,
        logo: String = "",
        currentProgram: String = ""
    ): ChannelPanelSource {
        return ChannelPanelSource(
            id = id,
            title = title,
            logo = logo,
            group = group,
            currentProgram = currentProgram,
        )
    }
}
