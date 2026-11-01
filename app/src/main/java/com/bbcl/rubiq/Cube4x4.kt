// 4x4 Rubik's Revenge solver (reduction method).
//
//   1. Centres – beam search over short parity-safe move sequences and pure centre
//                3-cycles until all six 2x2 centres are built.
//   2. Edges   – beam search that *pairs* the 24 edge wings into 12 dedges (they may end up
//                in any edge slot) with slice–flip–slice moves and pure wing 3-cycles.
//   3. Parity  – the reduced cube is read as a 3x3; if it has PLL parity, the parity
//                algorithm swaps two dedges.
//   4. 3x3     – the reduced cube is finished with the two-phase search.
//
// Every search minimises 90° steps (a half turn counts as two), because that is what the
// user follows. All stages are simulated on the facelet model and the result is verified.
package com.bbcl.rubiq

private const val N = 4
private const val FACELETS = 6 * N * N
private const val SETUP_DEPTH = 2

const val PLL_PARITY_ALGORITHM = "2R2 U2 2R2 Uw2 2R2 2U2"
const val OLL_PARITY_ALGORITHM = "2R2 B2 U2 2L U2 2R' U2 2R U2 F2 2R F2 2L' B2 2R2"

private val faces = listOf("U", "R", "F", "D", "L", "B")
private val outerMoves = faces.flatMap { listOf(it, "$it'", "${it}2") }
private val sliceMoves = faces.flatMap { listOf("2$it", "2$it'", "2${it}2") }
private val allMoves = outerMoves + sliceMoves

private class Tables4(val centers: Stage, val edges: Stage)

/** Builds the 4x4 candidate tables (about a second); called lazily on the first solve. */
fun warmUp4x4() {
    tables4
}

private val tables4: Tables4 by lazy {
    val slots = getPieceSlots(N)
    val home = slots.home

    // Centres: every centre facelet must show its face colour.
    val centerSet = CandidateSet(slots.centers)
    addCommutators(centerSet, centerCommutators, slots.centers.toSet(), SETUP_DEPTH, allMoves, N)
    // Short free sequences must keep the wing permutation even (an inner-slice quarter turn
    // is an odd wing permutation), so OLL parity can never appear later.
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

    // Edges: a dedge is paired when its two wings show the same colour on each of its faces.
    val byEdge = LinkedHashMap<String, MutableList<List<Int>>>()
    for (e in slots.edges) {
        val key = listOf(home[e[0]], home[e[1]]).sorted().joinToString("")
        byEdge.getOrPut(key) { mutableListOf() }.add(e)
    }
    // [aX, aY, bX, bY]: the two wings' facelets on face X and on face Y.
    val dedges = byEdge.map { (key, wings) ->
        fun onX(w: List<Int>) = if (home[w[0]] == key[0]) listOf(w[0], w[1]) else listOf(w[1], w[0])
        (onX(wings[0]) + onX(wings[1])).toIntArray()
    }
    val dedgeOf = IntArray(FACELETS) { -1 }
    for (i in dedges.indices) for (f in dedges[i]) dedgeOf[f] = i

    val wings = slots.edges.flatten()
    val edgeSet = CandidateSet(wings, dedgeOf)
    addCommutators(edgeSet, wingCommutators, wings.toSet(), SETUP_DEPTH, allMoves, N)
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
    fun paired(at: (Int) -> Char, d: IntArray) = at(d[0]) == at(d[2]) && at(d[1]) == at(d[3])
    val edges = Stage(
        dedges.size,
        edgeSet.values(),
        { s -> dedges.count { paired(s::get, it) } },
        { s, c ->
            for (d in c.touched) for (f in dedges[d]) scratch[f] = s[f]
            for (k in c.dst.indices) scratch[c.dst[k]] = s[c.src[k]]
            var delta = 0
            for (di in c.touched) {
                val d = dedges[di]
                if (paired({ scratch[it] }, d)) delta++
                if (paired(s::get, d)) delta--
            }
            delta
        },
        4.0,
    )

    Tables4(centers, edges)
}

// ---- Helpers ----

private fun wingParity(state: String) = permutationParity(readEdges(state, getPieceSlots(N)).map { it.target })

/** The reduction stages before the final 3x3 search. */
private fun reduce(start: String): MutableList<StageMoves> {
    val t = tables4
    var state = start
    val stages = mutableListOf<StageMoves>()

    // Stage 1: centres. An odd wing permutation (which would cause OLL parity) is fixed
    // up front with one inner-slice turn.
    val prep = if (wingParity(state) == 1) listOf("2R") else emptyList()
    state = applyMoves(state, prep, N)
    val centers = beamSolve(state, t.centers, N, width = 16, branch = 8)
    state = centers.state
    stages.add(
        StageMoves(
            "Stage 1: Centres",
            "Build all six 2x2 centre blocks (white, yellow, green, blue, red, orange).",
            prep + centers.moves,
        ),
    )

    // Stage 2: pair the edge wings.
    // Several finished pairings are compared, counting the PLL-parity fix some of them need.
    val parityCost = quarterSteps(splitMoves(PLL_PARITY_ALGORITHM))
    val edges = beamSolve(
        state,
        t.edges,
        N,
        width = 16,
        branch = 8,
        extraRounds = 5,
        finishCost = { s ->
            if (validateCube(reducedTo3x3(s, N), 3).errorType == ValidationError.PERMUTATION_PARITY) parityCost else 0
        },
    )
    state = edges.state
    stages.add(
        StageMoves(
            "Stage 2: Edge pairing",
            "Join the 24 edge wings into 12 complete edges without breaking the centres.",
            edges.moves,
        ),
    )

    // Stage 3: parity, if the reduced cube is not a legal 3x3.
    val v3 = validateCube(reducedTo3x3(state, N), 3)
    if (!v3.valid &&
        (v3.errorType == ValidationError.PERMUTATION_PARITY || v3.errorType == ValidationError.EDGE_FLIP)
    ) {
        val pll = v3.errorType == ValidationError.PERMUTATION_PARITY
        stages.add(
            StageMoves(
                "Stage 3: ${if (pll) "PLL" else "OLL"} parity",
                if (pll) {
                    "Swap two opposite edges (a 4x4-only case) so the cube can be finished like a 3x3."
                } else {
                    "Flip a single edge (a 4x4-only case) so the cube can be finished like a 3x3."
                },
                splitMoves(if (pll) PLL_PARITY_ALGORITHM else OLL_PARITY_ALGORITHM),
            ),
        )
    }
    return stages
}

fun solve4x4(facelets: String, allowAutoFix: Boolean = false): SolveResult {
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
