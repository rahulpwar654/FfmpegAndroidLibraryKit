package com.rahulp.ffmpeg_core.command

import kotlin.math.abs
import java.util.Locale

enum class ReelAspectMode {
    CROP_9_16,
    PAD_9_16
}

data class TrimCommandRequest(
    val inputPath: String,
    val outputPath: String,
    val startSeconds: Double,
    val endSeconds: Double,
    val reEncode: Boolean = true
)

data class FilterCommandRequest(
    val inputPath: String,
    val outputPath: String,
    val videoFilter: String,
    val includeAudio: Boolean = true,
    val videoQScale: Int = 5,
    val audioBitrate: String = "128k"
)

data class MergeCommandRequest(
    val concatFilePath: String,
    val outputPath: String,
    val reEncode: Boolean = false,
    val videoQScale: Int = 5,
    val audioBitrate: String = "128k"
)

data class ReelsCommandRequest(
    val inputPath: String,
    val outputPath: String,
    val maxDurationSeconds: Int = 30,
    val speedMultiplier: Double = 1.0,
    val aspectMode: ReelAspectMode = ReelAspectMode.PAD_9_16,
    val muteAudio: Boolean = false,
    val extraVideoFilter: String? = null,
    val videoQScale: Int = 4,
    val audioBitrate: String = "128k"
)

object FFmpegCommandFactory {

    fun buildTrimCommand(request: TrimCommandRequest): List<String> {
        val start = request.startSeconds.coerceAtLeast(0.0)
        val end = request.endSeconds.coerceAtLeast(start)

        val args = mutableListOf(
            "-i", request.inputPath,
            "-ss", formatSeconds(start),
            "-to", formatSeconds(end)
        )

        if (request.reEncode) {
            args += listOf("-c:v", "mpeg4", "-q:v", "4", "-c:a", "aac", "-b:a", "128k")
        } else {
            args += listOf("-c", "copy")
        }

        args += request.outputPath
        return args
    }

    fun buildFilterCommand(request: FilterCommandRequest): List<String> {
        val args = mutableListOf(
            "-i", request.inputPath,
            "-vf", request.videoFilter,
            "-c:v", "mpeg4",
            "-q:v", request.videoQScale.toString(),
            "-pix_fmt", "yuv420p"
        )

        if (request.includeAudio) {
            args += listOf("-c:a", "aac", "-b:a", request.audioBitrate)
        } else {
            args += "-an"
        }

        args += listOf("-movflags", "+faststart", request.outputPath)
        return args
    }

    fun buildMergeCommand(request: MergeCommandRequest): List<String> {
        val args = mutableListOf(
            "-f", "concat",
            "-safe", "0",
            "-i", request.concatFilePath
        )

        if (request.reEncode) {
            args += listOf("-c:v", "mpeg4", "-q:v", request.videoQScale.toString(), "-c:a", "aac", "-b:a", request.audioBitrate)
        } else {
            args += listOf("-c", "copy")
        }

        args += request.outputPath
        return args
    }

    fun buildReelsCommand(request: ReelsCommandRequest): List<String> {
        val args = mutableListOf("-i", request.inputPath)

        val baseAspectFilter = when (request.aspectMode) {
            ReelAspectMode.CROP_9_16 -> "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920"
            ReelAspectMode.PAD_9_16 -> "scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2"
        }

        val filterParts = mutableListOf(baseAspectFilter)
        request.extraVideoFilter?.takeIf { it.isNotBlank() }?.let { filterParts += it }

        val normalizedSpeed = request.speedMultiplier.coerceIn(0.5, 2.0)
        if (abs(normalizedSpeed - 1.0) > 0.0001) {
            filterParts += "setpts=${formatSpeedRatio(1.0 / normalizedSpeed)}*PTS"
        }

        args += listOf("-vf", filterParts.joinToString(","))
        args += listOf("-t", request.maxDurationSeconds.coerceAtLeast(1).toString())
        args += listOf("-c:v", "mpeg4", "-q:v", request.videoQScale.toString(), "-pix_fmt", "yuv420p")

        if (request.muteAudio) {
            args += "-an"
        } else {
            if (abs(normalizedSpeed - 1.0) > 0.0001) {
                args += listOf("-filter:a", "atempo=${formatSpeedRatio(normalizedSpeed)}")
            }
            args += listOf("-c:a", "aac", "-b:a", request.audioBitrate)
        }

        args += listOf("-movflags", "+faststart", request.outputPath)
        return args
    }

    fun buildConcatFileContent(inputPaths: List<String>): String {
        return inputPaths.joinToString(separator = "\n") { path ->
            val escaped = path.replace("'", "'\\''")
            "file '$escaped'"
        }
    }

    private fun formatSeconds(value: Double): String = String.format(Locale.US, "%.3f", value)

    private fun formatSpeedRatio(value: Double): String = String.format(Locale.US, "%.3f", value)
}


