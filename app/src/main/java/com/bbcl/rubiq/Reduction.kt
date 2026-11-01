// Shared machinery of the big-cube reduction solvers (4x4, 5x5): candidate move sequences
// with their effect on the stage-relevant facelets, a beam search over them, and the final
// 3x3 stage with simplification and verification.
package com.bbcl.rubiq

import kotlin.math.sign

private val layerSuffixRe = Regex("(2'|2|')$")
private val layerHalfRe = Regex("2'?$")
fun layerKey(t: String) = t.replaceFirst(layerSuffixRe, "")
fun isSliceQuarter(t: String) = t.startsWith("2") && !layerHalfRe.containsMatchIn(t.substring(1))

/** 90° steps of a move sequence (a half turn counts as two). */
fun quarterSteps(moves: List<String>) = moves.sumOf { if (layerHalfRe.containsMatchIn(it.substring(1))) 2 else 1 }

/** Permutation of "first, then second" (gather form: next[i] = current[perm[i]]). */
fun compose(first: ShortArray, second: ShortArray): ShortArray = composePermutations(first, second)

fun sequencePerm(moves: List<String>, n: Int): ShortArray {
    var p: ShortArray? = null
    for (m in moves) p = if (p == null) movePermutation(m, n) else compose(p, movePermutation(m, n))
    return p!!
}

// ---- Candidate move sequences ----

class Candidate(
    val moves: List<String>,
    val cost: Int, // 90° steps
    val dst: IntArray, // stage-relevant facelets that change: next[dst[k]] = current[src[k]]
    val src: IntArray,
    val touched: IntArray, // stage units affected
)

/** Collects candidates, keeping only the cheapest sequence for each distinct effect. */
class CandidateSet(val relevant: List<Int>, val unitOf: IntArray? = null) {
    // Re-inserting an existing key keeps its original position, as in a JavaScript Map.
    private val best = LinkedHashMap<String, Candidate>()

    fun addPairs(dst: List<Int>, src: List<Int>, cost: Int, moves: () -> List<String>) {
        if (dst.isEmpty()) return
        val key = StringBuilder()
        for (k in dst.indices) key.append(dst[k]).append(':').append(src[k]).append(',')
        val k = key.toString()
        val prev = best[k]
        if (prev != null && prev.cost <= cost) return
        val u = unitOf
        val touched = if (u != null) dst.map { u[it] }.distinct().toIntArray() else IntArray(0)
        best[k] = Candidate(moves(), cost, dst.toIntArray(), src.toIntArray(), touched)
    }

    fun addPerm(perm: ShortArray, cost: Int, moves: () -> List<String>) {
        val dst = mutableListOf<Int>()
        val src = mutableListOf<Int>()
        for (i in relevant) {
            if (perm[i].toInt() != i) {
                dst.add(i)
                src.add(perm[i].toInt())
            }
        }
        addPairs(dst, src, cost, moves)
    }

    fun values(): List<Candidate> = best.values.toList()
}

/** Depth-first walk over move sequences, composing permutations incrementally. */
fun walkSequences(moveSet: List<String>, maxLen: Int, n: Int, visit: (seq: List<String>, perm: ShortArray) -> Unit) {
    val seq = ArrayList<String>()
    fun walk(perm: ShortArray?) {
        if (perm != null) visit(seq, perm)
        if (seq.size >= maxLen) return
        val last = seq.lastOrNull()
        for (m in moveSet) {
            if (last != null && layerKey(last) == layerKey(m)) continue
            seq.add(m)
            walk(if (perm != null) compose(perm, movePermutation(m, n)) else movePermutation(m, n))
            seq.removeAt(seq.size - 1)
        }
    }
    walk(null)
}

private class Base(val moves: List<String>, val dst: List<Int>, val src: List<Int>, val cost: Int)

