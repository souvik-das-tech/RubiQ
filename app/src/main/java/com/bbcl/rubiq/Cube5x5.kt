// 5x5 Professor's Cube solver (reduction method).
//
//   0. Parity  – an odd wing permutation (the 5x5 "edge parity") is fixed up front with one
//                inner-slice turn; every later stage keeps the wing permutation even.
//   1. Centres – beam search over short parity-safe move sequences and pure x-centre and
//                t-centre 3-cycles until all six 3x3 centres are built.
//   2. Edges   – beam search that brings each edge wing next to its middle edge, with
//                slice–flip–slice moves and pure wing 3-cycles.
//   3. 3x3     – corners, middle edges and centres now form a 3x3, finished with the
//                two-phase search.
//
// Middle slices (3R, ...) only appear inside commutators, which undo them, so the fixed
// centres never move and the reduced cube is always a legal 3x3.
package com.bbcl.rubiq

private const val N = 5
private const val FACELETS = 6 * N * N
private const val SETUP_DEPTH = 2

private val faces = listOf("U", "R", "F", "D", "L", "B")
private val outerMoves = faces.flatMap { listOf(it, "$it'", "${it}2") }
private val sliceMoves = faces.flatMap { listOf("2$it", "2$it'", "2${it}2") }
private val allMoves = outerMoves + sliceMoves

private class Tables5(val centers: Stage, val edges: Stage)

/** Builds the 5x5 candidate tables (a couple of seconds); called lazily on the first solve. */
fun warmUp5x5() {
    tables5
}

private val tables5: Tables5 by lazy {
    val slots = getPieceSlots(N)
    val home = slots.home

    // Centres: every x- and t-centre facelet must show its face colour.
    val centerSet = CandidateSet(slots.centers)
    val centerFacelets = slots.centers.toSet()
    addCommutators(centerSet, xCenterCommutators5, centerFacelets, SETUP_DEPTH, allMoves, N)
    addCommutators(centerSet, tCenterCommutators5, centerFacelets, SETUP_DEPTH, allMoves, N)
    // Short free sequences must keep the wing permutation even (an inner-slice quarter turn
    // is an odd wing permutation), so edge parity can never appear later.
    walkSequences(allMoves, 3, N) { seq, perm ->
        if (seq.count(::isSliceQuarter) % 2 != 0) return@walkSequences
        centerSet.addPerm(perm, quarterSteps(seq)) { seq.toList() }
    }
    val centers = Stage(
        slots.centers.size,
        centerSet.values(),
        { s -> slots.centers.count { s[it] == home[it] } },
        { s, c ->
            var d = 0
            for (k in c.dst.indices) {
                val i = c.dst[k]
                d += (if (s[c.src[k]] == home[i]) 1 else 0) - (if (s[i] == home[i]) 1 else 0)
            }
            d
        },
        2.2,
    )

    // Edges: a wing is paired when it shows the colours of the middle edge in its slot.
    // [mX, mY, w1X, w1Y, w2X, w2Y]: middle edge and wing facelets on face X and on face Y.
    val stickers = getGeometry(N).stickers
    val tredges = slots.midges.map { m ->
        // The two wings of an edge share its two faces and lie on the same edge line.
        val mx = stickers[m[0]]
        val my = stickers[m[1]]
        val wings = mutableListOf<Int>()
        for (w in slots.edges) {
            for ((a, b) in listOf(w[0] to w[1], w[1] to w[0])) {
                val sa = stickers[a]
                val sb = stickers[b]
                if (sa.face == mx.face && sb.face == my.face && sameEdge(sa.grid, mx.grid)) {
                    wings.add(a)
                    wings.add(b)
                }
            }
        }
        if (wings.size != 4) throw IllegalStateException("5x5 edge analysis failed")
        (listOf(m[0], m[1]) + wings).toIntArray()
    }
    val unitOf = IntArray(FACELETS) { -1 }
    for (i in tredges.indices) for (f in tredges[i]) unitOf[f] = i
    fun paired(at: (Int) -> Char, t: IntArray) =
        (if (at(t[2]) == at(t[0]) && at(t[3]) == at(t[1])) 1 else 0) +
            (if (at(t[4]) == at(t[0]) && at(t[5]) == at(t[1])) 1 else 0)

    val edgeFacelets = tredges.flatMap { it.toList() }
    val wingFacelets = slots.edges.flatten().toSet()
    val edgeSet = CandidateSet(edgeFacelets, unitOf)
    addCommutators(edgeSet, wingCommutators5, wingFacelets, SETUP_DEPTH, allMoves, N)
    // Slice – flip – slice: an inner slice turn, outer turns, and the slice turned back.
    // Kept only when the centres come back intact.
    fun centersIntact(perm: ShortArray) = slots.centers.all { home[perm[it].toInt()] == home[it] }
    for (s in sliceMoves) {
        val sPerm = movePermutation(s, N)
        val sBack = movePermutation(invertMove(s), N)
        val sCost = 2 * quarterSteps(listOf(s))
        walkSequences(outerMoves, 3, N) { x, xPerm ->
            val perm = compose(compose(sPerm, xPerm), sBack)
            if (!centersIntact(perm)) return@walkSequences
            edgeSet.addPerm(perm, quarterSteps(x) + sCost) { listOf(s) + x + invertMove(s) }
        }
    }
    // Outer turns alone never pair anything, but they set up the next pairing move.
    walkSequences(outerMoves, 1, N) { x, perm -> edgeSet.addPerm(perm, quarterSteps(x)) { x.toList() } }

    // Only the solver thread runs the stage callbacks, so one scratch buffer is enough.
    val scratch = CharArray(FACELETS)
    val edges = Stage(
        2 * tredges.size,
        edgeSet.values(),
        { s -> tredges.sumOf { paired(s::get, it) } },
        { s, c ->
            for (u in c.touched) for (f in tredges[u]) scratch[f] = s[f]
            for (k in c.dst.indices) scratch[c.dst[k]] = s[c.src[k]]
            var delta = 0
            for (u in c.touched) delta += paired({ scratch[it] }, tredges[u]) - paired(s::get, tredges[u])
            delta
        },
        2.0,
    )

    Tables5(centers, edges)
}

