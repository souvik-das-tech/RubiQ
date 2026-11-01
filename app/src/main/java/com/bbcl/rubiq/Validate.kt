// Physical-legality checks for 3x3, 4x4 and 5x5 facelet strings. Cheap enough to run on every
// sticker tap while painting, and again before solving.
package com.bbcl.rubiq

enum class CheckState { OK, ERROR, PENDING }

data class CubeChecks(val cornerTwist: CheckState, val edgeFlip: CheckState, val permutation: CheckState) {
    companion object {
        val PENDING = CubeChecks(CheckState.PENDING, CheckState.PENDING, CheckState.PENDING)
    }
}

/** Error codes, spelled like the TypeScript `ValidationErrorType`. */
object ValidationError {
    const val INVALID_INPUT = "INVALID_INPUT"
    const val INCOMPLETE = "INCOMPLETE"
    const val COUNT_MISMATCH = "COUNT_MISMATCH"
    const val CENTER_MISMATCH = "CENTER_MISMATCH"
    const val CORNER_INVALID = "CORNER_INVALID"
    const val CORNER_DUPLICATE = "CORNER_DUPLICATE"
    const val EDGE_INVALID = "EDGE_INVALID"
    const val EDGE_DUPLICATE = "EDGE_DUPLICATE"
    const val WING_FLIP = "WING_FLIP"
    const val CORNER_TWIST = "CORNER_TWIST"
    const val EDGE_FLIP = "EDGE_FLIP"
    const val PERMUTATION_PARITY = "PERMUTATION_PARITY"
}

class CubeValidation(
    val valid: Boolean,
    val errorType: String? = null,
    val title: String,
    val message: String,
    /** Facelet indices of the offending piece(s), for highlighting. */
    val highlight: List<Int> = emptyList(),
    val canAutoFix: Boolean = false,
    /** Sticker count per colour letter (U, R, F, D, L, B). */
    val counts: Map<Char, Int>,
    val painted: Int,
    val total: Int,
    val checks: CubeChecks = CubeChecks.PENDING,
)

private val cubeColorCodes = listOf('U', 'R', 'F', 'D', 'L', 'B')

fun colorList(state: String, facelets: List<Int>): String =
    facelets.joinToString("/") { colorNames[state[it]] ?: state[it].toString() }

