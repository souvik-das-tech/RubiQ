// The puzzles RubiQ can paint and solve, behind one interface used by the app state, the
// views and the solver service. Cubes use the NxN facelet model; the Pyraminx and Skewb
// use the cut-polyhedron model.
package com.bbcl.rubiq

import kotlin.random.Random

enum class PuzzleKind { CUBE3, CUBE4, CUBE5, PYRAMINX, SKEWB }

sealed class Puzzle {
    abstract val kind: PuzzleKind

    /** Short name for the puzzle picker, e.g. "3x3". */
    abstract val label: String

    /** Full name for the badge over the 3D view. */
    abstract val title: String

    /** Colour codes in the order shown in the palette. */
    abstract val palette: List<Char>
    abstract val perColor: Int
    abstract val solved: String
    abstract val blank: String

    /** Stickers the user may not repaint (fixed centres of odd cubes). */
    open fun isFixed(index: Int) = false

    abstract fun applyMove(state: String, token: String): String
    fun applyMoves(state: String, tokens: Iterable<String>): String {
        var s = state
        for (t in tokens) if (t.isNotEmpty()) s = applyMove(s, t)
        return s
    }

    abstract fun scramble(rng: Random = Random.Default): List<String>
    abstract val scrambleMoveMs: Int
    abstract fun validate(state: String): CubeValidation

    /** How to hold the puzzle while painting and following a solution. */
    abstract val holdHint: String

    /** One-line notation help under the solution. */
    abstract val notationHelp: String

    /** Rotation of one step, for "every step is one ... turn". */
    abstract val stepTurn: String

    companion object {
        val all: List<Puzzle> by lazy { listOf(CubePuzzle(3), CubePuzzle(4), CubePuzzle(5), PyraminxPuzzle, SkewbPuzzle) }

        fun of(kind: PuzzleKind) = all.first { it.kind == kind }
    }
}

class CubePuzzle(val n: Int) : Puzzle() {
    override val kind = when (n) { 3 -> PuzzleKind.CUBE3; 4 -> PuzzleKind.CUBE4; else -> PuzzleKind.CUBE5 }
    override val label = "${n}x$n"
    override val title = when (n) { 3 -> "3x3x3 Classic"; 4 -> "4x4x4 Revenge"; else -> "5x5x5 Professor" }
    override val palette = listOf('U', 'D', 'F', 'B', 'R', 'L')
    override val perColor = n * n
    override val solved = solvedState(n)
    override val blank = blankState(n)
    override fun isFixed(index: Int) = isFixedCenter(index, n)
    override fun applyMove(state: String, token: String) = applyMove(state, token, n)
    override fun scramble(rng: Random) = randomScramble(n, rng = rng)
    override val scrambleMoveMs = when (n) { 3 -> 110; 4 -> 80; else -> 60 }
    override fun validate(state: String) = validateCube(state, n)
    override val holdHint = "Hold your cube with White on top and Green in front"
    override val notationHelp =
        "R = right face clockwise, R' = counter-clockwise (as seen looking at that face)." +
            (if (n >= 4) " Rw = right two layers, 2R = only the inner slice next to R." else "") +
            (if (n >= 5) " 3R = the middle slice." else "")
    override val stepTurn = "90°"
}

/** Shared behaviour of the cut-polyhedron puzzles. */
sealed class PolyPuzzle : Puzzle() {
    abstract val geometry: PolyGeometry
    abstract val scrambleMoves: List<String>
    abstract val scrambleLength: Int
    abstract fun explain(token: String): String

    override val solved get() = geometry.solvedState
    override val blank get() = "X".repeat(geometry.stickers.size)
    override fun applyMove(state: String, token: String) = geometry.applyMove(state, token)
    override val stepTurn = "120°"

    override fun scramble(rng: Random): List<String> {
        val out = mutableListOf<String>()
        while (out.size < scrambleLength) {
            val m = scrambleMoves[rng.nextInt(scrambleMoves.size)]
            if (out.isNotEmpty() && out.last().replace("'", "") == m) continue
            out.add(if (rng.nextBoolean()) m else "$m'")
        }
        return out
    }
}

object PyraminxPuzzle : PolyPuzzle() {
    override val kind = PuzzleKind.PYRAMINX
    override val label = "Pyraminx"
    override val title = "Pyraminx"
    override val geometry get() = pyraminxGeometry
    override val palette get() = pyraminxPalette
    override val perColor = 9
    override val scrambleMoves = listOf("U", "L", "R", "B")
    override val scrambleLength = 11

    override fun scramble(rng: Random): List<String> {
        // WCA style: layer turns, then each tip turned at random.
        val layers = super.scramble(rng)
        val tips = listOf("u", "l", "r", "b").mapNotNull { t ->
            when (rng.nextInt(3)) {
                1 -> t
                2 -> "$t'"
                else -> null
            }
        }
        return layers + tips
    }

    override val scrambleMoveMs = 150
    override fun validate(state: String) = validatePyraminx(state)
    override fun explain(token: String) = pyraminxExplanation(token)
    override val holdHint = "Hold your Pyraminx with one face towards you and one tip pointing up"
    override val notationHelp =
        "U, L, R, B = turn the top, left, right or back corner with the layer below it 120° clockwise " +
            "(as seen looking at that tip); u, l, r, b = just the tip; ' = counter-clockwise."
}

object SkewbPuzzle : PolyPuzzle() {
    override val kind = PuzzleKind.SKEWB
    override val label = "Skewb"
    override val title = "Skewb"
    override val geometry get() = skewbGeometry
    override val palette get() = skewbPalette
    override val perColor = 5
    override val scrambleMoves = listOf("R", "U", "L", "B")
    override val scrambleLength = 11
    override val scrambleMoveMs = 150
    override fun validate(state: String) = validateSkewb(state)
    override fun explain(token: String) = skewbExplanation(token)
    override val holdHint = "Hold your Skewb the same way throughout — the top-front-right corner never moves"
    override val notationHelp =
        "R, U, L, B = turn the half around the bottom-right-back, top-left-back, bottom-left-front or " +
            "bottom-left-back corner 120° clockwise (as seen looking at that corner); ' = counter-clockwise."
}
