package com.lizongying.mytv.models

import kotlinx.serialization.Serializable

@Serializable
enum class ProgramType {
    Y_PROTO,
    Y_JCE,
    F,
    RTP,
}