package com.roni.blocks.core.storage

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

enum class PrefsFile { DAY, SETTINGS, HISTORY }

@Singleton
class PrefsStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Dagger has no binding for a dispatcher, so tests that need writes to land synchronously swap this.
    internal var io: CoroutineDispatcher = Dispatchers.IO
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
            check(files.getValue(file).edit().putString(key, encoded).commit())
        }
    }

    suspend fun <T> read(file: PrefsFile, key: String, serializer: KSerializer<T>): T? {
        val raw = files.getValue(file).getString(key, null) ?: return null
        return json.decodeFromString(serializer, raw)
    }

    fun <T> observe(file: PrefsFile, key: String, serializer: KSerializer<T>): Flow<T?> = callbackFlow {
        val prefs = files.getValue(file)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key) trySend(decode(prefs, key, serializer))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(decode(prefs, key, serializer))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    suspend fun remove(file: PrefsFile, key: String) {
        withContext(io) {
            files.getValue(file).edit().remove(key).commit()
        }
    }

    fun keys(file: PrefsFile): Set<String> = files.getValue(file).all.keys

    private fun <T> decode(prefs: SharedPreferences, key: String, serializer: KSerializer<T>): T? {
        val raw = prefs.getString(key, null) ?: return null
        return json.decodeFromString(serializer, raw)
    }
}
