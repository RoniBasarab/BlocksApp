package com.roni.blocks.core.time

import com.roni.blocks.core.di.ApplicationScope
import com.roni.blocks.core.storage.PrefsFile
import com.roni.blocks.core.storage.PrefsStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer

/**
 * The one place every time label in the app is formatted from. The toggle lives in the
 * `settings` prefs; watching [use24] is how a screen repaints the moment it flips.
 */
@Singleton
class TimeFormatter @Inject constructor(
    private val prefs: PrefsStorage,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _use24 = MutableStateFlow(true)
    val use24: StateFlow<Boolean> = _use24.asStateFlow()

    init {
        scope.launch {
            _use24.value = prefs.read(PrefsFile.SETTINGS, KEY, Boolean.serializer()) ?: true
        }
    }

    suspend fun setUse24(value: Boolean) {
        prefs.write(PrefsFile.SETTINGS, KEY, value, Boolean.serializer())
        _use24.value = value
    }

    fun hourLabel(minute: Int): String = hourGutter(minute, _use24.value)

    fun label(minute: Int): String = Companion.label(minute, _use24.value)

    fun timeLabel(startMin: Int, endMin: Int): String = "${label(startMin)}–${label(endMin)}"

    companion object {
        private const val KEY = "hour_format_24"

        /** One source of truth for both formats; screens pass the flag from [use24]. */
        fun label(minute: Int, use24: Boolean): String =
            if (use24) "%02d:%02d".format(minute / 60, minute % 60)
            else "${((minute / 60 + 11) % 12) + 1}:${"%02d".format(minute % 60)} ${suffix(minute)}"

        /** Calendar gutter: 24h "08:00", 12h "8 AM". */
        fun hourGutter(minute: Int, use24: Boolean): String =
            if (use24) "%02d:00".format(minute / 60) else "${((minute / 60 + 11) % 12) + 1} ${suffix(minute)}"

        private fun suffix(minute: Int): String = if (minute / 60 < 12) "AM" else "PM"
    }
}
