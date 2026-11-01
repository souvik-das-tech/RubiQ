package com.bbcl.rubiq

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

class SolverTest {
    companion object {
        private lateinit var asset: KociembaTables

        @BeforeClass
        @JvmStatic
        fun loadTables() {
            asset = KociembaTables.fromBytes(File("src/main/assets/kociemba_tables.bin").readBytes())
            setKociembaTables(asset)
        }
    }

    private fun checkSolves(puzzle: Puzzle, seeds: IntRange, solve: (String) -> SolveResult) {
        for (seed in seeds) {
            val start = puzzle.applyMoves(puzzle.solved, puzzle.scramble(Random(seed)))
            assertTrue("${puzzle.label} seed $seed: scramble must validate", puzzle.validate(start).valid)
            val result = solve(start)
            assertTrue("${puzzle.label} seed $seed: ${result.error}", result.success)
            val end = puzzle.applyMoves(start, result.moves.map { it.move })
            assertEquals("${puzzle.label} seed $seed: solution must solve", puzzle.solved, end)
        }
    }

    @Test
    fun computedTablesMatchAsset() {
        val t = KociembaTables.compute()
        assertArrayEquals(asset.twist, t.twist)
        assertArrayEquals(asset.flip, t.flip)
        assertArrayEquals(asset.frToBr, t.frToBr)
        assertArrayEquals(asset.urfToDlf, t.urfToDlf)
        assertArrayEquals(asset.urToDf, t.urToDf)
        assertArrayEquals(asset.urToUl, t.urToUl)
        assertArrayEquals(asset.ubToDf, t.ubToDf)
        assertArrayEquals(asset.mergeUrToDf, t.mergeUrToDf)
        assertArrayEquals(asset.sliceTwist, t.sliceTwist)
        assertArrayEquals(asset.sliceFlip, t.sliceFlip)
        assertArrayEquals(asset.sliceUrfToDlfParity, t.sliceUrfToDlfParity)
        assertArrayEquals(asset.sliceUrToDfParity, t.sliceUrToDfParity)
    }

    @Test
    fun solves3x3() {
        checkSolves(CubePuzzle(3), 1..10) { solve3x3(it) }
        // Kociemba solutions are short: 20-ish face turns, so well under 40 quarter steps.
        val start = applyMoves(solvedState(3), randomScramble(3, rng = Random(99)), 3)
        assertTrue(solve3x3(start).moves.size <= 40)
    }

    @Test
    fun solves4x4() = checkSolves(CubePuzzle(4), 1..4) { solve4x4(it) }

    @Test
    fun solves5x5() = checkSolves(CubePuzzle(5), 1..2) { solve5x5(it) }

    @Test
    fun solvesPyraminx() = checkSolves(PyraminxPuzzle, 1..10) { solvePyraminx(it) }

    @Test
    fun solvesSkewb() = checkSolves(SkewbPuzzle, 1..10) { solveSkewb(it) }

    @Test
    fun simplifiesMoves() {
        assertEquals(listOf("R2"), simplifyMoves(listOf("R", "R"), 3))
        assertEquals(listOf("L"), simplifyMoves(listOf("R", "L", "R'"), 3))
        assertEquals(emptyList<String>(), simplifyMoves(listOf("U", "U'"), 3))
    }

    @Test
    fun detectsAndFixesTwistedCorner() {
        val scrambled = applyMoves(solvedState(3), randomScramble(3, rng = Random(7)), 3)
        val slots = getPieceSlots(3)
        val (a, b, c) = slots.corners[0]
        val arr = scrambled.toCharArray()
        val t = arr[a]
        arr[a] = arr[b]
        arr[b] = arr[c]
        arr[c] = t
        val twisted = String(arr)
        val v = validateCube(twisted, 3)
        assertEquals(ValidationError.CORNER_TWIST, v.errorType)
        assertTrue(v.canAutoFix)
        assertFalse(solve3x3(twisted).success)
        val fixed = solve3x3(twisted, allowAutoFix = true)
        assertTrue(fixed.success)
        assertTrue(fixed.fixed)
        assertEquals(solvedState(3), applyMoves(fixed.startState!!, fixed.moves.map { it.move }, 3))
    }

    @Test
    fun detectsFlippedWingAndSwappedSkewbCentres() {
        // A wing flipped in place reads exactly like its partner wing of the same edge.
        val slots = getPieceSlots(4)
        val w = slots.edges[0]
        val arr = solvedState(4).toCharArray()
        arr[w[0]] = arr[w[1]].also { arr[w[1]] = arr[w[0]] }
        assertEquals(ValidationError.EDGE_DUPLICATE, validateCube(String(arr), 4).errorType)

        val g = skewbGeometry
        val c = skewbSlots.centers
        val s = g.solvedState.toCharArray()
        s[c[0].stickers[0]] = s[c[1].stickers[0]].also { s[c[1].stickers[0]] = s[c[0].stickers[0]] }
        assertEquals(PolyValidationError.CENTER_SWAP, validateSkewb(String(s)).errorType)
        assertTrue(solveSkewb(String(s), allowAutoFix = true).success)
    }

    @Test
    fun blankStatesAreIncomplete() {
        for (p in Puzzle.all) {
            assertTrue(p.validate(p.solved).valid)
            assertEquals(ValidationError.INCOMPLETE, p.validate(p.blank).errorType)
        }
    }
}
