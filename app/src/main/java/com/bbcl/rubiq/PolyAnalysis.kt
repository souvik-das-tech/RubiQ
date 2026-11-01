// Piece analysis shared by the Pyraminx and Skewb validators and solvers: which colours a
// solved puzzle would show (derived from pieces that never move), piece readings, twists
// and parities.
package com.bbcl.rubiq

import kotlin.math.PI

/**
 * A piece slot with its stickers in counter-clockwise order (seen from outside), starting
 * from a fixed reference sticker.
 */
class PieceSlot(
    val kind: String, // "tip", "center", "edge", "corner"
    val key: String, // vertex or corner name
    val stickers: List<Int>,
)

/** A slot's reading: which home slot's piece sits there and how far it is rotated. */
class PieceReading(
    val slot: Int,
    val target: Int, // -1 if no piece of the target colouring matches
    val twist: Int, // rotation of the reading relative to the home reading
    val mirrored: Boolean,
)

private fun rot(s: String, k: Int) = s.substring(k) + s.substring(0, k)

/** Reads every slot of `slots` in `state` against the colouring `target`. */
fun readPieces(state: String, target: String, slots: List<PieceSlot>): List<PieceReading> {
    val home = HashMap<String, Int>()
    for (i in slots.indices) {
        val h = slots[i].stickers.map { target[it] }.joinToString("")
        for (k in h.indices) home.putIfAbsent(rot(h, k), i)
    }
    return slots.indices.map { s ->
        val r = slots[s].stickers.map { state[it] }.joinToString("")
        val t = home[r]
        if (t != null) {
            val h = slots[t].stickers.map { target[it] }.joinToString("")
            val k = h.indices.first { rot(h, it) == r }
            return@map PieceReading(s, t, k, false)
        }
        val mirrored = r.length > 2 && home.containsKey(r.reversed())
        PieceReading(s, -1, 0, mirrored)
    }
}

/**
 * Parity of the permutation of individual stickers implied by a reading of 2-sticker
 * pieces (odd = an odd number of pieces is flipped, whatever the orientation convention).
 */
fun stickerParity(state: String, target: String, slots: List<PieceSlot>, readings: List<PieceReading>): Int {
    val index = HashMap<Int, Int>()
    for (s in slots) for (f in s.stickers) index[f] = index.size
    val perm = MutableList(index.size) { 0 }
    for (r in readings) {
        val here = slots[r.slot].stickers
        val from = slots[r.target].stickers
        for (f in here) {
            val src = from.first { target[it] == state[f] }
            perm[index.getValue(f)] = index.getValue(src)
        }
    }
    return permutationParity(perm)
}

/** Permutation parity of the pieces in `readings` (which must map a set of slots onto itself). */
fun pieceParity(readings: List<PieceReading>): Int {
    val local = readings.indices.associateBy { readings[it].slot }
    return permutationParity(readings.map { local.getValue(it.target) })
}

// ============== Pyraminx ==============

class PyraminxSlots(val tips: Map<Char, PieceSlot>, val centers: Map<Char, PieceSlot>, val edges: List<PieceSlot>)

val pyraminxSlots: PyraminxSlots by lazy {
    val g = pyraminxGeometry
    val tips = LinkedHashMap<Char, PieceSlot>()
    val centers = LinkedHashMap<Char, PieceSlot>()
    val edges = mutableListOf<PieceSlot>()
    for (piece in g.pieces) {
        val c = centroid(piece.map { g.stickers[it].center })
        val near = pyraVertices.entries.reduce { a, b -> if (c.dot(a.value) > c.dot(b.value)) a else b }
        if (piece.size == 2) {
            edges.add(PieceSlot("edge", "", piece))
        } else if (c.dot(near.value.unit) > 5.0 / 3) {
            tips[near.key] = PieceSlot("tip", "${near.key}", g.orderedPiece(piece))
        } else {
            centers[near.key] = PieceSlot("center", "${near.key}", g.orderedPiece(piece))
        }
    }
    edges.sortBy { it.stickers.min() }
    PyraminxSlots(tips, centers, edges)
}

/**
 * The solved colouring implied by the centres (which never leave their corner): each face
 * takes the one colour missing from the centre opposite it. Null if the centres do not
 * determine one.
 */
fun pyraminxTarget(state: String): String? {
    val faceColor = HashMap<Char, Char>()
    for ((face, vertex) in pyraFaceOpposite) {
        val colors = pyraminxSlots.centers.getValue(vertex).stickers.map { state[it] }.toSet()
        val missing = listOf('F', 'R', 'B', 'D').filter { it !in colors }
        if (colors.size != 3 || missing.size != 1) return null
        faceColor[face] = missing.first()
    }
    if (faceColor.values.toSet().size != 4) return null
    val g = pyraminxGeometry
    return g.stickers.joinToString("") { faceColor.getValue(g.faces[it.face].name).toString() }
}

// ============== Skewb ==============

class SkewbSlots(
    val centers: List<PieceSlot>,
    val corners: List<PieceSlot>, // reference sticker = the one on the U or D face
    val tetrad: List<Int>, // 0 for the fixed corner's tetrad, 1 for the other
    val fixedCorner: Int, // index of the top-front-right corner
)

fun cornerName(c: P3) = "${if (c.y > 0) 'U' else 'D'}${if (c.z > 0) 'F' else 'B'}${if (c.x > 0) 'R' else 'L'}"

val skewbSlots: SkewbSlots by lazy {
    val g = skewbGeometry
    val centers = mutableListOf<PieceSlot>()
    val corners = mutableListOf<PieceSlot>()
    for (piece in g.pieces) {
        if (piece.size == 1) {
            centers.add(PieceSlot("center", "${g.faces[g.stickers[piece[0]].face].name}", piece))
        } else {
            val c = centroid(piece.map { g.stickers[it].center })
            val ordered = g.orderedPiece(piece)
            val ud = ordered.indexOfFirst { g.faces[g.stickers[it].face].name in "UD" }
            corners.add(PieceSlot("corner", cornerName(c), ordered.subList(ud, ordered.size) + ordered.subList(0, ud)))
        }
    }
    centers.sortBy { it.stickers[0] }
    corners.sortBy { it.stickers[0] }
    fun tetradOf(s: PieceSlot): Int {
        val c = centroid(s.stickers.map { g.stickers[it].center })
        return listOf(c.x, c.y, c.z).count { it < 0 } % 2
    }

    SkewbSlots(centers, corners, corners.map(::tetradOf), corners.indexOfFirst { it.key == "UFR" })
}

/** The 24 whole-puzzle rotations of the solved Skewb colouring. */
val skewbOrientations: List<String> by lazy {
    val g = skewbGeometry
    val gens = listOf(
        g.rotationPerm(P3(1.0, 0.0, 0.0), -99.0, PI / 2),
        g.rotationPerm(P3(0.0, 1.0, 0.0), -99.0, PI / 2),
    )
    val seen = linkedSetOf(g.solvedState)
    val queue = ArrayDeque(listOf(g.solvedState))
    while (queue.isNotEmpty()) {
        val s = queue.removeLast()
        for (p in gens) {
            val next = permute(s, p)
            if (seen.add(next)) queue.addLast(next)
        }
    }
    seen.toList()
}

/**
 * The solved colouring implied by the fixed top-front-right corner, or null if that
 * corner's colours belong to no real corner.
 */
fun skewbTarget(state: String): String? {
    val fixed = skewbSlots.corners[skewbSlots.fixedCorner].stickers
    return skewbOrientations.firstOrNull { t -> fixed.all { t[it] == state[it] } }
}
