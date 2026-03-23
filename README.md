# Description

`ffmpeg-core` is the native Android FFmpeg wrapper module in this project.
It loads packaged FFmpeg `.so` libraries, exposes diagnostics, and runs FFmpeg commands through Kotlin APIs.

## What `ffmpeg-core` provides

- Native library initialization and diagnostics via `FFmpegNative`
- Async command execution with progress/log callbacks via `FFmpegKit`
- Session tracking via `FFmpegSession` and `SessionState`
- Prebuilt command builders for common video tasks via `FFmpegCommandFactory`

## Module facts (from current project)

- Module path: `ffmpeg-core`
- Namespace: `com.rahulp.ffmpeg_core`
- `minSdk`: `29`
- Target ABIs currently filtered to: `armeabi-v7a`, `arm64-v8a`

---

# Usage

## 1) Add dependency

If your app module is in the same multi-module project:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":ffmpeg-core"))
}
```

## 2) Initialize native FFmpeg

Initialize once (for example in app startup or before first command):

```kotlin
import com.rahulp.ffmpeg_core.FFmpegNative

val init = FFmpegNative.initialize()
if (init.isSuccess) {
    println("FFmpeg version: ${init.diagnostics.versionInfo}")
    println("Has command entrypoint: ${init.diagnostics.hasCommandEntrypoint}")
} else {
    println("Initialization failed at ${init.diagnostics.failedLibrary}: ${init.diagnostics.failureMessage}")
}
```

Get diagnostics later:

```kotlin
val diagnostics = FFmpegNative.getInitializationDiagnostics()
println(diagnostics.loadedLibraries)
println(diagnostics.availableNativeSymbols)
```

## 3) Execute commands asynchronously

### String command

```kotlin
import com.rahulp.ffmpeg_core.FFmpegKit

val session = FFmpegKit.executeAsync(
    command = "-i input.mp4 -vf scale=720:-1 output.mp4",
    onComplete = { result ->
        println("Session ${result.id} finished with code ${result.returnCode}, state=${result.state}")
    },
    onProgress = { seconds ->
        println("Progress: ${seconds}s")
    }
)
```

### Argument list + options + logs

```kotlin
import com.rahulp.ffmpeg_core.FFmpegKit
import com.rahulp.ffmpeg_core.FFmpegNative

val args = listOf("-i", "input.mp4", "-vf", "hue=s=0", "output.mp4")

val options = FFmpegNative.ExecutionOptions(
    hideBanner = true,
    overwriteOutput = true,
    printStats = true,
    logLevel = FFmpegNative.LogLevel.INFO
)

FFmpegKit.executeAsync(
    args = args,
    options = options,
    onComplete = { session ->
        println("Done: code=${session.returnCode}, state=${session.state}")
    },
    onProgress = { seconds ->
        println("time=${seconds}s")
    },
    onLog = { line ->
        println(line)
    }
)
```

## 4) Build commands with `FFmpegCommandFactory`

```kotlin
import com.rahulp.ffmpeg_core.command.FFmpegCommandFactory
import com.rahulp.ffmpeg_core.command.TrimCommandRequest

val trimArgs = FFmpegCommandFactory.buildTrimCommand(
    TrimCommandRequest(
        inputPath = "input.mp4",
        outputPath = "trimmed.mp4",
        startSeconds = 5.0,
        endSeconds = 12.0,
        reEncode = true
    )
)

FFmpegKit.executeAsync(
    args = trimArgs,
    onComplete = { println("trim done: ${it.returnCode}") },
    onProgress = {}
)
```

Also available request builders:

- `FilterCommandRequest`
- `MergeCommandRequest`
- `ReelsCommandRequest`

For concat input files:

```kotlin
val content = FFmpegCommandFactory.buildConcatFileContent(
    listOf("/sdcard/Movies/a.mp4", "/sdcard/Movies/b.mp4")
)
// Write content to list.txt, then pass it to MergeCommandRequest.concatFilePath
```

## 5) Return codes and cancellation

- Success is return code `0`
- `FFmpegNative.RETURN_CODE_INITIALIZATION_FAILED = -1000`
- `FFmpegNative.RETURN_CODE_COMMAND_ENTRYPOINT_MISSING = -200`

Cancel a running command:

```kotlin
FFmpegNative.cancel()
```

---

## Minimal end-to-end flow

```kotlin
val init = FFmpegNative.initialize()
if (!init.isSuccess || !init.diagnostics.hasCommandEntrypoint) {
    return
}

FFmpegKit.executeAsync(
    command = "-i input.mp4 -c:v mpeg4 -q:v 5 -c:a aac -b:a 128k out.mp4",
    onComplete = { session ->
        // check session.returnCode and session.state
    },
    onProgress = { /* seconds */ }
)
```
