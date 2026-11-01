// App state and actions (painting, scrambling, solving, playback), mirroring the web app.
package com.bbcl.rubiq

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class BannerType { VALID, WARNING, ERROR, INFO }

class BannerConfig(
    val type: BannerType,
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val showFix: Boolean = false,
)

fun bannerFor(v: CubeValidation): BannerConfig {
    if (v.valid) return BannerConfig(BannerType.VALID, Icons.Filled.CheckCircle, v.title, v.message)
    if (v.errorType == ValidationError.INCOMPLETE) {
        return BannerConfig(BannerType.WARNING, Icons.Filled.Brush, v.title, v.message)
    }
    if (v.errorType == ValidationError.COUNT_MISMATCH) {
        return BannerConfig(BannerType.WARNING, Icons.Filled.Palette, v.title, v.message)
    }
    val icon = when (v.errorType) {
        ValidationError.CORNER_TWIST -> Icons.Filled.Sync
        ValidationError.EDGE_FLIP, ValidationError.WING_FLIP -> Icons.Filled.Repeat
        ValidationError.PERMUTATION_PARITY, PolyValidationError.CENTER_SWAP -> Icons.Filled.Shuffle
        else -> Icons.Rounded.WarningAmber
    }
    return BannerConfig(BannerType.ERROR, icon, v.title, v.message, showFix = v.canAutoFix)
}

class Solution(val moves: List<DetailedMove>, val stages: List<SolveStage>, val start: String)

enum class PanelTab { PAINT, SOLUTION, CHECKS }

enum class Haptic { SELECTION, LIGHT, MEDIUM }

class AppController : ViewModel() {
    val cubeView = CubeViewController()

    var puzzle by mutableStateOf(Puzzle.all.first())
        private set
    var facelets by mutableStateOf(puzzle.solved)
        private set
    var activeColor by mutableStateOf(puzzle.palette.first())
        private set
    var paintMode by mutableStateOf(true)
        private set
    var autoRotate by mutableStateOf(false)
        private set
    var showNet by mutableStateOf(false)
        private set
    var solving by mutableStateOf(false)
        private set
    var scrambling by mutableStateOf(false)
        private set
    var solution by mutableStateOf<Solution?>(null)
        private set
    var currentStep by mutableIntStateOf(0)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var speedMs by mutableIntStateOf(600)
        private set
    var notice by mutableStateOf<BannerConfig?>(null)
        private set
    var validation by mutableStateOf(puzzle.validate(facelets))
        private set

    /** Asks the UI to switch tabs. */
    val requestedTab = MutableSharedFlow<PanelTab>(extraBufferCapacity = 1)

    /** Bumped to ask the UI to celebrate. */
    var celebrations by mutableIntStateOf(0)
        private set

    /** Set by the activity while it is alive. */
    var haptics: ((Haptic) -> Unit)? = null

    private var busy = false
    private var loopRunning = false

    val locked get() = scrambling || solving
    val banner get() = notice ?: bannerFor(validation)
    val highlight: List<Int>
        get() = if (solution != null || validation.valid || validation.errorType == ValidationError.INCOMPLETE) {
            emptyList()
        } else {
            validation.highlight
        }

    val moves get() = solution?.moves ?: emptyList()
    val currentMove get() = if (currentStep > 0 && currentStep <= moves.size) moves[currentStep - 1] else null
    val nextMove get() = if (currentStep < moves.size) moves[currentStep] else null
    val finished get() = moves.isNotEmpty() && currentStep == moves.size
    val solutionText get() = moves.joinToString(" ") { it.move }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // ============== Core helpers ==============

    private fun setCube(next: String) {
        facelets = next
        validation = puzzle.validate(next)
    }

    private fun stopPlayback() {
        isPlaying = false
    }

    private fun clearSolution() {
        stopPlayback()
        solution = null
        currentStep = 0
    }

