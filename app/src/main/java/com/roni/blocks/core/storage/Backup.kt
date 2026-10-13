package com.roni.blocks.core.storage

import com.roni.blocks.core.model.InstantSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The tag every Blocks backup carries (3.4.0). */
const val BACKUP_APP = "blocks"

/**
 * A full backup of the app's stored data (3.4.0): every key of DAY, SETTINGS and HISTORY as raw
 * strings, tagged with the app name and the version that wrote it, so a restore can refuse a foreign
 * file or one written by a newer app.
 */
@Serializable
data class BackupFile(
    val app: String = BACKUP_APP,
    val version: Int,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val files: Map<String, Map<String, String>>,
)

/** Reads and writes [BackupFile] (3.4.0). */
object Backup {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(version: Int, createdAt: Instant, files: Map<String, Map<String, String>>): String =
        json.encodeToString(BackupFile.serializer(), BackupFile(version = version, createdAt = createdAt, files = files))

    /** The backup in [text], or null when the text isn't a Blocks backup at all. */
    fun decode(text: String): BackupFile? =
        runCatching { json.decodeFromString(BackupFile.serializer(), text) }.getOrNull()?.takeIf { it.app == BACKUP_APP }

    /** True when [file] can be restored into an app with [versionCode]: never a newer backup (3.4.0). */
    fun canRestore(file: BackupFile, versionCode: Int): Boolean = file.version <= versionCode
}
