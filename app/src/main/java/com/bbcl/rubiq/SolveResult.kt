package com.bbcl.rubiq

class DetailedMove(val step: Int, val move: String, val explanation: String, val stage: String? = null)

class SolveStage(
    val stageNumber: Int,
    val title: String,
    val description: String,
    val moves: List<String>,
    val isParity: Boolean = false,
)

class SolveResult(
    val success: Boolean,
    val moves: List<DetailedMove> = emptyList(),
    val stages: List<SolveStage> = emptyList(),
    val solutionString: String = "",
    /** The state the solution starts from (differs from the input when auto-fixed). */
    val startState: String? = null,
    val alreadySolved: Boolean = false,
    val fixesApplied: List<String> = emptyList(),
    val errorType: String? = null,
    val error: String? = null,
    val validation: CubeValidation? = null,
) {
    val moveCount get() = moves.size
    val fixed get() = fixesApplied.isNotEmpty()

    companion object {
        fun failure(error: String, errorType: String? = null, validation: CubeValidation? = null) =
            SolveResult(success = false, errorType = errorType, error = error, validation = validation)
    }
}

/** The state to solve plus the fixes applied, or a failure result. */
class Prepared(val state: String, val fixes: List<String>, val failure: SolveResult?)

/** Shared validation/auto-fix preamble of the cube solvers. */
fun prepareForSolve(facelets: String, n: Int, allowAutoFix: Boolean): Prepared {
    var current = facelets
    var fixes = emptyList<String>()
    var validation = validateCube(current, n)
    if (!validation.valid) {
        if (allowAutoFix && validation.canAutoFix) {
            val fixed = autoFixCube(current, n)
                ?: return Prepared(
                    current,
                    fixes,
                    SolveResult.failure("This cube cannot be repaired automatically.", validation = validation),
                )
            current = fixed.state
            fixes = fixed.fixes
            validation = validateCube(current, n)
        }
        if (!validation.valid) {
            return Prepared(current, fixes, SolveResult.failure(validation.message, validation.errorType, validation))
        }
    }
    return Prepared(current, fixes, null)
}
