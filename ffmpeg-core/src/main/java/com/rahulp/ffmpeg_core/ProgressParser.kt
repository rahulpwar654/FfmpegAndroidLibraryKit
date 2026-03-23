package com.rahulp.ffmpeg_core

object ProgressParser {

    fun parseTime(log: String): Long? {
        val regex = Regex("time=(\\d+):(\\d+):(\\d+\\.\\d+)")
        val match = regex.find(log) ?: return null

        val (h, m, s) = match.destructured
        return (h.toLong()*3600 + m.toLong()*60 + s.toDouble()).toLong()
    }
}