    /** Returns once the move being animated (if any) has finished. */
    private suspend fun waitIdle(): Boolean {
        var i = 0
        while (i < 150 && busy) {
            delay(20)
            i++
        }
        return !busy
    }

    /** Any manual change of the cube stops playback and invalidates the current solution. */
    private suspend fun editCube(update: (current: String) -> String?) {
        if (scrambling) return
        stopPlayback()
        if (!waitIdle()) return
        val next = update(facelets) ?: return
        clearSolution()
        notice = null
        setCube(next)
    }

    /** Animates one move and commits the resulting state. */
    private suspend fun runMove(token: String, durationMs: Int) {
        val next = puzzle.applyMove(facelets, token)
        cubeView.animateMove(token, durationMs, next)
        setCube(next)
    }

    // ============== Painting ==============

    fun pickColor(code: Char) {
        activeColor = code
    }

    fun paintSticker(index: Int) {
        if (!paintMode || scrambling) return
        if (puzzle.isFixed(index)) {
            notice = BannerConfig(
                BannerType.INFO,
                Icons.Outlined.Info,
                "Centres are fixed on a ${puzzle.label}",
                "${puzzle.holdHint}, then paint the other stickers to match.",
            )
            return
        }
        haptics?.invoke(Haptic.SELECTION)
        val color = activeColor
        launch {
            editCube { current ->
                if (current[index] == color) null else current.substring(0, index) + color + current.substring(index + 1)
            }
        }
    }

    fun togglePaintMode() {
        paintMode = !paintMode
    }

    fun toggleAutoRotate() {
        autoRotate = !autoRotate
    }

    fun toggleNet() {
        showNet = !showNet
    }

    fun dismissNotice() {
        notice = null
    }

    // ============== Mode / reset ==============

    fun switchPuzzle(kind: PuzzleKind) = launch {
        if (kind == puzzle.kind || scrambling || solving) return@launch
        stopPlayback()
        if (!waitIdle() || kind == puzzle.kind) return@launch
        clearSolution()
        notice = null
        puzzle = Puzzle.of(kind)
        if (activeColor !in puzzle.palette) activeColor = puzzle.palette.first()
        setCube(puzzle.solved)
    }

    fun resetToSolved() = launch { editCube { puzzle.solved } }

    fun resetToBlank() = launch { editCube { puzzle.blank } }

    fun scramble() = launch {
        if (scrambling || solving) return@launch
        stopPlayback()
        if (!waitIdle()) return@launch
        clearSolution()
        notice = null
        // A scramble only makes sense from a fully painted cube.
        if ('X' in facelets) setCube(puzzle.solved)
        val seq = puzzle.scramble()
        scrambling = true
        busy = true
        try {
            for (m in seq) runMove(m, puzzle.scrambleMoveMs)
        } finally {
            busy = false
            scrambling = false
        }
        notice = BannerConfig(BannerType.INFO, Icons.Filled.Shuffle, "Scrambled with ${seq.size} random moves", seq.joinToString(" "))
    }

    // ============== Playback ==============

    private suspend fun stepForward(): Boolean {
        val sol = solution
        if (sol == null || busy || currentStep >= sol.moves.size) return false
        busy = true
        try {
            val move = sol.moves[currentStep].move
            currentStep++
            runMove(move, (speedMs * 0.7).roundToInt())
        } finally {
            busy = false
        }
        return true
    }

    /** Next button: pauses playback, then steps. */
    fun nextStep() = launch {
        stopPlayback()
        stepForward()
    }

    fun stepBackward() = launch {
        stopPlayback()
        if (!waitIdle()) return@launch
        val sol = solution
        if (sol == null || currentStep <= 0) return@launch
        busy = true
        try {
            val move = sol.moves[currentStep - 1].move
            currentStep--
            runMove(invertMove(move), (speedMs * 0.7).roundToInt())
        } finally {
            busy = false
        }
    }

    /** Jumps instantly to the state after `step` moves of the solution. */
    fun jumpTo(step: Int) = launch { jumpToNow(step) }

