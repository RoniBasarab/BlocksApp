package com.roni.blocks.core.logging

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogger @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir = File(context.filesDir, "logs").apply { mkdirs() }
    private val file = File(dir, "blocks.log")

    fun info(message: String) = write("I", message, null)

    fun error(message: String, error: Throwable? = null) = write("E", message, error)

    fun shareFile(): File {
        trim()
        return file
    }

    private fun write(level: String, message: String, error: Throwable?) {
        val line = "$level ${System.currentTimeMillis()} $message ${error?.stackTraceToString().orEmpty()}"
        Log.i("Blocks", line)
        runCatching {
            file.appendText(line.take(2000) + "\n")
            trim()
        }
    }

    private fun trim() {
        if (file.length() <= 1_000_000) return
        val tail = file.readLines().takeLast(400)
        file.writeText(tail.joinToString("\n"))
    }
}
