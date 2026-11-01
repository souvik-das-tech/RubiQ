// Physical-legality checks and auto-fix for the Pyraminx and the Skewb.
//
// Neither puzzle has a fixed colour orientation the user must hold: the solved colouring is
// read from pieces that never move (Pyraminx centres, the Skewb's top-front-right corner).
//
// Pyraminx: tips and centres may sit in any rotation; edges need an even permutation and
//           an even number of flips (equivalently, an even permutation of edge stickers).
// Skewb:    centres need an even permutation; corners stay in their tetrad with an even
//           permutation, and each tetrad's twist sum is fixed by the other tetrad's
//           permutation (worked out by a small search, see skewbLegalKeys).
package com.bbcl.rubiq

val pyraminxPalette = listOf('F', 'R', 'B', 'D')
val skewbPalette = listOf('U', 'D', 'F', 'B', 'R', 'L')

/** Extra error codes for the Skewb. */
object PolyValidationError {
    const val CORNER_ORBIT = "CORNER_ORBIT"
    const val CENTER_SWAP = "CENTER_SWAP"
}

private fun faceList(g: PolyGeometry, facelets: List<Int>) = facelets.joinToString("-") { "${g.faces[g.stickers[it].face].name}" }

/** Colour counts and completeness, shared by both puzzles. Returns a failure or null. */
private fun countCheck(
    state: String,
    total: Int,
    palette: List<Char>,
    counts: MutableMap<Char, Int>,
    perColor: Int,
): CubeValidation? {
    var painted = 0
    fun fail(type: String, title: String, msg: String) =
        CubeValidation(false, type, title, msg, counts = counts, painted = painted, total = total)
    if (state.length != total) {
        return fail(ValidationError.INVALID_INPUT, "Invalid puzzle data", "Expected $total stickers.")
    }
    for (ch in state) {
        val c = counts[ch]
        if (c != null) {
            counts[ch] = c + 1
            painted++
        } else if (ch != 'X') {
            return fail(ValidationError.INVALID_INPUT, "Invalid puzzle data", "Unknown colour code \"$ch\".")
        }
    }
    if (painted < total) {
        return fail(
            ValidationError.INCOMPLETE,
            "Painting in progress",
            "$painted/$total stickers painted. Paint every sticker to match your puzzle ($perColor of each colour).",
        )
    }
    val wrong = palette.filter { counts[it] != perColor }
    if (wrong.isNotEmpty()) {
        return fail(
            ValidationError.COUNT_MISMATCH,
            "Colour counts do not match",
            "Each colour needs exactly $perColor stickers. Off: ${wrong.joinToString(", ") { "${colorNames[it]} ${counts[it]}" }}.",
        )
    }
    return null
}

// ============== Pyraminx ==============

private val vertexName = mapOf("U" to "top", "L" to "left", "R" to "right", "B" to "back")

