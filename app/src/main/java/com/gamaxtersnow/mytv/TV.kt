package com.gamaxtersnow.mytv

import com.gamaxtersnow.mytv.models.ProgramType
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class TV(
    var id: Int = 0,
    var title: String,
    var alias: String = "",
    var videoUrl: List<String>,
    var channel: String = "",
    var logo: String = "",
    var pid: String = "",
    var sid: String = "",
    var programType: ProgramType = ProgramType.RTP,
    var needToken: Boolean = false,
    var mustToken: Boolean = false,
) {

    override fun toString(): String {
        return "TV{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", videoUrl='" + videoUrl + '\'' +
                ", logo='" + logo + '\'' +
                ", pid='" + pid + '\'' +
                ", sid='" + sid + '\'' +
                ", programType='" + programType + '\'' +
                '}'
    }
}