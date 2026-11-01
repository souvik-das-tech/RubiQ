// Kociemba two-phase search on top of the cubejs-derived move/pruning tables.
//
// Unlike a plain two-phase solver (which returns the first solution it finds), this search
// keeps looking for shorter solutions until a time limit. It minimises the number of face
// turns, and among equally short solutions prefers the one with the fewest 90° steps
// (a half turn is shown to the user as two 90° steps).
package com.bbcl.rubiq

@Volatile
private var tables: KociembaTables? = null

/** Installs the tables (loaded from the bundled asset, or computed). */
fun setKociembaTables(t: KociembaTables) {
    tables = t
}

@Synchronized
private fun ensureTables(): KociembaTables = tables ?: KociembaTables.compute().also { tables = it }

private val moveFaceNames = listOf("U", "R", "F", "D", "L", "B")
private val powerNames = listOf("", "2", "'")
private fun moveName(m: Int) = moveFaceNames[m / 3] + powerNames[m % 3]
private fun quarterSteps(moves: List<Int>) = moves.sumOf { if (it % 3 == 1) 2 else 1 }

// Never turn the same face twice in a row, and turn opposite faces in a fixed order.
private fun allowedAfter(lastFace: Int, phase2: Boolean): IntArray {
    val out = mutableListOf<Int>()
    for (face in 0 until 6) {
        if (lastFace >= 0 && (face == lastFace || face == lastFace - 3)) continue
        val powers = if (!phase2 || face == 0 || face == 3) listOf(0, 1, 2) else listOf(1)
        for (p in powers) out.add(face * 3 + p)
    }
    return out.toIntArray()
}

private val next1 = (-1 until 6).map { allowedAfter(it, false) }
private val next2 = (-1 until 6).map { allowedAfter(it, true) }
private val phase2MoveSet = next2[0].toSet()

private class SearchTimeout : RuntimeException(null, null, false, false)

/**
 * Time-limited two-phase search for a cube in standard orientation.
 * `maxTotal` caps the solution length while no solution has been found yet.
 */
private class TwoPhaseSearch(val t: KociembaTables, facelets: String, val timeMs: Long, val maxTotal: Int) {
    private val cube = CubieCube.fromString(facelets)
    private val startFlip = cube.flip
    private val startTwist = cube.twist
    private val startFrToBr = cube.frToBr
    private val startSlice = startFrToBr / 24
    private val startParity = cube.cornerParity
    private val startUrfToDlf = cube.urfToDlf
    private val startUrToUl = cube.urToUl
    private val startUbToDf = cube.ubToDf

    private val startTime = System.nanoTime()
    private var nodes = 0
    var best: List<Int>? = null
        private set
    private var bestTurns = 1 shl 30
    private var bestSteps = 1 shl 30

    private val path1 = ArrayList<Int>()
    private val path2 = ArrayList<Int>()

    private fun tick() {
        if ((++nodes and 0x3ff) == 0 && best != null && (System.nanoTime() - startTime) / 1_000_000 > timeMs) {
            throw SearchTimeout()
        }
    }

    private fun minDist1(flip: Int, twist: Int, slice: Int): Int =
        maxOf(prune(t.sliceFlip, 495 * flip + slice), prune(t.sliceTwist, 495 * twist + slice))

    private fun minDist2(urfToDlf: Int, frToBr: Int, parity: Int, urToDf: Int): Int = maxOf(
        prune(t.sliceUrToDfParity, (24 * urToDf + frToBr) * 2 + parity),
        prune(t.sliceUrfToDlfParity, (24 * urfToDlf + frToBr) * 2 + parity),
    )

    private fun phase2(urfToDlf: Int, frToBr: Int, parity: Int, urToDf: Int, lastFace: Int, budget: Int): Boolean {
        tick()
        val d = minDist2(urfToDlf, frToBr, parity, urToDf)
        if (d == 0) return budget == 0
        if (d > budget) return false
        for (m in next2[lastFace + 1]) {
            path2.add(m)
            if (phase2(
                    t.urfToDlf[urfToDlf * 18 + m].toInt(),
                    t.frToBr[frToBr * 18 + m].toInt(),
                    parityAfter(parity, m),
                    t.urToDf[urToDf * 18 + m],
                    m / 3,
                    budget - 1,
                )
            ) {
                return true
            }
            path2.removeAt(path2.size - 1)
        }
        return false
    }