    private suspend fun jumpToNow(step: Int) {
        stopPlayback()
        if (!waitIdle()) return
        val sol = solution ?: return
        val target = step.coerceIn(0, sol.moves.size)
        currentStep = target
        setCube(puzzle.applyMoves(sol.start, sol.moves.take(target).map { it.move }))
    }

    private suspend fun startPlayback() {
        val sol = solution
        if (sol == null || sol.moves.isEmpty()) return
        if (currentStep >= sol.moves.size) jumpToNow(0)
        if (solution !== sol) return
        isPlaying = true
        if (loopRunning) return // the running loop simply continues
        loopRunning = true
        try {
            while (isPlaying && solution === sol && currentStep < sol.moves.size) {
                val moved = stepForward()
                if (!moved) {
                    delay(50)
                    continue
                }
                if (currentStep < sol.moves.size) delay((speedMs * 0.3).roundToInt().toLong())
            }
        } finally {
            loopRunning = false
        }
        if (solution === sol && currentStep >= sol.moves.size) {
            stopPlayback()
            notice = BannerConfig(
                BannerType.VALID,
                Icons.Filled.EmojiEvents,
                "${if (puzzle is CubePuzzle) "Cube" else puzzle.label} solved!",
                "Finished all ${sol.moves.size} moves. Scramble again or paint a new one.",
            )
            celebrations++
            haptics?.invoke(Haptic.MEDIUM)
        }
    }

    fun togglePlayback() {
        if (isPlaying) stopPlayback() else launch { startPlayback() }
    }

    fun setSpeed(ms: Int) {
        speedMs = ms
    }

    // ============== Solve ==============

    fun solve(allowAutoFix: Boolean = false) = launch {
        if (scrambling || solving) return@launch
        stopPlayback()
        if (!waitIdle()) return@launch
        val current = facelets
        val p = puzzle
        val v = p.validate(current)
        if (!v.valid && !(allowAutoFix && v.canAutoFix)) {
            val b = bannerFor(v)
            notice = BannerConfig(
                if (v.errorType == ValidationError.INCOMPLETE) BannerType.WARNING else BannerType.ERROR,
                b.icon,
                b.title,
                b.desc,
                showFix = b.showFix,
            )
            return@launch
        }

        clearSolution()
        solving = true
        val data = try {
            SolverService.solve(current, p.kind, allowAutoFix)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notice = BannerConfig(BannerType.ERROR, Icons.Outlined.ErrorOutline, "Solver failed", "$e")
            return@launch
        } finally {
            solving = false
        }

        // The cube may have changed (mode switch) while solving.
        if (facelets != current || puzzle !== p) return@launch

        if (!data.success) {
            notice = BannerConfig(
                BannerType.ERROR,
                Icons.Rounded.WarningAmber,
                data.validation?.title ?: "Could not solve this cube",
                data.error ?: "Unknown solver error.",
                showFix = !allowAutoFix && (data.validation?.canAutoFix ?: false),
            )
            return@launch
        }

        val start = data.startState ?: current
        if (start != current) setCube(start)
        val fixes = if (data.fixesApplied.isNotEmpty()) "Auto-fixed: ${data.fixesApplied.joinToString("; ")}. " else ""

        if (data.alreadySolved || data.moves.isEmpty()) {
            notice = BannerConfig(
                BannerType.VALID,
                Icons.Filled.CheckCircle,
                "Already solved",
                "${fixes}Nothing left to do — it is already solved.",
            )
            return@launch
        }

        val sol = Solution(data.moves, data.stages, start)
        solution = sol
        currentStep = 0
        notice = BannerConfig(
            BannerType.VALID,
            Icons.Filled.AutoAwesome,
            "Solution found: ${sol.moves.size} moves",
            "$fixes${p.holdHint}, then follow along step by step.",
        )
        requestedTab.tryEmit(PanelTab.SOLUTION)
        haptics?.invoke(Haptic.LIGHT)
        delay(400)
        if (solution === sol) startPlayback()
    }
}
