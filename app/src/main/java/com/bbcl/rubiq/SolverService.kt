// Runs the solvers on one long-lived background thread, so the UI never freezes. The
// Kociemba tables ship as an asset and are loaded once; the big-cube candidate tables and
// the Pyraminx/Skewb search tables are built right after start-up, while the user is
// still painting.
package com.bbcl.rubiq

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

fun solvePuzzle(facelets: String, kind: PuzzleKind, autoFix: Boolean): SolveResult = when (kind) {
    PuzzleKind.CUBE3 -> solve3x3(facelets, autoFix)
    PuzzleKind.CUBE4 -> solve4x4(facelets, autoFix)
    PuzzleKind.CUBE5 -> solve5x5(facelets, autoFix)
    PuzzleKind.PYRAMINX -> solvePyraminx(facelets, autoFix)
    PuzzleKind.SKEWB -> solveSkewb(facelets, autoFix)
}

object SolverService {
    // A single thread: requests that arrive during warm-up simply wait their turn. The
    // big stack leaves room for the recursive searches.
    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(null, r, "solver", 64L shl 20).apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var started = false

    /** Loads the tables and builds the search tables in the background (idempotent). */
    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val assets = context.applicationContext.assets
        scope.launch {
            // If the asset cannot be read, the tables are computed on the first 3x3 solve.
            runCatching { setKociembaTables(KociembaTables.fromBytes(assets.open("kociemba_tables.bin").use { it.readBytes() })) }
            warmUpPoly()
            warmUp4x4()
            warmUp5x5()
        }
    }

    suspend fun solve(facelets: String, kind: PuzzleKind, autoFix: Boolean = false): SolveResult =
        withContext(dispatcher) { solvePuzzle(facelets, kind, autoFix) }
}