fun validateCube(state: String, n: Int): CubeValidation {
    val total = 6 * n * n
    val perColor = n * n
    val counts = cubeColorCodes.associateWith { 0 }.toMutableMap()
    var painted = 0

    fun fail(
        errorType: String,
        title: String,
        message: String,
        highlight: List<Int> = emptyList(),
        canAutoFix: Boolean = false,
        checks: CubeChecks = CubeChecks.PENDING,
    ) = CubeValidation(false, errorType, title, message, highlight, canAutoFix, counts, painted, total, checks)

    if (state.length != total || n < 3 || n > 5) {
        return fail(ValidationError.INVALID_INPUT, "Invalid cube data", "Expected $total facelets for a ${n}x$n cube.")
    }

    for (ch in state) {
        val c = counts[ch]
        if (c != null) {
            counts[ch] = c + 1
            painted++
        } else if (ch != 'X') {
            return fail(ValidationError.INVALID_INPUT, "Invalid cube data", "Unknown colour code \"$ch\".")
        }
    }

    if (painted < total) {
        return fail(
            ValidationError.INCOMPLETE,
            "Painting in progress",
            "$painted/$total stickers painted. Paint every sticker to match your physical cube ($perColor of each colour).",
        )
    }

    val wrong = cubeColorCodes.filter { counts[it] != perColor }
    if (wrong.isNotEmpty()) {
        return fail(
            ValidationError.COUNT_MISMATCH,
            "Colour counts do not match",
            "Each colour needs exactly $perColor stickers. Off: ${wrong.joinToString(", ") { "${colorNames[it]} ${counts[it]}" }}.",
        )
    }

    val slots = getPieceSlots(n)

    if (n % 2 == 1) {
        val centres = (0 until 6).map { it * n * n + n * n / 2 }
        val bad = (0 until 6).filter { state[centres[it]] != cubeColorCodes[it] }.map { centres[it] }
        if (bad.isNotEmpty()) {
            return fail(
                ValidationError.CENTER_MISMATCH,
                "Centre colours are wrong",
                "Hold the cube with White on top and Green in front: the centres must be White (U), Red (R), Green (F), Yellow (D), Orange (L), Blue (B).",
                highlight = bad,
            )
        }
    }

    // ---- Corners ----
    val corners = readCorners(state, slots)
    val badCorner = corners.firstOrNull { it.target == -1 }
    if (badCorner != null) {
        val f = slots.corners[badCorner.slot]
        return fail(
            ValidationError.CORNER_INVALID,
            "Impossible corner colours",
            if (badCorner.mirrored) {
                "The corner at ${pieceName(f, slots)} (${colorList(state, f)}) has its colours in mirror order — two of its stickers are swapped."
            } else {
                "The corner at ${pieceName(f, slots)} shows ${colorList(state, f)}, which no real corner piece has."
            },
            highlight = f,
        )
    }
    val cornerOwner = HashMap<Int, Int>()
    for (c in corners) {
        val prev = cornerOwner[c.target]
        if (prev != null) {
            return fail(
                ValidationError.CORNER_DUPLICATE,
                "Duplicate corner piece",
                "Two corners show the same colours (${colorList(state, slots.corners[c.slot])}). Re-check these corner stickers.",
                highlight = slots.corners[prev] + slots.corners[c.slot],
            )
        }
        cornerOwner[c.target] = c.slot
    }

    // ---- Edges (3x3), middle edges (5x5) and wings (4x4, 5x5) ----
    // `midEdges` follow the 3x3 edge rules; `wings` can never be flipped in place.
    val midEdges = if (n == 3) slots.edges else slots.midges
    val groups = buildList {
        if (midEdges.isNotEmpty()) add(midEdges to "edge")
        if (n >= 4) add(slots.edges to "edge wing")
    }
    val readings = mutableListOf<List<EdgeReading>>()
    for ((pairs, word) in groups) {
        val edges = readEdges(state, slots, pairs)
        readings.add(edges)
        val badEdge = edges.firstOrNull { it.target == -1 }
        if (badEdge != null) {
            val f = pairs[badEdge.slot]
            return fail(
                ValidationError.EDGE_INVALID,
                "Impossible $word colours",
                "The $word at ${pieceName(f, slots)} shows ${colorList(state, f)}, which no real piece has.",
                highlight = f,
            )
        }
        val edgeOwner = HashMap<Int, Int>()
        for (e in edges) {
            val prev = edgeOwner[e.target]
            if (prev != null) {
                return fail(
                    ValidationError.EDGE_DUPLICATE,
                    "Duplicate $word",
                    "Two ${word}s show the same colours (${colorList(state, pairs[e.slot])}). Re-check these stickers.",
                    highlight = pairs[prev] + pairs[e.slot],
                )
            }
            edgeOwner[e.target] = e.slot
        }
    }
    val edges = if (midEdges.isNotEmpty()) readings.first() else emptyList()
    val wings = if (n >= 4) readings.last() else emptyList()

    // ---- 5x5 centre orbits: x-centres and t-centres never mix ----
    if (n == 5) {
        for ((orbit, name) in listOf(slots.xCenters to "corner", slots.tCenters to "edge")) {
            val perFace = cubeColorCodes.associateWith { 0 }.toMutableMap()
            for (f in orbit) perFace[state[f]] = perFace.getValue(state[f]) + 1
            val off = cubeColorCodes.filter { perFace[it] != 4 }
            if (off.isNotEmpty()) {
                return fail(
                    ValidationError.CENTER_MISMATCH,
                    "Centre stickers do not add up",
                    "Each face has 4 $name-centre stickers (the ${if (name == "corner") "diagonal" else "cross"} ones around the middle), and each colour must appear on exactly 4 of them across the cube. " +
                        "Off: ${off.joinToString(", ") { "${colorNames[it]} ${perFace[it]}" }}.",
                    highlight = orbit.filter { state[it] in off },
                )
            }
        }
    }

    var cornerTwistCheck = CheckState.OK
    var edgeFlipCheck = CheckState.OK
    var permutationCheck = CheckState.OK

    if (wings.any { it.flip == 1 }) edgeFlipCheck = CheckState.ERROR

    val twistSum = corners.sumOf { it.twist } % 3
    if (twistSum != 0) cornerTwistCheck = CheckState.ERROR

    var flipSum = 0
    if (edges.isNotEmpty()) {
        flipSum = edges.sumOf { it.flip } % 2
        if (flipSum != 0) edgeFlipCheck = CheckState.ERROR
        val cp = permutationParity(corners.map { it.target })
        val ep = permutationParity(edges.map { it.target })
        if (cp != ep) permutationCheck = CheckState.ERROR
    }

    val checks = CubeChecks(cornerTwistCheck, edgeFlipCheck, permutationCheck)

    if (twistSum != 0) {
        // A single corner twisted clockwise adds 1, counter-clockwise adds 2.
        val culprit = corners.firstOrNull { it.twist == twistSum } ?: corners.first { it.twist != 0 }
        val f = slots.corners[culprit.slot]
        return fail(
            ValidationError.CORNER_TWIST,
            "Twisted corner detected",
            "Corner twist sum is $twistSum (mod 3), which no legal turns can produce. Likely culprit: the ${pieceName(f, slots)} corner, twisted ${if (twistSum == 1) "clockwise" else "counter-clockwise"}. Twist it back (or check its stickers).",
            highlight = f,
            canAutoFix = true,
            checks = checks,
        )
    }

    val flippedWing = wings.firstOrNull { it.flip == 1 }
    if (flippedWing != null) {
        val f = slots.edges[flippedWing.slot]
        return fail(
            ValidationError.WING_FLIP,
            "Impossible edge wing",
            "The edge wing at ${pieceName(f, slots)} (${colorList(state, f)}) reads in mirror order — a ${n}x$n wing can never be flipped in place. Its two stickers are probably swapped.",
            highlight = f,
            canAutoFix = true,
            checks = checks,
        )
    }

    if (flipSum != 0) {
        val e = edges.first { it.flip == 1 }
        val f = midEdges[e.slot]
        return fail(
            ValidationError.EDGE_FLIP,
            "Flipped edge detected",
            "An odd number of edges is flipped, which legal turns cannot produce. One edge (e.g. ${pieceName(f, slots)}) has been flipped by hand.",
            highlight = f,
            canAutoFix = true,
            checks = checks,
        )
    }

    if (permutationCheck == CheckState.ERROR) {
        return fail(
            ValidationError.PERMUTATION_PARITY,
            "Swapped pieces detected",
            "Corner and edge permutation parity differ: two pieces were swapped by hand (or two stickers were painted on the wrong pieces).",
            canAutoFix = true,
            checks = checks,
        )
    }

    return CubeValidation(
        valid = true,
        title = "Cube state is valid",
        message = "All colour counts, corner twists, edge flips and parities are physically legal. Ready to solve!",
        counts = counts,
        painted = painted,
        total = total,
        checks = checks,
    )
}