/** S C S' for every base commutator C and every setup S of up to `depth` moves of `setupMoves`. */
fun addCommutators(
    set: CandidateSet,
    commutators: List<String>,
    relevant: Set<Int>,
    depth: Int,
    setupMoves: List<String>,
    n: Int,
) {
    val bases = commutators.flatMap { c ->
        listOf(splitMoves(c), invertMoves(splitMoves(c))).map { moves ->
            val perm = sequencePerm(moves, n)
            val dst = mutableListOf<Int>()
            val src = mutableListOf<Int>()
            for (i in perm.indices) {
                if (perm[i].toInt() == i) continue
                if (i !in relevant) throw IllegalStateException("Commutator is not pure")
                dst.add(i)
                src.add(perm[i].toInt())
            }
            Base(moves, dst, src, quarterSteps(moves))
        }
    }
    fun visit(setup: List<String>, g: ShortArray) {
        // For T = S C S', next[g[d]] = current[g[s]] for each change (d, s) of C.
        val setupCost = 2 * quarterSteps(setup)
        for (b in bases) {
            set.addPairs(
                b.dst.map { g[it].toInt() },
                b.src.map { g[it].toInt() },
                b.cost + setupCost,
            ) { setup + b.moves + invertMoves(setup) }
        }
    }

    visit(emptyList(), ShortArray(6 * n * n) { it.toShort() })
    walkSequences(setupMoves, depth, n) { setup, g -> visit(setup.toList(), g) }
}

// ---- Stage definitions ----

class Stage(
    val total: Int,
    val candidates: List<Candidate>,
    val score: (String) -> Int,
    /** Change in score if candidate `c` were applied to state `s`. */
    val delta: (String, Candidate) -> Int,
    /** Rough cost (90° steps) per remaining unsolved unit, used to rank partial solutions. */
    val costPerUnit: Double,
)

// ---- Beam search ----

private class Node(val state: String, val moves: List<String>, val cost: Int, val score: Int)

private class Pick(val c: Candidate, val gain: Int, val value: Double)

class BeamResult(val state: String, val moves: List<String>)

/**
 * Beam search: keeps the `width` most promising partial solutions and extends each with its
 * `branch` best-value candidates (score gained per 90° step). When a state has no improving
 * candidate it may take one neutral or slightly worse candidate, which resolves positions
 * (like the last two edges) that need two algorithms in a row.
 */
fun beamSolve(
    start: String,
    stage: Stage,
    n: Int,
    width: Int = 8,
    branch: Int = 6,
    extraRounds: Int = 0,
    maxDepth: Int = 80,
    finishCost: ((String) -> Int)? = null,
): BeamResult {
    var beam = listOf(Node(start, emptyList(), 0, stage.score(start)))
    var best: Node? = null
    var bestTotal = Double.POSITIVE_INFINITY
    var roundsLeft: Int? = null // null: unlimited until the first complete solution
    fun consider(node: Node) {
        val total = node.cost + (finishCost?.invoke(node.state) ?: 0)
        if (total < bestTotal) {
            best = node
            bestTotal = total.toDouble()
        }
        if (roundsLeft == null) roundsLeft = extraRounds
    }

    if (beam[0].score == stage.total) consider(beam[0])
    val seen = HashMap<String, Int>()

    var depth = 0
    while (depth < maxDepth && beam.isNotEmpty()) {
        depth++
        val left = roundsLeft
        if (left != null) {
            if (left <= 0) break
            roundsLeft = left - 1
        }
        val children = mutableListOf<Node>()
        for (node in beam) {
            val top = ArrayList<Pick>()
            fun offer(c: Candidate, gain: Int, value: Double) {
                if (top.size < branch) {
                    top.add(Pick(c, gain, value))
                } else if (value > top.last().value) {
                    top[top.size - 1] = Pick(c, gain, value)
                } else {
                    return
                }
                // Stable, like the reference's insertion sort.
                top.sortWith { a, b -> sign(b.value - a.value).toInt() }
            }

            for (c in stage.candidates) {
                val gain = stage.delta(node.state, c)
                if (gain > 0) offer(c, gain, gain.toDouble() / c.cost)
            }
            if (top.isEmpty()) {
                // Stuck: allow sidesteps, preferring cheap ones that keep the score.
                for (c in stage.candidates) {
                    val gain = stage.delta(node.state, c)
                    if (gain >= -1) offer(c, gain, gain - c.cost / 100.0)
                }
            }
            for (p in top) {
                val state = applyMoves(node.state, p.c.moves, n)
                val cost = node.cost + p.c.cost
                val prev = seen[state]
                if (prev != null && prev <= cost) continue
                seen[state] = cost
                children.add(Node(state, node.moves + p.c.moves, cost, node.score + p.gain))
            }
        }
        for (ch in children) if (ch.score == stage.total) consider(ch)
        fun rank(node: Node) = node.cost + stage.costPerUnit * (stage.total - node.score)
        val next = children.filter { it.score < stage.total && it.cost < bestTotal }.toMutableList()
        next.sortWith { a, b -> sign(rank(a) - rank(b)).toInt() }
        beam = if (next.size > width) next.subList(0, width) else next
    }
    val found = best ?: throw IllegalStateException("Solver could not finish a stage")
    return BeamResult(found.state, found.moves)
}

