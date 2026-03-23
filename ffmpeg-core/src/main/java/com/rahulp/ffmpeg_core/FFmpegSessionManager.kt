package com.rahulp.ffmpeg_core

object FFmpegSessionManager {

    private val sessions = mutableMapOf<Long, FFmpegSession>()
    private var counter = 0L

    fun create(command: String, args: List<String> = emptyList()): FFmpegSession {
        val session = FFmpegSession(++counter, command, args = args)
        sessions[session.id] = session
        return session
    }

    fun renderCommand(args: List<String>): String = args.joinToString(" ") { argument ->
        if (argument.any(Char::isWhitespace)) {
            val escaped = argument.replace("\"", "\\\"")
            "\"$escaped\""
        } else {
            argument
        }
    }
}