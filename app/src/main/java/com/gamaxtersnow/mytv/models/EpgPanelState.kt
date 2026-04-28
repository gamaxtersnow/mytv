package com.gamaxtersnow.mytv.models

data class EpgPanelRow(
    val id: Int,
    val displayNumber: String,
    val title: String,
    val group: String,
    val programSummary: ProgramSummary,
    val isPlaying: Boolean
) {
    val currentText: String
        get() = programSummary.currentScheduleText.ifBlank { title }

    val nextText: String
        get() = programSummary.nextTitle
}

data class EpgPanelState(
    val rows: List<EpgPanelRow>,
    val selectedIndex: Int
) {
    companion object {
        fun fromGroups(groups: List<ChannelPanelGroup>, currentPlayingId: Int): EpgPanelState {
            val allRows = groups.firstOrNull { it.title == TVListViewModel.ALL_GROUP_KEY }
                ?.rows
                ?: groups.flatMap { it.rows }.distinctBy { it.id }.sortedBy { it.id }

            val rows = allRows.map { row ->
                EpgPanelRow(
                    id = row.id,
                    displayNumber = row.displayNumber,
                    title = row.title,
                    group = groups.firstOrNull { group -> group.rows.any { it.id == row.id } }?.title.orEmpty(),
                    programSummary = row.programSummary,
                    isPlaying = row.id == currentPlayingId || row.isPlaying
                )
            }
            val selected = rows.indexOfFirst { it.isPlaying }.takeIf { it >= 0 } ?: 0
            return EpgPanelState(rows = rows, selectedIndex = selected)
        }
    }
}
