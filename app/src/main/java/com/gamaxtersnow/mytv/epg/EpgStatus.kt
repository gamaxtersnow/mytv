package com.gamaxtersnow.mytv.epg

data class EpgStatus(
    val sourceKey: String = "",
    val lastSuccessTime: Long = 0L,
    val coveredStartDate: String = "",
    val coveredEndDate: String = "",
    val isAvailable: Boolean = false,
    val isStale: Boolean = false,
    val message: String = ""
) {
    val dateRangeText: String
        get() = when {
            coveredStartDate.isBlank() && coveredEndDate.isBlank() -> ""
            coveredStartDate == coveredEndDate -> coveredStartDate
            else -> "$coveredStartDate - $coveredEndDate"
        }
}
