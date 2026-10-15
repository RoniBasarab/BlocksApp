package com.roni.blocks.core.system

import com.roni.blocks.core.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * How a failure behaves (3.5.0). Release builds are quiet: the failure is logged, the app carries on
 * and the screen shows its error card in place of the broken part. Debug builds are [loud]: the same
 * failure is rethrown, so it crashes and gets fixed. Provided once in `AppModule` from `BuildConfig.DEBUG`;
 * tests pass `FailurePolicy(loud = false)` to check the quiet path.
 */
data class FailurePolicy(val loud: Boolean)

/**
 * The one place a failure goes (3.5.0, architecture.md §14). Talks to: [AppLogger].
 *
 * Cancellation is never a failure: a coroutine cancelled because its screen went away just stops.
 * A [CancellationException] thrown while the coroutine is still active (a `withTimeout` that ran out)
 * is a real failure and is treated like any other.
 */
@Singleton
class Failures @Inject constructor(
    private val logger: AppLogger,
    private val policy: FailurePolicy,
) {
    /** Logs [error] as a failure of [where]. A loud build then rethrows it. */
    fun report(where: String, error: Throwable) {
        logger.error("failed: $where", error)
        if (policy.loud) throw error
    }

    /**
     * `scope.launch`, except that a failure inside [block] calls [onFailure] (the screen marks its part
     * as failed) and is [report]ed instead of crashing the app.
     */
    fun launch(
        scope: CoroutineScope,
        where: String,
        onFailure: () -> Unit = {},
        block: suspend CoroutineScope.() -> Unit,
    ): Job = scope.launch {
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) currentCoroutineContext().ensureActive()
            onFailure()
            report(where, e)
        }
    }

    /** Runs [block]; a failure is [report]ed and gives null. For one-off work in plain suspend code. */
    suspend fun <T> attempt(where: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException) currentCoroutineContext().ensureActive()
            report(where, e)
            null
        }
}

/**
 * A flow whose failure doesn't kill its collector: the failure calls [onFailure], is reported, and the
 * flow completes. Put it last before `collect` / `stateIn` on every long-lived flow a ViewModel or
 * manager collects.
 */
fun <T> Flow<T>.reportFailures(failures: Failures, where: String, onFailure: () -> Unit = {}): Flow<T> =
    catch { e ->
        if (e is CancellationException) currentCoroutineContext().ensureActive()
        onFailure()
        failures.report(where, e)
    }
