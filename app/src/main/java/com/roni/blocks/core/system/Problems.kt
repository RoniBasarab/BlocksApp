package com.roni.blocks.core.system

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.roni.blocks.MainActivity
import com.roni.blocks.core.logging.AppLogger
import com.roni.blocks.core.storage.PrefsStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * What the error card offers (3.5.0). A screen wraps it in its own Action, `XAction.Problem(ProblemAction)`,
 * and its ViewModel hands it to [AppRescue.handle], the way sheets wrap `BlockSheetAction`.
 */
sealed interface ProblemAction {
    data object Restart : ProblemAction
    data object SendLog : ProblemAction
}

/**
 * The ways out when something failed (3.5.0): restart the app, share the log, share what couldn't be read.
 * Starts activities from the application context, so ViewModels never hold a Context.
 * Talks to: [AppLogger], [PrefsStorage].
 */
@Singleton
class AppRescue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
    private val prefs: PrefsStorage,
) {
    fun handle(action: ProblemAction) = when (action) {
        ProblemAction.Restart -> restart()
        ProblemAction.SendLog -> shareLog()
    }

    /** A fresh MainActivity, then this process exits, so every store loads from disk again. */
    fun restart() {
        logger.info("restart requested")
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        Runtime.getRuntime().exit(0)
    }

    fun shareLog() = share(logger.shareFile(), "Share log")

    /** Writes the set-aside values next to the log (the folder FileProvider already serves) and shares them. */
    suspend fun shareUnreadable() {
        val items = prefs.unreadable()
        val text = Json { prettyPrint = true }.encodeToString(MapSerializer(String.serializer(), String.serializer()), items)
        val file = File(logger.shareFile().parentFile, "unreadable.json").apply { writeText(text) }
        share(file, "Share unreadable data")
    }

    private fun share(file: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
