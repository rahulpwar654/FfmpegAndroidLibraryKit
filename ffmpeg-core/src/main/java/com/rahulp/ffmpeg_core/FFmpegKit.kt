package com.rahulp.ffmpeg_core

object FFmpegKit {
    /**
     * Executes the given FFmpeg command asynchronously.
     * Usage:
     * FFmpegKit.executeAsync(
     *     "-i input.mp4 -vf scale=1280:720 output.mp4",
     *     onComplete = { session ->
     *         println("Done: ${session.returnCode}")
     *     },
     *     onProgress = { time ->
     *         println("Progress: $time sec")
     *     }
     * )
     */
    fun executeAsync(
        command: String,
        onComplete: (FFmpegSession) -> Unit,
        onProgress: (Long) -> Unit
    ): FFmpegSession = executeAsync(
        args = splitCommand(command),
        options = FFmpegNative.ExecutionOptions(),
        command = command,
        onComplete = onComplete,
        onProgress = onProgress,
        onLog = {}
    )

    fun executeAsync(
        command: String,
        options: FFmpegNative.ExecutionOptions,
        onComplete: (FFmpegSession) -> Unit,
        onProgress: (Long) -> Unit
    ): FFmpegSession = executeAsync(
        args = splitCommand(command),
        options = options,
        command = command,
        onComplete = onComplete,
        onProgress = onProgress,
        onLog = {}
    )

    fun executeAsync(
        command: String,
        options: FFmpegNative.ExecutionOptions,
        onComplete: (FFmpegSession) -> Unit,
        onProgress: (Long) -> Unit,
        onLog: (String) -> Unit
    ): FFmpegSession = executeAsync(
        args = splitCommand(command),
        options = options,
        command = command,
        onComplete = onComplete,
        onProgress = onProgress,
        onLog = onLog
    )

    fun executeAsync(
        args: List<String>,
        onComplete: (FFmpegSession) -> Unit,
        onProgress: (Long) -> Unit
    ): FFmpegSession = executeAsync(
        args = args,
        options = FFmpegNative.ExecutionOptions(),
        command = null,
        onComplete = onComplete,
        onProgress = onProgress,
        onLog = {}
    )

    fun executeAsync(
        args: List<String>,
        options: FFmpegNative.ExecutionOptions,
        onComplete: (FFmpegSession) -> Unit,
        onProgress: (Long) -> Unit
    ): FFmpegSession = executeAsync(
        args = args,
        options = options,
        command = null,
        onComplete = onComplete,
        onProgress = onProgress,
        onLog = {}
    )

    fun executeAsync(
        args: List<String>,
        options: FFmpegNative.ExecutionOptions,
        onComplete: (FFmpegSession) -> Unit,
        onProgress: (Long) -> Unit,
        onLog: (String) -> Unit
    ): FFmpegSession = executeAsync(
        args = args,
        options = options,
        command = null,
        onComplete = onComplete,
        onProgress = onProgress,
        onLog = onLog
    )

    private fun executeAsync(
        args: List<String>,
        options: FFmpegNative.ExecutionOptions,
        command: String?,
        onComplete: (FFmpegSession) -> Unit,
        onProgress: (Long) -> Unit,
        onLog: (String) -> Unit
    ): FFmpegSession {

        val session = FFmpegSessionManager.create(
            command = command ?: FFmpegSessionManager.renderCommand(args),
            args = args
        )
        session.state = SessionState.RUNNING

        Thread {
            val result = FFmpegNative.execute(
                args.toTypedArray(),
                object : LogCallback {

                    override fun onLog(message: String) {
                        session.logs.add(message)
                        onLog(message)

                        ProgressParser.parseTime(message)?.let {
                            session.progress = it
                            onProgress(it)
                        }
                    }
                },
                options
            )

            session.returnCode = result
            session.state = if (result == 0) {
                SessionState.COMPLETED
            } else {
                SessionState.FAILED
            }

            onComplete(session)
        }.start()

        return session
    }

    private fun splitCommand(command: String): List<String> {
        val tokenRegex = Regex("\"([^\"]*)\"|'([^']*)'|(\\S+)")
        return tokenRegex
            .findAll(command)
            .mapNotNull { match ->
                match.groups[1]?.value
                    ?: match.groups[2]?.value
                    ?: match.groups[3]?.value
            }
            .toList()
    }
}