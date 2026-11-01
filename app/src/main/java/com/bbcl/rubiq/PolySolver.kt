// Optimal solvers for the Pyraminx and the Skewb.
//
// Both puzzles have at most 11 moves to solve (ignoring Pyraminx tips), so a meet-in-the-
// middle search finds a shortest solution: a table of every state within 6 moves of solved
// (built once), and a search of up to 5 moves from the scrambled state into that table.
// Colours are first renamed so the target is always the standard solved state, whatever
// way the user holds the puzzle.
package com.bbcl.rubiq

private const val FORWARD_DEPTH = 6
private const val BACKWARD_DEPTH = 5

private class MeetInMiddle(val g: PolyGeometry, val moves: List<String>) {
    val target = g.solvedState

    /** State -> index of the last move on a shortest path from `target` (-1 for target). */
    private val last = HashMap<String, Int>()
    private val depth = HashMap<String, Int>()

    init {
        last[target] = -1
        depth[target] = 0
        var frontier = listOf(target)
        for (d in 1..FORWARD_DEPTH) {
            val next = mutableListOf<String>()
            for (s in frontier) {
                for (m in moves.indices) {
                    val t = g.applyMove(s, moves[m])
                    if (last.containsKey(t)) continue
                    last[t] = m
                    depth[t] = d
                    next.add(t)
                }
            }
            frontier = next
        }
    }

    private fun inverse(m: String) = if (m.endsWith("'")) m.dropLast(1) else "$m'"

    /** A shortest move sequence taking `start` to `target`, or null if none has ≤ 11 moves. */
    fun solve(start: String): List<String>? {
        val back = hashMapOf(start to -1)
        var frontier = listOf(start)
        var meet: String? = null
        var best = 1 shl 30
        var k = 0
        while (k <= BACKWARD_DEPTH && k < best) {
            for (s in frontier) {
                val j = depth[s]
                if (j != null && k + j < best) {
                    best = k + j
                    meet = s
                }
            }
            if (k == BACKWARD_DEPTH || k + 1 >= best) break
            val next = mutableListOf<String>()
            for (s in frontier) {
                for (m in moves.indices) {
                    val t = g.applyMove(s, moves[m])
                    if (back.containsKey(t)) continue
                    back[t] = m
                    next.add(t)
                }
            }
            frontier = next
            k++
        }
        if (meet == null) return null

        // start -> meet, read backwards through the search tree.
        val first = mutableListOf<String>()
        var s: String = meet
        while (back.getValue(s) >= 0) {
            val m = moves[back.getValue(s)]
            first.add(m)
            s = g.applyMove(s, inverse(m))
        }
        // meet -> target: undo the forward path.
        val second = mutableListOf<String>()
        s = meet
        while (last.getValue(s) >= 0) {
            val inv = inverse(moves[last.getValue(s)])
            second.add(inv)
            s = g.applyMove(s, inv)
        }
        return first.asReversed() + second
    }
}

private val pyraSearch by lazy { MeetInMiddle(pyraminxGeometry, listOf("U", "U'", "L", "L'", "R", "R'", "B", "B'")) }
private val skewbSearch by lazy { MeetInMiddle(skewbGeometry, listOf("R", "R'", "U", "U'", "L", "L'", "B", "B'")) }

/** Builds the search tables (a fraction of a second each). */
fun warmUpPoly() {
    pyraSearch
    skewbSearch
}

/** Renames colours so that `target` becomes the standard solved colouring. */
private fun relabel(g: PolyGeometry, state: String, target: String): String {
    val solved = g.solvedState
    val map = HashMap<Char, Char>()
    for (i in target.indices) map[target[i]] = solved[i]
    return String(CharArray(state.length) { map.getValue(state[it]) })
}

private val pyraVertexNames = mapOf("U" to "top", "L" to "left", "R" to "right", "B" to "back")

fun pyraminxExplanation(token: String): String {
    val prime = token.endsWith("'")
    val letter = token.replace("'", "")
    val dir = if (prime) "counter-clockwise" else "clockwise"
    val name = pyraVertexNames[letter.uppercase()] ?: letter
    if (letter == letter.lowercase()) {
        return "Turn just the $name tip 120° $dir (as seen looking at that tip)"
    }
    return "Turn the $name corner with the layer below it 120° $dir (as seen looking at the $name tip)"
}

private val skewbCornerNames =
    mapOf("R" to "bottom-right-back", "U" to "top-left-back", "L" to "bottom-left-front", "B" to "bottom-left-back")

