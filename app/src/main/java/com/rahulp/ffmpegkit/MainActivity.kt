package com.rahulp.ffmpegkit

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.rahulp.ffmpeg_core.FFmpegNative

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.text_ffmpeg_status)
        val initializeButton = findViewById<Button>(R.id.button_initialize_ffmpeg)

        fun initializeFfmpeg() {
            val initialization = FFmpegNative.initialize()
            if (initialization.isSuccess) {
                val message = "FFmpeg initialized. Version=${initialization.diagnostics.versionInfo}, symbols=${initialization.diagnostics.availableNativeSymbols}"
                statusText.text = message
                Log.i("FfmpegKit", message)
            } else {
                val message = "FFmpeg initialization failed at ${initialization.diagnostics.failedLibrary}: ${initialization.diagnostics.failureMessage}"
                statusText.text = message
                Log.w("FfmpegKit", message)
            }
        }

        initializeButton.setOnClickListener { initializeFfmpeg() }
        initializeFfmpeg()
    }
}