    private fun startPhase2(cost1: Int) {
        var a = startUrfToDlf
        var b = startFrToBr
        var p = startParity
        var u1 = startUrToUl
        var u2 = startUbToDf
        for (m in path1) {
            a = t.urfToDlf[a * 18 + m].toInt()
            b = t.frToBr[b * 18 + m].toInt()
            p = parityAfter(p, m)
            u1 = t.urToUl[u1 * 18 + m].toInt()
            u2 = t.ubToDf[u2 * 18 + m].toInt()
        }
        val urToDf = t.mergeUrToDf[u1 * N_MERGE + u2].toInt()
        val lastFace = if (path1.isNotEmpty()) path1.last() / 3 else -1
        // Equal-length solutions are still explored: they may need fewer 90° steps.
        val limit = if (best == null) maxTotal - cost1 else minOf(bestSteps - 1, bestTurns) - cost1
        var budget = minDist2(a, b, p, urToDf)
        while (budget <= limit) {
            path2.clear()
            if (phase2(a, b, p, urToDf, lastFace, budget)) {
                val candidate = path1 + path2
                val steps = quarterSteps(candidate)
                if (steps < bestSteps || (steps == bestSteps && candidate.size < bestTurns)) {
                    best = candidate
                    bestTurns = candidate.size
                    bestSteps = steps
                }
                return
            }
            budget++
        }
    }

    private fun phase1(flip: Int, twist: Int, slice: Int, lastFace: Int, budget: Int, cost1: Int) {
        tick()
        val d = minDist1(flip, twist, slice)
        if (budget == 0) {
            if (d == 0 && (path1.isEmpty() || path1.last() !in phase2MoveSet)) startPhase2(cost1)
            return
        }
        if (d > budget) return
        for (m in next1[lastFace + 1]) {
            path1.add(m)
            phase1(
                t.flip[flip * 18 + m].toInt(),
                t.twist[twist * 18 + m].toInt(),
                t.frToBr[slice * 24 * 18 + m] / 24,
                m / 3,
                budget - 1,
                cost1 + 1,
            )
            path1.removeAt(path1.size - 1)
        }
    }

    fun run(): List<Int>? {
        try {
            var cost1 = 0
            while (cost1 <= minOf(bestSteps - 1, bestTurns) && cost1 <= 20) {
                path1.clear()
                phase1(startFlip, startTwist, startSlice, -1, cost1, 0)
                cost1++
            }
        } catch (_: SearchTimeout) {
            // Keep the best solution found so far.
        }
        return best
    }
}

private fun search(t: KociembaTables, facelets: String, timeMs: Long, maxTotal: Int = 24): List<Int>? =
    TwoPhaseSearch(t, facelets, timeMs, maxTotal).run()

// ---- Orientations ----
// The two-phase method treats one axis specially, so the same cube searched with a
// different axis "up" often yields a shorter solution. A rotated view of the cube is
// solved and its moves are translated back to the real faces.

private val rotations = listOf("", "x", "z")

private class Orientation(
    val rotate: (String) -> String,
    /** Real move for each move index (0..17) of the rotated view. */
    val toReal: List<String>,
)

private val orientations: List<Orientation> by lazy {
    fun rotateBy(rot: String): (String) -> String = { state ->
        if (rot.isEmpty()) {
            state
        } else {
            val turned = applyMove(state, rot, 3)
            // Recolour so the rotated view has standard centres again.
            val colorToFace = HashMap<Char, Char>()
            for (f in 0 until 6) colorToFace[turned[f * 9 + 4]] = faceOrder[f]
            String(CharArray(turned.length) { colorToFace[turned[it]] ?: turned[it] })
        }
    }
    // Find, for every move of the rotated view, the real move with the same effect.
    val probe = applyMove(applyMove(applyMove(solvedState(3), "R", 3), "U", 3), "F'", 3)
    rotations.map { rot ->
        val rotate = rotateBy(rot)
        val viewed = rotate(probe)
        val toReal = List(18) { i ->
            val target = applyMove(viewed, moveName(i), 3)
            (0 until 18).firstOrNull { j -> rotate(applyMove(probe, moveName(j), 3)) == target }?.let(::moveName)
                ?: throw IllegalStateException("Orientation mapping failed")
        }
        Orientation(rotate, toReal)
    }
}

/**
 * Solves a *valid* 3x3 facelet string (centres in standard orientation) and returns
 * outer-layer move tokens, with as few 90° steps as the time limit allows.
 */
fun kociembaSolve(facelets: String, timeMs: Long = 900): List<String> {
    val t = ensureTables()
    if (facelets == solvedState(3)) return emptyList()

    var moves: List<String>? = null
    var steps = 1 shl 30
    for (o in orientations) {
        val found = search(t, o.rotate(facelets), timeMs / rotations.size) ?: continue
        val real = found.map { o.toReal[it] }
        val s = real.sumOf { if (it.endsWith("2")) 2 else 1 }
        if (s < steps) {
            steps = s
            moves = real
        }
    }
    // Practically unreachable: retry without the length cap.
    if (moves == null) moves = search(t, facelets, timeMs, maxTotal = 40)?.map(::moveName)

    // Double-check the answer instead of trusting the search blindly.
    if (moves == null || applyMoves(facelets, moves, 3) != solvedState(3)) {
        throw IllegalStateException("Kociemba solver returned an invalid solution")
    }
    return moves
}