fun skewbExplanation(token: String): String {
    val prime = token.endsWith("'")
    val name = skewbCornerNames[token.replace("'", "")] ?: token
    return "Turn the half around the $name corner 120° ${if (prime) "counter-clockwise" else "clockwise"} (as seen looking at that corner)"
}

private fun preparePoly(
    facelets: String,
    allowAutoFix: Boolean,
    validate: (String) -> CubeValidation,
    autoFix: (String, MutableList<String>) -> String?,
): Prepared {
    var current = facelets
    val fixes = mutableListOf<String>()
    var v = validate(current)
    var attempt = 0
    while (attempt < 4 && !v.valid && allowAutoFix && v.canAutoFix) {
        current = autoFix(current, fixes) ?: break
        v = validate(current)
        attempt++
    }
    if (!v.valid) {
        return Prepared(current, fixes, SolveResult.failure(v.message, v.errorType, v))
    }
    return Prepared(current, fixes, null)
}

private class PolyStage(val title: String, val description: String, val moves: List<String>)

private fun polyResult(start: String, fixes: List<String>, stages: List<PolyStage>, explain: (String) -> String): SolveResult {
    val kept = stages.filter { it.moves.isNotEmpty() }
    val moves = mutableListOf<DetailedMove>()
    for (s in kept) {
        for (m in s.moves) moves.add(DetailedMove(moves.size + 1, m, explain(m), s.title))
    }
    return SolveResult(
        success = true,
        moves = moves,
        stages = if (kept.size > 1) kept.mapIndexed { i, s -> SolveStage(i + 1, s.title, s.description, s.moves) } else emptyList(),
        solutionString = moves.joinToString(" ") { it.move },
        startState = start,
        fixesApplied = fixes,
    )
}

fun solvePyraminx(facelets: String, allowAutoFix: Boolean = false): SolveResult {
    val prep = preparePoly(facelets, allowAutoFix, ::validatePyraminx, ::autoFixPyraminx)
    prep.failure?.let { return it }
    val start = prep.state
    val g = pyraminxGeometry
    var state = relabel(g, start, pyraminxTarget(start)!!)
    if (state == g.solvedState) {
        return SolveResult(success = true, startState = start, alreadySolved = true, fixesApplied = prep.fixes)
    }

    // Tips first: each tip only ever needs one turn to match the centre below it.
    val tips = mutableListOf<String>()
    for (v in listOf('U', 'L', 'R', 'B')) {
        val tip = pyraminxSlots.tips.getValue(v).stickers
        // Tip and centre stickers on the same face, in the same order.
        val center = tip.map { f ->
            pyraminxSlots.centers.getValue(v).stickers.first { g.stickers[it].face == g.stickers[f].face }
        }
        val lower = v.lowercaseChar()
        for (m in listOf("", "$lower", "$lower'")) {
            val s = if (m.isEmpty()) state else g.applyMove(state, m)
            if (tip.indices.all { s[tip[it]] == s[center[it]] }) {
                if (m.isNotEmpty()) tips.add(m)
                state = s
                break
            }
        }
    }
    val rest = pyraSearch.solve(state) ?: return SolveResult.failure("No solution found.", "SOLVER_EXCEPTION")
    return polyResult(
        start,
        prep.fixes,
        listOf(
            PolyStage("Stage 1: Tips", "Turn each tip to match the three colours of the centre below it.", tips),
            PolyStage("Stage 2: Solve the rest", "Solve the centres and edges in the fewest possible moves.", rest),
        ),
        ::pyraminxExplanation,
    )
}

fun solveSkewb(facelets: String, allowAutoFix: Boolean = false): SolveResult {
    val prep = preparePoly(facelets, allowAutoFix, ::validateSkewb, ::autoFixSkewb)
    prep.failure?.let { return it }
    val start = prep.state
    val g = skewbGeometry
    val state = relabel(g, start, skewbTarget(start)!!)
    if (state == g.solvedState) {
        return SolveResult(success = true, startState = start, alreadySolved = true, fixesApplied = prep.fixes)
    }
    val moves = skewbSearch.solve(state) ?: return SolveResult.failure("No solution found.", "SOLVER_EXCEPTION")
    return polyResult(start, prep.fixes, listOf(PolyStage("Solve", "Solve the Skewb in the fewest possible moves.", moves)), ::skewbExplanation)
}
