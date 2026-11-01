// Generic NxN facelet model shared by the 3D view, the validator and the solvers.
//
// Facelet order follows Kociemba: faces U, R, F, D, L, B, each N*N row-major as seen
// looking straight at that face (U is viewed with B at the top, D with F at the top).
// A cube state is a string of face letters (or 'X' for an unpainted sticker).
package com.bbcl.rubiq

import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

typealias Vec3 = IntArray

val faceOrder = listOf('U', 'R', 'F', 'D', 'L', 'B')

val faceNormals: Map<Char, Vec3> = mapOf(
    'U' to intArrayOf(0, 1, 0),
    'D' to intArrayOf(0, -1, 0),
    'R' to intArrayOf(1, 0, 0),
    'L' to intArrayOf(-1, 0, 0),
    'F' to intArrayOf(0, 0, 1),
    'B' to intArrayOf(0, 0, -1),
)

val faceNames = mapOf('U' to "Top", 'D' to "Bottom", 'R' to "Right", 'L' to "Left", 'F' to "Front", 'B' to "Back")

val colorNames = mapOf(
    'U' to "White",
    'D' to "Yellow",
    'F' to "Green",
    'B' to "Blue",
    'R' to "Red",
    'L' to "Orange",
    'X' to "Unpainted",
)

class StickerInfo(
    val index: Int,
    val face: Char,
    val row: Int,
    val col: Int,
    val grid: Vec3, // cubie coordinates, each 0..N-1
    val pos2: Vec3, // cubie centre in doubled, centred coordinates (2g - (N-1))
    val normal: Vec3,
)

class CubieInfo(val key: String, val grid: Vec3, val pos2: Vec3) {
    val stickers = mutableListOf<Int>()
}

class CubeGeometry(
    val n: Int,
    val stickers: List<StickerInfo>,
    val cubies: List<CubieInfo>,
    val stickerAt: Map<String, Int>,
)

private fun gridFor(face: Char, row: Int, col: Int, n: Int): Vec3 {
    val m = n - 1
    return when (face) {
        'U' -> intArrayOf(col, m, row)
        'D' -> intArrayOf(col, 0, m - row)
        'F' -> intArrayOf(col, m - row, m)
        'B' -> intArrayOf(m - col, m - row, 0)
        'L' -> intArrayOf(0, m - row, col)
        else -> intArrayOf(m, m - row, m - col) // R
    }
}

private fun stickerKey(p: Vec3, n: Vec3) = "${p[0]},${p[1]},${p[2]}|${n[0]},${n[1]},${n[2]}"

private val geometryCache = ConcurrentHashMap<Int, CubeGeometry>()

fun getGeometry(n: Int): CubeGeometry = geometryCache.getOrPut(n) {
    val stickers = mutableListOf<StickerInfo>()
    val stickerAt = HashMap<String, Int>()
    val cubieMap = LinkedHashMap<String, CubieInfo>() // insertion ordered, like a JS Map

    for (f in faceOrder.indices) {
        val face = faceOrder[f]
        for (row in 0 until n) {
            for (col in 0 until n) {
                val index = f * n * n + row * n + col
                val grid = gridFor(face, row, col, n)
                val pos2 = IntArray(3) { 2 * grid[it] - (n - 1) }
                val normal = faceNormals.getValue(face)
                stickers.add(StickerInfo(index, face, row, col, grid, pos2, normal))
                stickerAt[stickerKey(pos2, normal)] = index

                val key = grid.joinToString(",")
                cubieMap.getOrPut(key) { CubieInfo(key, grid, pos2) }.stickers.add(index)
            }
        }
    }
    CubeGeometry(n, stickers, cubieMap.values.toList(), stickerAt)
}

// Rotate v a quarter turn clockwise as seen from the tip of `axis` (i.e. -90° about axis).
private fun rotateCW(axis: Vec3, v: Vec3): Vec3 {
    val (ax, ay, az) = axis
    val (x, y, z) = v
    val cx = ay * z - az * y
    val cy = az * x - ax * z
    val cz = ax * y - ay * x
    val d = ax * x + ay * y + az * z
    return intArrayOf(-cx + ax * d, -cy + ay * d, -cz + az * d)
}

// ============== Moves ==============

class MoveSpec(
    val token: String,
    val face: Char, // the turning axis points out of this face
    val layers: List<Int>, // 1 = outer layer on `face`
    val turns: Int, // clockwise quarter turns (1..3) as seen looking at `face`
)

private val moveRe = Regex("^(\\d*)([URFDLBxyz])(w?)(2'|2|')?$")
private val rotationFace = mapOf('x' to 'R', 'y' to 'U', 'z' to 'F')

/**
 * Parses WCA-style notation: R, R', R2, Rw (two layers), 3Rw (three layers),
 * 2R (second layer only, i.e. an inner slice) and whole-cube rotations x, y, z.
 */
