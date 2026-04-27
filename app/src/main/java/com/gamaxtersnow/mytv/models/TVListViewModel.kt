package com.gamaxtersnow.mytv.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class ChannelPanelRow(
    val id: Int,
    val displayNumber: String,
    val title: String,
    val logo: String,
    val currentProgram: String,
    val isPlaying: Boolean
)

data class ChannelPanelGroup(
    val title: String,
    val rows: List<ChannelPanelRow>
)

data class ChannelPanelSource(
    val id: Int,
    val title: String,
    val logo: String,
    val group: String,
    val currentProgram: String = ""
)

class TVListViewModel : ViewModel() {

    var maxNum = mutableListOf<Int>()
    private var channelPanelCache: ChannelPanelCache? = null

    private val _tvListViewModel = MutableLiveData<MutableList<TVViewModel>>()
    val tvListViewModel: LiveData<MutableList<TVViewModel>>
        get() = _tvListViewModel

    private val _itemPosition = MutableLiveData<Int>()
    val itemPosition: LiveData<Int>
        get() = _itemPosition

    private val _itemPositionCurrent = MutableLiveData<Int>()
    val itemPositionCurrent: LiveData<Int>
        get() = _itemPositionCurrent

    fun addTVViewModel(tvViewModel: TVViewModel) {
        if (_tvListViewModel.value == null) {
            _tvListViewModel.value = mutableListOf(tvViewModel)
        } else {
            _tvListViewModel.value?.add(tvViewModel)
        }
        channelPanelCache = null
    }

    fun clearTVViewModels() {
        _tvListViewModel.value?.clear()
        maxNum.clear()
        channelPanelCache = null
    }

    fun getTVViewModel(id: Int): TVViewModel? {
        return _tvListViewModel.value?.get(id)
    }

    fun getTVViewModelCurrent(): TVViewModel? {
        return _itemPositionCurrent.value?.let { _tvListViewModel.value?.get(it) }
    }

    fun setItemPosition(position: Int) {
        _itemPosition.value = position
        _itemPositionCurrent.value = position
    }

    fun setItemPositionCurrent(position: Int) {
        _itemPositionCurrent.value = position
    }

    fun size(): Int {
        if (_tvListViewModel.value == null) {
            return 0
        }
        return _tvListViewModel.value!!.size
    }

    fun channelPanelGroups(currentPlayingId: Int = itemPosition.value ?: 0): List<ChannelPanelGroup> {
        val channels = _tvListViewModel.value.orEmpty()
        if (channels.isEmpty()) {
            return emptyList()
        }

        val channelSignature = channels.joinToString("|") { tvViewModel ->
            val tv = tvViewModel.getTV()
            "${tv.id}:${tv.title}:${tv.logo}:${tv.channel}"
        }
        val programSignature = channels.joinToString("|") { tvViewModel ->
            "${tvViewModel.getTV().id}:${tvViewModel.epgVersion}"
        }
        val timeBucket = System.currentTimeMillis() / PROGRAM_REFRESH_BUCKET_MS
        val cached = channelPanelCache
        if (
            cached?.channelSignature == channelSignature &&
            cached.programSignature == programSignature &&
            cached.timeBucket == timeBucket &&
            cached.currentPlayingId == currentPlayingId
        ) {
            return cached.groups
        }

        return buildChannelPanelGroups(
            channels = channels.map { it.toChannelPanelSource() },
            currentPlayingId = currentPlayingId,
        ).also {
            channelPanelCache = ChannelPanelCache(
                channelSignature = channelSignature,
                programSignature = programSignature,
                timeBucket = timeBucket,
                currentPlayingId = currentPlayingId,
                groups = it
            )
        }
    }

    companion object {
        const val ALL_GROUP_KEY = "__all__"
        const val FALLBACK_GROUP_KEY = "__custom__"
        private const val PROGRAM_REFRESH_BUCKET_MS = 60_000L

        fun buildChannelPanelGroups(
            channels: List<ChannelPanelSource>,
            currentPlayingId: Int,
        ): List<ChannelPanelGroup> {
            val normalizedGroups = channels
                .groupBy { fallbackGroup(it.group) }
                .filterValues { it.isNotEmpty() }

            val effectivePlayingId = if (channels.any { it.id == currentPlayingId }) {
                currentPlayingId
            } else {
                channels.minByOrNull { it.id }?.id ?: currentPlayingId
            }

            val rowsById = channels
                .distinctBy { it.id }
                .sortedBy { it.id }
                .map { it.toChannelPanelRow(effectivePlayingId) }
                .associateBy { it.id }

            val allRows = rowsById.values.toList()

            val groups = mutableListOf<ChannelPanelGroup>()
            groups.add(ChannelPanelGroup(ALL_GROUP_KEY, allRows))

            normalizedGroups.forEach { (title, channels) ->
                groups.add(
                    ChannelPanelGroup(
                        title = title,
                        rows = channels.sortedBy { it.id }
                            .mapNotNull { rowsById[it.id] }
                    )
                )
            }

            return groups.filter { it.rows.isNotEmpty() }
        }

        private fun TVViewModel.toChannelPanelSource(): ChannelPanelSource {
            val tv = getTV()
            val currentProgram = epg.value
                ?.asSequence()
                ?.filter { it.beginTime < System.currentTimeMillis() / 1000 }
                ?.lastOrNull()
                ?.title
                .orEmpty()

            return ChannelPanelSource(
                id = tv.id,
                title = tv.title,
                logo = tv.logo,
                group = tv.channel,
                currentProgram = currentProgram
            )
        }

        private fun ChannelPanelSource.toChannelPanelRow(currentPlayingId: Int): ChannelPanelRow {
            return ChannelPanelRow(
                id = id,
                displayNumber = "%03d".format(id + 1),
                title = title,
                logo = logo,
                currentProgram = currentProgram,
                isPlaying = id == currentPlayingId
            )
        }

        private fun fallbackGroup(group: String): String {
            return group.trim().ifEmpty { FALLBACK_GROUP_KEY }
        }
    }

    private data class ChannelPanelCache(
        val channelSignature: String,
        val programSignature: String,
        val timeBucket: Long,
        val currentPlayingId: Int,
        val groups: List<ChannelPanelGroup>
    )
}