// ---- Final stage and result ----

class StageMoves(val title: String, val description: String, val moves: List<String>)

/** Reads a reduced NxN cube (centres built, edges paired) as a 3x3 facelet string. */
fun reducedTo3x3(state: String, n: Int): String {
    val map = intArrayOf(0, n / 2, n - 1)
    val out = StringBuilder(54)
    for (f in 0 until 6) {
        for (r in 0 until 3) {
            for (c in 0 until 3) out.append(state[f * n * n + map[r] * n + map[c]])
        }
    }
    return out.toString()
}

/**
 * Appends the 3x3 stage to the reduction `stages`, verifies the whole solution, and builds
 * the result with every step a single 90° turn.
 */
fun finishReduction(start: String, stages: MutableList<StageMoves>, fixes: List<String>, n: Int): SolveResult {
    var state = applyMoves(start, stages.flatMap { it.moves }, n)
    val v3 = validateCube(reducedTo3x3(state, n), 3)
    if (!v3.valid) throw IllegalStateException("Reduction produced an illegal 3x3 (${v3.errorType})")

    // Final stage: solve as a 3x3.
    val finish = kociembaSolve(reducedTo3x3(state, n), timeMs = 700)
    state = applyMoves(state, finish, n)
    stages.add(
        StageMoves(
            "Stage ${stages.size + 1}: Solve as a 3x3",
            "Finish with outer-layer turns only, treating the paired edges and centres like a 3x3.",
            finish,
        ),
    )

    if (state != solvedState(n)) throw IllegalStateException("Verification failed")

    // Simplify each stage, then split half turns so every step is a single 90° turn.
    val simplified = stages
        .map { it to toQuarterSteps(simplifyMoves(it.moves, n)) }
        .filter { it.second.isNotEmpty() }
    val allMoves = simplified.flatMap { (_, steps) -> steps.map { it.move } }
    if (applyMoves(start, allMoves, n) != solvedState(n)) {
        throw IllegalStateException("Verification failed after simplification")
    }

    val resultStages = simplified.mapIndexed { i, (stage, steps) ->
        SolveStage(i + 1, stage.title, stage.description, steps.map { it.move }, isParity = "parity" in stage.title)
    }
    val moves = mutableListOf<DetailedMove>()
    for ((stage, steps) in simplified) {
        for (q in steps) moves.add(DetailedMove(moves.size + 1, q.move, quarterStepExplanation(q, n), stage.title))
    }
    return SolveResult(
        success = true,
        moves = moves,
        stages = resultStages,
        solutionString = allMoves.joinToString(" "),
        startState = start,
        fixesApplied = fixes,
    )
}