fun parseMove(token: String, n: Int): MoveSpec? {
    val m = moveRe.find(token) ?: return null
    val prefix = m.groupValues[1]
    val letter = m.groupValues[2][0]
    val wide = m.groupValues[3]
    val suffix = m.groupValues[4]
    val turns = if (suffix == "'") 3 else if (suffix.isNotEmpty()) 2 else 1

    val rotFace = rotationFace[letter]
    if (rotFace != null) {
        if (prefix.isNotEmpty() || wide.isNotEmpty()) return null
        return MoveSpec(token, rotFace, (1..n).toList(), turns)
    }

    val num = if (prefix.isNotEmpty()) prefix.toInt() else 0
    val layers: List<Int>
    if (wide.isNotEmpty()) {
        val depth = if (num != 0) num else 2
        if (depth < 1 || depth > n) return null
        layers = (1..depth).toList()
    } else {
        val depth = if (num != 0) num else 1
        if (depth < 1 || depth > n) return null
        layers = listOf(depth)
    }
    return MoveSpec(token, letter, layers, turns)
}

/** Layer depth (1 = outer) of a cubie centre measured from `face`. */
fun layerDepth(pos2: Vec3, face: Char, n: Int): Int {
    val nv = faceNormals.getValue(face)
    val d = pos2[0] * nv[0] + pos2[1] * nv[1] + pos2[2] * nv[2]
    return ((n - 1) - d) / 2 + 1
}

// Gather permutation: next[i] = current[perm[i]].
private val permCache = ConcurrentHashMap<String, ShortArray>()

fun movePermutation(token: String, n: Int): ShortArray = permCache.getOrPut("$n:$token") {
    val spec = parseMove(token, n) ?: throw IllegalArgumentException("Invalid move \"$token\"")
    val geo = getGeometry(n)
    val axis = faceNormals.getValue(spec.face)

    val quarter = ShortArray(geo.stickers.size) { it.toShort() }
    for (s in geo.stickers) {
        if (layerDepth(s.pos2, spec.face, n) !in spec.layers) continue
        val target = geo.stickerAt[stickerKey(rotateCW(axis, s.pos2), rotateCW(axis, s.normal))]
            ?: throw IllegalStateException("Cube geometry mismatch")
        quarter[target] = s.index.toShort()
    }

    var perm = quarter
    for (t in 1 until spec.turns) perm = composePermutations(perm, quarter)
    perm
}

/** Permutation equivalent to applying `first` and then `second`. */
fun composePermutations(first: ShortArray, second: ShortArray): ShortArray =
    ShortArray(first.size) { first[second[it].toInt()] }

private val whitespace = Regex("\\s+")

fun splitMoves(moves: String): List<String> = moves.trim().split(whitespace).filter { it.isNotEmpty() }

/** Applies a gather permutation to a state string. */
fun permute(state: String, perm: ShortArray): String {
    val out = CharArray(perm.size)
    for (i in perm.indices) out[i] = state[perm[i].toInt()]
    return String(out)
}

fun applyMove(state: String, token: String, n: Int): String = permute(state, movePermutation(token, n))

fun applyMoves(state: String, moves: Iterable<String>, n: Int): String {
    var s = state
    for (m in moves) if (m.isNotEmpty()) s = applyMove(s, m, n)
    return s
}

fun invertMove(token: String): String = when {
    token.endsWith("2'") -> token.dropLast(1)
    token.endsWith("2") -> token
    token.endsWith("'") -> token.dropLast(1)
    else -> "$token'"
}

fun invertMoves(tokens: List<String>): List<String> = tokens.asReversed().map(::invertMove)

private val opposite = mapOf('U' to 'D', 'D' to 'U', 'R' to 'L', 'L' to 'R', 'F' to 'B', 'B' to 'F')
private val axisOf = mapOf('U' to 'y', 'D' to 'y', 'R' to 'x', 'L' to 'x', 'F' to 'z', 'B' to 'z')
private val canonicalFace = mapOf('x' to 'R', 'y' to 'U', 'z' to 'F')

private val suffixRe = Regex("(2'|2|')$")

private fun baseOf(token: String) = token.replaceFirst(suffixRe, "")

private fun withTurns(base: String, turns: Int): String? {
    val t = ((turns % 4) + 4) % 4
    if (t == 0) return null
    return base + if (t == 1) "" else if (t == 2) "2" else "'"
}

private class Group(val base: String, val flip: Boolean, var turns: Int)

/**
 * Cancels and merges moves: consecutive moves on the same axis commute, so within such a
 * run, moves that turn the same set of layers are combined (R R -> R2, R L R' -> L, ...).
 */
