package com.gamaxtersnow.mytv.models

data class ProgramSummary(
    val currentTitle: String = "",
    val currentStartTimeText: String = "",
    val currentEndTimeText: String = "",
    val nextTitle: String = "",
    val nextStartTimeText: String = "",
    val progressPercent: Int? = null,
    val hasEpg: Boolean = false
) {
    val currentScheduleText: String
        get() = when {
            currentStartTimeText.isBlank() || currentEndTimeText.isBlank() -> currentTitle
            currentTitle.isBlank() -> "$currentStartTimeText-$currentEndTimeText"
            else -> "$currentTitle  $currentStartTimeText-$currentEndTimeText"
        }

    companion object {
        fun from(epg: List<EPG>, nowSeconds: Long): ProgramSummary {
            if (epg.isEmpty()) {
                return ProgramSummary()
            }

            val sorted = epg.sortedBy { it.beginTime }
            val currentIndex = sorted.indexOfLast { candidate ->
                candidate.beginTime.toLong() <= nowSeconds
            }

            if (currentIndex == -1) {
                return ProgramSummary(hasEpg = true)
            }

            val current = sorted[currentIndex]
            val next = sorted.getOrNull(currentIndex + 1)
            val progressPercent = if (current.endTime > current.beginTime) {
                (((nowSeconds - current.beginTime) * 100) / (current.endTime - current.beginTime))
                    .toInt()
                    .coerceIn(0, 100)
            } else {
                null
            }

            return ProgramSummary(
                currentTitle = current.title,
                currentStartTimeText = formatTime(current.beginTime),
                currentEndTimeText = formatTime(current.endTime),
                nextTitle = next?.title.orEmpty(),
                nextStartTimeText = next?.beginTime?.let(::formatTime).orEmpty(),
                progressPercent = progressPercent,
                hasEpg = true
            )
        }

        private fun formatTime(timestampSeconds: Int): String {
            if (timestampSeconds <= 0) {
                return ""
            }
            val totalMinutes = (timestampSeconds / 60) % (24 * 60)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return "%02d:%02d".format(hours, minutes)
        }
    }
}