fun validatePyraminx(state: String): CubeValidation {
    val g = pyraminxGeometry
    val slots = pyraminxSlots
    val total = g.stickers.size
    val counts = pyraminxPalette.associateWith { 0 }.toMutableMap()
    countCheck(state, total, pyraminxPalette, counts, 9)?.let { return it }

    fun fail(
        type: String,
        title: String,
        msg: String,
        highlight: List<Int> = emptyList(),
        canAutoFix: Boolean = false,
        checks: CubeChecks = CubeChecks.PENDING,
    ) = CubeValidation(false, type, title, msg, highlight, canAutoFix, counts, total, total, checks)

    // ---- Centres decide the colour of every face ----
    for (c in slots.centers.values) {
        if (c.stickers.map { state[it] }.toSet().size != 3) {
            return fail(
                ValidationError.CENTER_MISMATCH,
                "Impossible centre colours",
                "The centre under the ${vertexName[c.key]} tip shows ${colorList(state, c.stickers)}: a centre always has three different colours.",
                highlight = c.stickers,
            )
        }
    }
    val target = pyraminxTarget(state)
        ?: return fail(
            ValidationError.CENTER_MISMATCH,
            "Impossible centre colours",
            "Two centres have the same three colours. Re-check the triangles right below each tip.",
            highlight = slots.centers.values.flatMap { it.stickers },
        )
    for (group in listOf(slots.centers.values, slots.tips.values)) {
        for (p in group) {
            val r = readPieces(state, target, listOf(p)).first()
            if (r.target == 0) continue
            val tip = p.kind == "tip"
            val what = if (tip) "${vertexName[p.key]} tip" else "centre under the ${vertexName[p.key]} tip"
            return fail(
                ValidationError.CORNER_INVALID,
                if (tip) "Impossible tip colours" else "Impossible centre colours",
                if (r.mirrored) {
                    "The $what (${colorList(state, p.stickers)}) has its colours in mirror order — two of its stickers are swapped."
                } else {
                    "The $what shows ${colorList(state, p.stickers)}, which does not match the centre below it."
                },
                highlight = p.stickers,
            )
        }
    }

    // ---- Edges ----
    val edges = readPieces(state, target, slots.edges)
    val bad = edges.firstOrNull { it.target == -1 }
    if (bad != null) {
        val f = slots.edges[bad.slot].stickers
        return fail(
            ValidationError.EDGE_INVALID,
            "Impossible edge colours",
            "The edge at ${faceList(g, f)} shows ${colorList(state, f)}, which no real edge has.",
            highlight = f,
        )
    }
    val owner = HashMap<Int, Int>()
    for (e in edges) {
        val prev = owner[e.target]
        if (prev != null) {
            return fail(
                ValidationError.EDGE_DUPLICATE,
                "Duplicate edge",
                "Two edges show the same colours (${colorList(state, slots.edges[e.slot].stickers)}). Re-check these stickers.",
                highlight = slots.edges[prev].stickers + slots.edges[e.slot].stickers,
            )
        }
        owner[e.target] = e.slot
    }

    val swapped = pieceParity(edges) != 0
    val flipped = stickerParity(state, target, slots.edges, edges) != 0
    val checks = CubeChecks(
        CheckState.OK,
        if (flipped) CheckState.ERROR else CheckState.OK,
        if (swapped) CheckState.ERROR else CheckState.OK,
    )
    if (flipped) {
        return fail(
            ValidationError.EDGE_FLIP,
            "Flipped edge detected",
            "An odd number of edges is flipped, which legal turns cannot produce. One edge was flipped by hand (or two of its stickers were painted the wrong way round).",
            highlight = flipCulprit(state, target, edges),
            canAutoFix = true,
            checks = checks,
        )
    }
    if (swapped) {
        return fail(
            ValidationError.PERMUTATION_PARITY,
            "Swapped edges detected",
            "The edges form an odd permutation: two edges were swapped by hand (or painted on the wrong pieces).",
            canAutoFix = true,
            checks = checks,
        )
    }
    return CubeValidation(
        valid = true,
        title = "Pyraminx state is valid",
        message = "All colour counts, tips, centres and edges are physically legal. Ready to solve!",
        counts = counts,
        painted = total,
        total = total,
        checks = checks,
    )
}

/** The edge most likely flipped by hand: one in its home slot but reading the wrong way. */
private fun flipCulprit(state: String, target: String, edges: List<PieceReading>): List<Int> {
    val slots = pyraminxSlots.edges
    for (e in edges) {
        val f = slots[e.slot].stickers
        if (e.target == e.slot && f.any { state[it] != target[it] }) return f
    }
    return emptyList()
}

private fun CharArray.swap(a: Int, b: Int) {
    val t = this[a]
    this[a] = this[b]
    this[b] = t
}

fun autoFixPyraminx(state: String, fixes: MutableList<String>): String? {
    val v = validatePyraminx(state)
    val target = pyraminxTarget(state) ?: return null
    val edges = pyraminxSlots.edges
    val arr = state.toCharArray()
    if (v.errorType == ValidationError.EDGE_FLIP) {
        val culprit = flipCulprit(state, target, readPieces(state, target, edges))
        val f = culprit.ifEmpty { edges[0].stickers }
        arr.swap(f[0], f[1])
        fixes.add("Flipped the ${faceList(pyraminxGeometry, f)} edge back")
    } else if (v.errorType == ValidationError.PERMUTATION_PARITY) {
        val a = edges[0].stickers
        val b = edges[1].stickers
        for (k in 0 until 2) arr.swap(a[k], b[k])
        fixes.add("Swapped two edges to fix the permutation parity")
    } else {
        return null
    }
    return String(arr)
}

// ============== Skewb ==============

/**
 * Legal combinations of (E-tetrad permutation, O-tetrad permutation, E twist sum, O twist
 * sum). A move changes the twist sums by amounts that depend only on piece positions, so
 * this projection of the state evolves on its own; a breadth-first search over it from the
 * solved state finds every reachable combination.
 */