fun simplifyMoves(tokens: List<String>, n: Int): List<String> {
    var current = tokens.toList()
    while (true) {
        val out = mutableListOf<String>()
        var i = 0
        while (i < current.size) {
            val spec = parseMove(current[i], n)
            if (spec == null) {
                out.add(current[i])
                i++
                continue
            }
            val axis = axisOf.getValue(spec.face)
            // Collect the run of moves on this axis.
            val groups = LinkedHashMap<String, Group>()
            var j = i
            while (j < current.size) {
                val s = parseMove(current[j], n)
                if (s == null || axisOf[s.face] != axis) break
                val flip = s.face != canonicalFace[axis]
                val layers = (if (flip) s.layers.map { n + 1 - it } else s.layers).sorted().joinToString(",")
                val amount = if (flip) -s.turns else s.turns
                val g = groups[layers]
                if (g != null) {
                    g.turns += amount
                } else {
                    groups[layers] = Group(baseOf(current[j]), flip, amount)
                }
                j++
            }
            for (g in groups.values) {
                withTurns(g.base, if (g.flip) -g.turns else g.turns)?.let(out::add)
            }
            i = j
        }
        if (out.size == current.size) return out
        current = out
    }
}

fun solvedState(n: Int): String = faceOrder.joinToString("") { it.toString().repeat(n * n) }

fun blankState(n: Int): String {
    if (n % 2 == 0) return "X".repeat(6 * n * n)
    // Centres of odd cubes are fixed reference points and stay painted.
    val half = "X".repeat(n * n / 2)
    return faceOrder.joinToString("") { "$half$it$half" }
}

/** Whether `index` is the fixed centre sticker of an odd cube. */
fun isFixedCenter(index: Int, n: Int) = n % 2 == 1 && index % (n * n) == n * n / 2

fun isSolvedState(state: String, n: Int) = state == solvedState(n)

// ============== Scrambles ==============

fun randomScramble(n: Int, length: Int? = null, rng: Random = Random.Default): List<String> {
    val len = length ?: when (n) { 3 -> 20; 4 -> 40; else -> 60 }
    val faces = listOf('U', 'D', 'R', 'L', 'F', 'B')
    val suffixes = listOf("", "'", "2")
    val out = mutableListOf<String>()
    var lastFace: Char? = null
    var prevFace: Char? = null
    while (out.size < len) {
        val face = faces[rng.nextInt(faces.size)]
        if (face == lastFace) continue
        // Avoid three moves in a row on one axis (e.g. R L R).
        if (prevFace != null && lastFace != null && opposite[face] == lastFace && face == prevFace) continue
        var base = face.toString()
        // WCA 4x4 scrambles use outer turns plus Rw, Uw and Fw; 5x5 scrambles use all six wide turns.
        if (n == 4 && (face == 'R' || face == 'U' || face == 'F') && rng.nextDouble() < 0.4) base = "${face}w"
        if (n >= 5 && rng.nextDouble() < 0.5) base = "${face}w"
        out.add(base + suffixes[rng.nextInt(suffixes.size)])
        prevFace = lastFace
        lastFace = face
    }
    return out
}

// ============== Quarter-turn steps ==============

class QuarterStep(
    val move: String, // always a single 90° turn, e.g. "R" or "2U'"
    val from: String, // the original token, e.g. "R2"
    val part: Int, // 1-based index within `from`
    val parts: Int, // 1 for quarter turns, 2 for half turns
)

private val halfRe = Regex("2'?$")
private val digitsRe = Regex("^\\d+$")

/**
 * Splits every half turn into two quarter turns (R2 -> R R), so each playback step is
 * exactly one 90° turn and is easy to follow on a physical cube.
 */
fun toQuarterSteps(tokens: List<String>): List<QuarterStep> {
    val out = mutableListOf<QuarterStep>()
    for (token in tokens) {
        if (halfRe.containsMatchIn(token) && !digitsRe.matches(token.replaceFirst(halfRe, ""))) {
            val base = token.replaceFirst(halfRe, "")
            out.add(QuarterStep(base, token, 1, 2))
            out.add(QuarterStep(base, token, 2, 2))
        } else {
            out.add(QuarterStep(token, token, 1, 1))
        }
    }
    return out
}

fun quarterStepExplanation(step: QuarterStep, n: Int): String {
    val text = moveExplanation(step.move, n)
    return if (step.parts == 2) "$text (${step.part} of 2 — together these make ${step.from})" else text
}

// ============== Explanations ==============

private val rotationRe = Regex("^[xyz]")

fun moveExplanation(token: String, n: Int): String {
    val spec = parseMove(token, n) ?: return "Execute $token"
    val amount = if (spec.turns == 2) "180°" else if (spec.turns == 1) "90° clockwise" else "90° counter-clockwise"
    val faceName = faceNames.getValue(spec.face)
    if (rotationRe.containsMatchIn(token)) return "Rotate the whole cube $amount (like ${spec.face})"
    if (spec.layers.size > 1) {
        return "Turn the $faceName ${spec.layers.size} layers together (wide ${spec.face}) $amount"
    }
    if (spec.layers[0] == 1) return "Turn the $faceName face $amount"
    return "Turn the inner slice next to the $faceName face (layer ${spec.layers[0]}) $amount, as seen from the $faceName"
}
