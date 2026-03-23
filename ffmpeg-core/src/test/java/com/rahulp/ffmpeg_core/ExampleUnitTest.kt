package com.rahulp.ffmpeg_core

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun renderCommand_quotesWhitespaceArguments() {
        val args = listOf(
            "-i",
            "C:/Movies/input clip.mp4",
            "-vf",
            "scale=1280:720",
            "output.mp4"
        )

        assertEquals(
            "-i \"C:/Movies/input clip.mp4\" -vf scale=1280:720 output.mp4",
            FFmpegSessionManager.renderCommand(args)
        )
    }

    @Test
    fun createSession_preservesArgs() {
        val args = listOf("-version")
        val session = FFmpegSessionManager.create(
            command = FFmpegSessionManager.renderCommand(args),
            args = args
        )

        assertEquals(args, session.args)
        assertEquals("-version", session.command)
    }
}