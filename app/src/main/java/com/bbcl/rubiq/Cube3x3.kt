// 3x3 solver: validation + auto-fix from the facelet model, solving via Kociemba.
package com.bbcl.rubiq

fun solve3x3(facelets: String, allowAutoFix: Boolean = false): SolveResult {
    val prep = prepareForSolve(facelets, 3, allowAutoFix)
    prep.failure?.let { return it }
    val current = prep.state

    if (current == solvedState(3)) {
        return SolveResult(success = true, startState = current, alreadySolved = true, fixesApplied = prep.fixes)
    }

    return try {
        val steps = toQuarterSteps(kociembaSolve(current))
        val moves = steps.mapIndexed { i, s -> DetailedMove(i + 1, s.move, quarterStepExplanation(s, 3)) }
        SolveResult(
            success = true,
            moves = moves,
            solutionString = steps.joinToString(" ") { it.move },
            startState = current,
            fixesApplied = prep.fixes,
        )
    } catch (err: Exception) {
        SolveResult.failure("Solving failed: ${err.message}", "SOLVER_EXCEPTION")
    }
}
