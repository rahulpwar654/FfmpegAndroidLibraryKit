package com.rahulp.ffmpeg_core

import com.rahulp.ffmpeg_core.command.FFmpegCommandFactory
import com.rahulp.ffmpeg_core.command.FilterCommandRequest
import com.rahulp.ffmpeg_core.command.MergeCommandRequest
import com.rahulp.ffmpeg_core.command.ReelAspectMode
import com.rahulp.ffmpeg_core.command.ReelsCommandRequest
import com.rahulp.ffmpeg_core.command.TrimCommandRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FFmpegCommandFactoryTest {

    @Test
    fun buildTrimCommand_containsExpectedArguments() {
        val args = FFmpegCommandFactory.buildTrimCommand(
            TrimCommandRequest(
                inputPath = "input.mp4",
                outputPath = "trimmed.mp4",
                startSeconds = 5.0,
                endSeconds = 10.0,
                reEncode = false
            )
        )

        assertEquals(listOf("-i", "input.mp4", "-ss", "5.000", "-to", "10.000", "-c", "copy", "trimmed.mp4"), args)
    }

    @Test
    fun buildFilterCommand_appliesVideoFilterAndAudioCodec() {
        val args = FFmpegCommandFactory.buildFilterCommand(
            FilterCommandRequest(
                inputPath = "in.mp4",
                outputPath = "out.mp4",
                videoFilter = "hue=s=0"
            )
        )

        assertTrue(args.containsAll(listOf("-vf", "hue=s=0", "-c:v", "mpeg4", "-c:a", "aac", "out.mp4")))
    }

    @Test
    fun buildMergeCommand_usesConcatDemuxer() {
        val args = FFmpegCommandFactory.buildMergeCommand(
            MergeCommandRequest(
                concatFilePath = "list.txt",
                outputPath = "merged.mp4"
            )
        )

        assertEquals(listOf("-f", "concat", "-safe", "0", "-i", "list.txt", "-c", "copy", "merged.mp4"), args)
    }

    @Test
    fun buildReelsCommand_addsAspectAndSpeedFilters() {
        val args = FFmpegCommandFactory.buildReelsCommand(
            ReelsCommandRequest(
                inputPath = "clip.mp4",
                outputPath = "reel.mp4",
                speedMultiplier = 1.5,
                aspectMode = ReelAspectMode.CROP_9_16
            )
        )

        val vfIndex = args.indexOf("-vf")
        assertTrue(vfIndex >= 0)
        assertTrue(args[vfIndex + 1].contains("crop=1080:1920"))
        assertTrue(args.contains("-filter:a"))
    }

    @Test
    fun buildConcatFileContent_escapesQuotes() {
        val content = FFmpegCommandFactory.buildConcatFileContent(
            listOf("C:/Videos/one.mp4", "C:/Videos/two's.mp4")
        )

        assertEquals("file 'C:/Videos/one.mp4'\nfile 'C:/Videos/two'\\''s.mp4'", content)
    }
}

