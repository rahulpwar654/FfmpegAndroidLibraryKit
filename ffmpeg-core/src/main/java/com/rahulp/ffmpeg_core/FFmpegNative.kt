package com.rahulp.ffmpeg_core

object FFmpegNative {

    const val RETURN_CODE_INITIALIZATION_FAILED = -1000
    const val RETURN_CODE_COMMAND_ENTRYPOINT_MISSING = -200
    private val requiredLibraryLoadOrder = listOf(
        "avutil",
        "swresample",
        "swscale",
        "avcodec",
        "avformat",
        "avfilter",
        "avdevice",
        "ffmpeg_jni"
    )
    private val optionalCommandLibraries = listOf(
        "ffmpeg",
        "ffmpegcmd",
        "ffmpeg_cli"
    )

    @Volatile
    private var cachedInitializationResult: InitializationResult? = null

    object LogLevel {
        const val QUIET = -8
        const val PANIC = 0
        const val FATAL = 8
        const val ERROR = 16
        const val WARNING = 24
        const val INFO = 32
        const val VERBOSE = 40
        const val DEBUG = 48
        const val TRACE = 56
    }

    data class ExecutionOptions(
        val hideBanner: Boolean = true,
        val overwriteOutput: Boolean = false,
        val printStats: Boolean = false,
        val extraGlobalArgs: List<String> = emptyList(),
        val logLevel: Int? = null,
        val resetCancelFlag: Boolean = true
    )

    data class InitializationDiagnostics(
        val initialized: Boolean,
        val loadedLibraries: List<String> = emptyList(),
        val missingOptionalLibraries: List<String> = emptyList(),
        val failedLibrary: String? = null,
        val failureMessage: String? = null,
        val availableNativeSymbols: List<String> = emptyList(),
        val versionInfo: String? = null,
        val buildConfiguration: String? = null,
        val codecLicense: String? = null,
        val hasFfmpegMain: Boolean = false,
        val hasCommandEntrypoint: Boolean = false
    )

    data class InitializationResult(
        val isSuccess: Boolean,
        val diagnostics: InitializationDiagnostics
    )

    enum class NativeFeature(val symbol: String) {
        FFMPEG_MAIN("ffmpeg_main"),
        VERSION("av_version_info"),
        BUILD_CONFIGURATION("avcodec_configuration"),
        CODEC_LICENSE("avcodec_license")
    }

    fun initialize(forceRetry: Boolean = false): InitializationResult = synchronized(this) {
        if (!forceRetry) {
            cachedInitializationResult?.let { return it }
        }

        val loadedLibraries = mutableListOf<String>()
        var failedLibrary: String? = null
        var failureMessage: String? = null

        for (libraryName in requiredLibraryLoadOrder) {
            try {
                System.loadLibrary(libraryName)
                loadedLibraries += libraryName
            } catch (throwable: Throwable) {
                failedLibrary = libraryName
                failureMessage = throwable.message ?: throwable::class.java.simpleName
                break
            }
        }

        val missingOptionalLibraries = mutableListOf<String>()
        if (failedLibrary == null) {
            for (libraryName in optionalCommandLibraries) {
                runCatching { System.loadLibrary(libraryName) }
                    .onSuccess { loadedLibraries += libraryName }
                    .onFailure { missingOptionalLibraries += libraryName }
            }
        }

        val diagnostics = if (failedLibrary == null) {
            collectInitializationDiagnostics(
                loadedLibraries = loadedLibraries,
                missingOptionalLibraries = missingOptionalLibraries
            )
        } else {
            InitializationDiagnostics(
                initialized = false,
                loadedLibraries = loadedLibraries.toList(),
                failedLibrary = failedLibrary,
                failureMessage = failureMessage
            )
        }

        return InitializationResult(
            isSuccess = diagnostics.initialized,
            diagnostics = diagnostics
        ).also {
            cachedInitializationResult = it
        }
    }

    fun isInitialized(): Boolean = cachedInitializationResult?.isSuccess == true

    fun getInitializationDiagnostics(forceRefresh: Boolean = false): InitializationDiagnostics =
        initialize(forceRetry = forceRefresh).diagnostics

    fun execute(
        commands: Array<String>,
        callback: LogCallback
    ): Int {
        val initialization = initialize()
        if (!initialization.isSuccess) {
            return RETURN_CODE_INITIALIZATION_FAILED
        }
        if (!initialization.diagnostics.hasCommandEntrypoint) {
            return RETURN_CODE_COMMAND_ENTRYPOINT_MISSING
        }

        return nativeExecute(commands, callback)
    }

