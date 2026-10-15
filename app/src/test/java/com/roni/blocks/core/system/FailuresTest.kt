package com.roni.blocks.core.system

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roni.blocks.core.logging.AppLogger
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 3.5.0. The logic was compiled and run by the planning agent with a stand-in logger; here the real
 * [AppLogger] writes its file, and [failures] reads back the "failed:" lines from it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FailuresTest {
    private val context = RuntimeEnvironment.getApplication()
    private val logger = AppLogger(context)
    private val quiet = Failures(logger, FailurePolicy(loud = false))
    private val loud = Failures(logger, FailurePolicy(loud = true))

    @Before
    fun clearLog() {
        File(context.filesDir, "logs/blocks.log").delete()
    }

    /** The log's failure lines as "failed: <where> <ExceptionClass>", like the stand-in recorded them. */
    private val failuresLogged: List<String>
        get() = File(context.filesDir, "logs/blocks.log").takeIf { it.exists() }?.readLines().orEmpty()
            .mapNotNull { line ->
                Regex("failed: (.+?) (?:java\\.lang\\.|kotlinx\\.coroutines\\.)?(\\w+(?:Exception|Error))").find(line)
                    ?.let { "failed: ${it.groupValues[1]} ${it.groupValues[2]}" }
            }

    @Test
    fun quiet_failureMarksThePart_logs_andTheScopeLivesOn() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var failed = 0
        quiet.launch(scope, "weekly load", onFailure = { failed++ }) { error("boom") }.join()
        assertEquals(1, failed)
        assertEquals(listOf("failed: weekly load IllegalStateException"), failuresLogged)
        var ranAfter = false
        quiet.launch(scope, "next") { ranAfter = true }.join()
        assertTrue("the scope must survive a failure", ranAfter)
    }

    @Test
    fun loud_failureStillCrashes() = runBlocking {
        var crashed: Throwable? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> crashed = e })
        var failed = 0
        loud.launch(scope, "home", onFailure = { failed++ }) { throw IllegalArgumentException("bad") }.join()
        assertTrue("debug must crash, got $crashed", crashed is IllegalArgumentException)
        assertEquals(1, failed)
        assertEquals(1, failuresLogged.size)
    }

    @Test
    fun cancellation_isNotAFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var failed = 0
        val job = quiet.launch(scope, "screen", onFailure = { failed++ }) { awaitCancellation() }
        yield(); delay(20)
        scope.cancel()
        job.join()
        assertEquals(0, failed)
        assertTrue("nothing logged, got ${failuresLogged}", failuresLogged.isEmpty())
    }

    @Test
    fun timeout_isAFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var failed = 0
        quiet.launch(scope, "slow read", onFailure = { failed++ }) { withTimeout(10) { delay(1_000) } }.join()
        assertEquals(1, failed)
        assertEquals(1, failuresLogged.size)
    }

    @Test
    fun attempt_givesNullAndLogs() = runBlocking {
        assertNull(quiet.attempt<Int>("parse") { throw IllegalStateException() })
        assertEquals(7, quiet.attempt("ok") { 7 })
        assertEquals(1, failuresLogged.size)
    }

    @Test
    fun flow_failureEndsTheFlowQuietly() = runBlocking {
        var failed = 0
        val got = flow { emit(1); emit(2); error("decode") }
            .reportFailures(quiet, "observe day", onFailure = { failed++ })
            .toList()
        assertEquals(listOf(1, 2), got)
        assertEquals(1, failed)
        assertEquals(listOf("failed: observe day IllegalStateException"), failuresLogged)
    }

    @Test
    fun manyLaunches_onlyTheBrokenOnesFail() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var failed = 0
        val lock = Any()
        val jobs = (1..200).map { i ->
            quiet.launch(scope, "job $i", onFailure = { synchronized(lock) { failed++ } }) { if (i % 10 == 0) error("x") else delay(1) }
        }
        jobs.joinAll()
        assertEquals(20, failed)
    }
}
