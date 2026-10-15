package com.roni.blocks.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.roni.blocks.core.di.IoDispatcher
import com.roni.blocks.core.system.Failures
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * The SharedPreferences files. [UNREADABLE] (3.5.0) keeps values that couldn't be decoded, so nothing
 * unreadable is ever overwritten and lost: Settings → Your data counts them and can share them.
 */
enum class PrefsFile { DAY, SETTINGS, HISTORY, UNREADABLE }

/**
 * Typed read / write / observe over SharedPreferences (architecture.md §7).
 *
 * Failures (3.5.0, §14): a value that can't be decoded is moved to [PrefsFile.UNREADABLE] and reads as
 * null, so the app carries on with a fresh value instead of crashing on every start; a failed commit is
 * reported. Both go through [Failures], so a debug build still crashes on them.
 *
 * Talks to: [Failures].
 */
@Singleton
class PrefsStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher internal val io: CoroutineDispatcher,
    private val failures: Failures,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val files: Map<PrefsFile, SharedPreferences> = PrefsFile.entries.associateWith { file ->
        context.getSharedPreferences(file.name.lowercase(), Context.MODE_PRIVATE)
    }

    suspend fun <T> write(file: PrefsFile, key: String, value: T, serializer: KSerializer<T>) {
        val encoded = json.encodeToString(serializer, value)
        withContext(io) {
            if (!files.getValue(file).edit().putString(key, encoded).commit()) {
                failures.report("write ${file.name}/$key", IOException("commit returned false"))
            }
        }
    }

    /** The value under [key], or null when there is none or it couldn't be read (then it is set aside). */
    suspend fun <T> read(file: PrefsFile, key: String, serializer: KSerializer<T>): T? {
        val raw = withContext(io) { rawString(file, key) } ?: return null
        return decodeOrSetAside(file, key, raw, serializer)
    }

    /** Emits the value now and after every change to [key] (or a clear of the whole file). */
    fun <T> observe(file: PrefsFile, key: String, serializer: KSerializer<T>): Flow<T?> = callbackFlow {
        val prefs = files.getValue(file)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key || changed == null) trySend(Unit)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().map { read(file, key, serializer) }

    suspend fun remove(file: PrefsFile, key: String) {
        withContext(io) {
            files.getValue(file).edit().remove(key).commit()
        }
    }

    fun keys(file: PrefsFile): Set<String> = files.getValue(file).all.keys

    /** How many values were set aside as unreadable (3.5.0); Settings → Your data shows it. */
    fun unreadableCount(): Int = files.getValue(PrefsFile.UNREADABLE).all.size

    /** The values set aside, "<file>/<key>@<millis>" → the raw text, for sharing. */
    suspend fun unreadable(): Map<String, String> = withContext(io) {
        files.getValue(PrefsFile.UNREADABLE).all.mapValues { (_, value) -> value as? String ?: value.toString() }
    }

    /** Every key of every file as raw strings (3.4.0), for a backup. */
    suspend fun exportAll(): Map<String, Map<String, String>> = withContext(io) {
        PrefsFile.entries.associate { file ->
            file.name to files.getValue(file).all.mapValues { (_, value) -> value as? String ?: "" }
        }
    }

    /** Replaces every file's contents with [backup] (3.4.0): clear each file, write every entry, commit. */
    suspend fun replaceAll(backup: Map<String, Map<String, String>>) {
        withContext(io) {
            PrefsFile.entries.forEach { file ->
                val editor = files.getValue(file).edit().clear()
                backup[file.name].orEmpty().forEach { (key, value) -> editor.putString(key, value) }
                if (!editor.commit()) failures.report("restore ${file.name}", IOException("commit returned false"))
            }
        }
    }

    /** The stored string, or null. A key holding something else (never written by us) reads as null. */
    private fun rawString(file: PrefsFile, key: String): String? =
        try {
            files.getValue(file).getString(key, null)    // not `all[key]`: `all` copies the whole file's map
        } catch (e: ClassCastException) {
            null
        }

    private suspend fun <T> decodeOrSetAside(file: PrefsFile, key: String, raw: String, serializer: KSerializer<T>): T? =
        try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {   // SerializationException, a date that doesn't parse, a bad enum name...
            setAside(file, key, raw)
            failures.report("read ${file.name}/$key", e)
            null
        }

    /**
     * Copies [raw] into [PrefsFile.UNREADABLE], and only once that copy is on disk removes it from [file],
     * so the next save starts fresh without losing the original.
     */
    private suspend fun setAside(file: PrefsFile, key: String, raw: String) {
        withContext(io) {
            val asideKey = "${file.name.lowercase()}/$key@${System.currentTimeMillis()}"
            val kept = files.getValue(PrefsFile.UNREADABLE).edit().putString(asideKey, raw).commit()
            if (kept) files.getValue(file).edit().remove(key).commit()
        }
    }
}
