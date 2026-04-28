package com.gamaxtersnow.mytv.models


data class EPG(
    val title: String,
    val beginTime: Int,
    val endTime: Int = 0,
)