private val skewbLegalKeys: Set<String> by lazy {
    val g = skewbGeometry
    val keys = HashSet<String>()
    val queue = ArrayDeque(listOf(g.solvedState))
    keys.add(skewbKey(g.solvedState, g.solvedState)!!)
    while (queue.isNotEmpty()) {
        val s = queue.removeLast()
        for (m in g.axes.keys) {
            val next = g.applyMove(s, m)
            if (keys.add(skewbKey(next, g.solvedState)!!)) queue.addLast(next)
        }
    }
    keys
}

private fun skewbKey(state: String, target: String): String? {
    val sl = skewbSlots
    val rd = readPieces(state, target, sl.corners)
    if (rd.any { it.target < 0 }) return null
    var e = 0
    var o = 0
    for (r in rd) if (sl.tetrad[r.slot] == 0) e += r.twist else o += r.twist
    val ep = rd.filter { sl.tetrad[it.slot] == 0 }.joinToString(",") { "${it.target}" }
    val op = rd.filter { sl.tetrad[it.slot] == 1 }.joinToString(",") { "${it.target}" }
    return "$ep|$op|${e % 3}|${o % 3}"
}

private fun isLegalSkewb(state: String, target: String) = skewbKey(state, target) in skewbLegalKeys

private fun tetradOdd(corners: List<PieceReading>, t: Int) =
    pieceParity(corners.filter { skewbSlots.tetrad[it.slot] == t }) != 0

fun validateSkewb(state: String): CubeValidation {
    val g = skewbGeometry
    val sl = skewbSlots
    val total = g.stickers.size
    val counts = skewbPalette.associateWith { 0 }.toMutableMap()
    countCheck(state, total, skewbPalette, counts, 5)?.let { return it }

    fun fail(
        type: String,
        title: String,
        msg: String,
        highlight: List<Int> = emptyList(),
        canAutoFix: Boolean = false,
        checks: CubeChecks = CubeChecks.PENDING,
    ) = CubeValidation(false, type, title, msg, highlight, canAutoFix, counts, total, total, checks)

    // ---- The fixed top-front-right corner decides the colour of every face ----
    val fixed = sl.corners[sl.fixedCorner].stickers
    val target = skewbTarget(state)
        ?: return fail(
            ValidationError.CORNER_INVALID,
            "Impossible corner colours",
            "The top-front-right corner shows ${colorList(state, fixed)}, which no real corner has (or two of its stickers are swapped). " +
                "This corner never moves, so it decides the colour of every face.",
            highlight = fixed,
        )

    // ---- Centres ----
    val centerColors = HashMap<Char, Int>()
    for (c in sl.centers) {
        val f = c.stickers[0]
        val prev = centerColors[state[f]]
        if (prev != null) {
            return fail(
                ValidationError.CENTER_MISMATCH,
                "Duplicate centre colour",
                "Two centres are ${colorNames[state[f]]}: every face of a Skewb has its own centre colour.",
                highlight = listOf(prev, f),
            )
        }
        centerColors[state[f]] = f
    }

    // ---- Corners ----
    val corners = readPieces(state, target, sl.corners)
    val bad = corners.firstOrNull { it.target < 0 }
    if (bad != null) {
        val f = sl.corners[bad.slot].stickers
        return fail(
            ValidationError.CORNER_INVALID,
            "Impossible corner colours",
            if (bad.mirrored) {
                "The ${sl.corners[bad.slot].key} corner (${colorList(state, f)}) has its colours in mirror order — two of its stickers are swapped."
            } else {
                "The ${sl.corners[bad.slot].key} corner shows ${colorList(state, f)}, which no real corner has."
            },
            highlight = f,
        )
    }
    val owner = HashMap<Int, Int>()
    for (r in corners) {
        val prev = owner[r.target]
        if (prev != null) {
            return fail(
                ValidationError.CORNER_DUPLICATE,
                "Duplicate corner piece",
                "Two corners show the same colours (${colorList(state, sl.corners[r.slot].stickers)}). Re-check these corner stickers.",
                highlight = sl.corners[prev].stickers + sl.corners[r.slot].stickers,
            )
        }
        owner[r.target] = r.slot
    }
    val stray = corners.firstOrNull { sl.tetrad[it.target] != sl.tetrad[it.slot] }
    if (stray != null) {
        return fail(
            PolyValidationError.CORNER_ORBIT,
            "Corner in an impossible place",
            "The ${sl.corners[stray.slot].key} corner holds a piece that can never reach that spot: Skewb corners only move among every other corner. " +
                "Its stickers were probably painted on the wrong corner.",
            highlight = sl.corners[stray.slot].stickers,
        )
    }

    // ---- Parities ----
    val centersOdd = pieceParity(readPieces(state, target, sl.centers)) != 0
    val cornersOdd = tetradOdd(corners, 0) || tetradOdd(corners, 1)
    val twistOk = cornersOdd || isLegalSkewb(state, target)
    val checks = CubeChecks(
        if (cornersOdd) CheckState.PENDING else if (twistOk) CheckState.OK else CheckState.ERROR,
        if (centersOdd) CheckState.ERROR else CheckState.OK,
        if (cornersOdd) CheckState.ERROR else CheckState.OK,
    )
    if (cornersOdd) {
        return fail(
            ValidationError.PERMUTATION_PARITY,
            "Swapped corners detected",
            "Two corners were swapped by hand (or painted on the wrong pieces): turns only ever cycle three corners at a time.",
            canAutoFix = true,
            checks = checks,
        )
    }
    if (!twistOk) {
        return fail(
            ValidationError.CORNER_TWIST,
            "Twisted corner detected",
            "The corner twists add up to a total that legal turns cannot produce: a corner was twisted by hand (or painted rotated).",
            canAutoFix = true,
            checks = checks,
        )
    }
    if (centersOdd) {
        return fail(
            PolyValidationError.CENTER_SWAP,
            "Swapped centres detected",
            "Two centres were swapped by hand (or painted the wrong way round): turns only ever cycle three centres at a time.",
            canAutoFix = true,
            checks = checks,
        )
    }
    return CubeValidation(
        valid = true,
        title = "Skewb state is valid",
        message = "All colour counts, centres, corner positions and twists are physically legal. Ready to solve!",
        counts = counts,
        painted = total,
        total = total,
        checks = checks,
    )
}