/** Whether two edge cubies lie on the same edge of the cube (they differ in one coordinate). */
private fun sameEdge(a: Vec3, b: Vec3) = (0 until 3).count { a[it] != b[it] } == 1

private fun wingParity(state: String) = permutationParity(readEdges(state, getPieceSlots(N)).map { it.target })

private fun reduce(start: String): MutableList<StageMoves> {
    val t = tables5
    var state = start
    val stages = mutableListOf<StageMoves>()

    // Stage 1: centres. An odd wing permutation (which would cause edge parity) is fixed up
    // front with one inner-slice turn.
    val prep = if (wingParity(state) == 1) listOf("2R") else emptyList()
    state = applyMoves(state, prep, N)
    val centers = beamSolve(state, t.centers, N, width = 12, branch = 6, maxDepth = 120)
    state = centers.state
    stages.add(
        StageMoves(
            "Stage 1: Centres",
            "Build all six 3x3 centres around the fixed middle stickers (white, yellow, green, blue, red, orange).",
            prep + centers.moves,
        ),
    )

    // Stage 2: bring every edge wing next to its matching middle edge.
    val edges = beamSolve(state, t.edges, N, width = 12, branch = 6, maxDepth = 120)
    stages.add(
        StageMoves(
            "Stage 2: Edge pairing",
            "Join each pair of edge wings to its middle edge, making 12 complete edges without breaking the centres.",
            edges.moves,
        ),
    )
    return stages
}

fun solve5x5(facelets: String, allowAutoFix: Boolean = false): SolveResult {
    val prep = prepareForSolve(facelets, N, allowAutoFix)
    prep.failure?.let { return it }
    val start = prep.state

    if (start == solvedState(N)) {
        return SolveResult(success = true, startState = start, alreadySolved = true, fixesApplied = prep.fixes)
    }

    return try {
        finishReduction(start, reduce(start), prep.fixes, N)
    } catch (err: Exception) {
        SolveResult.failure("Solving failed: ${err.message}", "SOLVER_EXCEPTION")
    }
}