    fun execute(
        commands: Array<String>,
        callback: LogCallback?,
        options: ExecutionOptions
    ): Int {
        val initialization = initialize()
        if (!initialization.isSuccess) {
            return RETURN_CODE_INITIALIZATION_FAILED
        }
        if (!initialization.diagnostics.hasCommandEntrypoint) {
            return RETURN_CODE_COMMAND_ENTRYPOINT_MISSING
        }

        val configuredCommand = buildConfiguredCommand(commands, options)
        val logLevel = options.logLevel ?: Int.MIN_VALUE
        return nativeExecuteWithConfig(
            configuredCommand,
            callback,
            logLevel,
            options.resetCancelFlag
        )
    }

    private external fun nativeExecute(
        commands: Array<String>,
        callback: LogCallback?
    ): Int

    private external fun nativeExecuteWithConfig(
        commands: Array<String>,
        callback: LogCallback?,
        logLevel: Int,
        resetCancelFlag: Boolean
    ): Int

    fun cancel() {
        if (initialize().isSuccess) {
            nativeCancel()
        }
    }

    fun setLogLevel(level: Int) {
        if (initialize().isSuccess) {
            nativeSetLogLevel(level)
        }
    }

    fun getLogLevel(): Int =
        if (initialize().isSuccess) nativeGetLogLevel() else LogLevel.INFO

    fun getVersionInfo(): String =
        getInitializationDiagnostics().versionInfo ?: unavailableMessage()

    fun getBuildConfiguration(): String =
        getInitializationDiagnostics().buildConfiguration ?: unavailableMessage()

    fun getCodecLicense(): String =
        getInitializationDiagnostics().codecLicense ?: unavailableMessage()

    fun hasFfmpegMain(): Boolean = getInitializationDiagnostics().hasFfmpegMain

    fun isSymbolAvailable(symbolName: String): Boolean =
        initialize().isSuccess && nativeIsSymbolAvailable(symbolName)

    fun getAvailableNativeSymbols(): Array<String> =
        getInitializationDiagnostics().availableNativeSymbols.toTypedArray()

    fun getSupportedFeatures(): Set<NativeFeature> =
        NativeFeature.entries.filterTo(mutableSetOf()) { isSymbolAvailable(it.symbol) }

    private external fun nativeCancel()

    private external fun nativeSetLogLevel(level: Int)

    private external fun nativeGetLogLevel(): Int

    private external fun nativeGetVersionInfo(): String

    private external fun nativeGetBuildConfiguration(): String

    private external fun nativeGetCodecLicense(): String

    private external fun nativeHasFfmpegMain(): Boolean

    private external fun nativeIsSymbolAvailable(symbolName: String): Boolean

    private external fun nativeGetAvailableNativeSymbols(): Array<String>

    private fun buildConfiguredCommand(
        command: Array<String>,
        options: ExecutionOptions
    ): Array<String> {
        val finalArgs = mutableListOf<String>()
        if (options.hideBanner) {
            finalArgs += "-hide_banner"
        }
        if (options.overwriteOutput) {
            finalArgs += "-y"
        }
        if (options.printStats) {
            finalArgs += "-stats"
        }
        finalArgs += options.extraGlobalArgs
        finalArgs += command
        return finalArgs.toTypedArray()
    }

    private fun collectInitializationDiagnostics(
        loadedLibraries: List<String>,
        missingOptionalLibraries: List<String>
    ): InitializationDiagnostics {
        val hasCommandEntrypoint = safeNativeCall(defaultValue = false) { nativeHasFfmpegMain() }
        val versionInfo = safeNativeCall<String?>(null) { nativeGetVersionInfo() }
        val buildConfiguration = safeNativeCall<String?>(null) { nativeGetBuildConfiguration() }
        val codecLicense = safeNativeCall<String?>(null) { nativeGetCodecLicense() }
        val hasCoreSymbols = versionInfo != null || buildConfiguration != null || codecLicense != null
        val failureMessage = if (hasCoreSymbols) {
            null
        } else {
            "FFmpeg core symbols not found in loaded native libraries"
        }

        return InitializationDiagnostics(
            initialized = hasCoreSymbols,
            loadedLibraries = loadedLibraries.toList(),
            missingOptionalLibraries = missingOptionalLibraries.toList(),
            failureMessage = failureMessage,
            availableNativeSymbols = safeNativeCall(emptyList()) {
                nativeGetAvailableNativeSymbols().toList()
            },
            versionInfo = versionInfo,
            buildConfiguration = buildConfiguration,
            codecLicense = codecLicense,
            hasFfmpegMain = hasCommandEntrypoint,
            hasCommandEntrypoint = hasCommandEntrypoint
        )
    }

    private fun unavailableMessage(): String {
        val diagnostics = getInitializationDiagnostics()
        return diagnostics.failureMessage?.let { "unavailable ($it)" } ?: "unavailable"
    }

    private inline fun <T> safeNativeCall(defaultValue: T, block: () -> T): T =
        runCatching(block).getOrElse { defaultValue }
}