fun autoFixSkewb(state: String, fixes: MutableList<String>): String? {
    val v = validateSkewb(state)
    val sl = skewbSlots
    val target = skewbTarget(state) ?: return null
    val arr = state.toCharArray()

    val movable = sl.corners.indices.filter { it != sl.fixedCorner }
    when (v.errorType) {
        PolyValidationError.CENTER_SWAP -> {
            arr.swap(sl.centers[0].stickers[0], sl.centers[1].stickers[0])
            fixes.add("Swapped two centres to fix the centre parity")
        }
        ValidationError.PERMUTATION_PARITY -> {
            val corners = readPieces(state, target, sl.corners)
            val t = listOf(0, 1).first { tetradOdd(corners, it) }
            val pair = movable.filter { sl.tetrad[it] == t }.take(2)
            for (k in 0 until 3) arr.swap(sl.corners[pair[0]].stickers[k], sl.corners[pair[1]].stickers[k])
            fixes.add("Swapped the ${sl.corners[pair[0]].key} and ${sl.corners[pair[1]].key} corners")
        }
        ValidationError.CORNER_TWIST -> {
            // Twist single corners until the twist sums are legal (one or two corners).
            fun twisted(s: String, corner: Int, k: Int): String {
                val a = s.toCharArray()
                val f = sl.corners[corner].stickers
                val colors = f.map { s[it] }
                for (j in 0 until 3) a[f[j]] = colors[(j + k) % 3]
                return String(a)
            }

            for (c in movable) {
                for (k in listOf(1, 2)) {
                    val s = twisted(state, c, k)
                    if (isLegalSkewb(s, target)) {
                        fixes.add("Twisted the ${sl.corners[c].key} corner back into place")
                        return s
                    }
                }
            }
            for (c in movable) {
                for (d in movable.filter { sl.tetrad[it] != sl.tetrad[c] }) {
                    for (k in listOf(1, 2)) {
                        for (j in listOf(1, 2)) {
                            val s = twisted(twisted(state, c, k), d, j)
                            if (isLegalSkewb(s, target)) {
                                fixes.add("Twisted the ${sl.corners[c].key} and ${sl.corners[d].key} corners back into place")
                                return s
                            }
                        }
                    }
                }
            }
            return null
        }
        else -> return null
    }
    return String(arr)
}
