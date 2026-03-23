package com.rahulp.ffmpeg_core

data class FFmpegSession(
    val id: Long,
    val command: String,
    val args: List<String> = emptyList(),
    var state: SessionState = SessionState.CREATED,
    var returnCode: Int? = null,
    var logs: MutableList<String> = mutableListOf(),
    var progress: Long = 0L
)

enum class SessionState {
    CREATED, RUNNING, COMPLETED, FAILED
}