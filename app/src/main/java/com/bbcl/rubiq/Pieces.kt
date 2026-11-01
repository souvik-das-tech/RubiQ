// Piece slots (corners, edges/wings, centres) derived from the cube geometry.
package com.bbcl.rubiq

import java.util.concurrent.ConcurrentHashMap

class PieceSlots(
    val n: Int,
    /** Facelet triples, U/D facelet first, then clockwise (Kociemba order). */
    val corners: List<List<Int>>,
    /** Facelet pairs [primary, secondary]. Edges on 3x3, wings on 4x4 and 5x5. */
    val edges: List<List<Int>>,
    /** Single centre facelets (not including the fixed centres of odd cubes). */
    val centers: List<Int>,
    /** Face letter each facelet shows when solved. */
    val home: List<Char>,
    /** 5x5 only: middle edges (behave like 3x3 edges), and the two centre orbits. */
    val midges: List<List<Int>> = emptyList(),
    val xCenters: List<Int> = emptyList(),
    val tCenters: List<Int> = emptyList(),
)

private val slotCache = ConcurrentHashMap<Int, PieceSlots>()

private fun det(a: Vec3, b: Vec3, c: Vec3) =
    a[0] * (b[1] * c[2] - b[2] * c[1]) - a[1] * (b[0] * c[2] - b[2] * c[0]) + a[2] * (b[0] * c[1] - b[1] * c[0])

/** Groups facelets into orbits under all single-layer quarter turns. */
private fun faceletOrbits(n: Int): IntArray {
    val size = 6 * n * n
    val parent = IntArray(size) { it }
    fun find(x: Int): Int {
        if (parent[x] == x) return x
        parent[x] = find(parent[x])
        return parent[x]
    }

    for (face in faceOrder) {
        for (d in 1..n) {
            val perm = movePermutation(if (d == 1) "$face" else "$d$face", n)
            for (i in 0 until size) {
                val a = find(i)
                val b = find(perm[i].toInt())
                if (a != b) parent[a] = b
            }
        }
    }
    return IntArray(size) { find(it) }
}

fun getPieceSlots(n: Int): PieceSlots = slotCache.getOrPut(n) {
    val geo = getGeometry(n)
    val home = geo.stickers.map { it.face }
    val corners = mutableListOf<List<Int>>()
    val edges = mutableListOf<List<Int>>()
    val midges = mutableListOf<List<Int>>()
    val centers = mutableListOf<Int>()
    val xCenters = mutableListOf<Int>()
    val tCenters = mutableListOf<Int>()
    val mid = if (n % 2 == 1) (n - 1) / 2 else -1
    // Cubie coordinates strictly inside the cube (not on an outer layer) along each axis.
    fun inner(g: Vec3) = g.filter { it != 0 && it != n - 1 }
    fun isUD(f: Char) = f == 'U' || f == 'D'
    fun isFB(f: Char) = f == 'F' || f == 'B'
    fun primaryOf(st: List<Int>) = st.firstOrNull { isUD(home[it]) } ?: st.first { isFB(home[it]) }

    val orbit = if (n >= 4) faceletOrbits(n) else null
    var wingOrbit = -1

    for (cubie in geo.cubies) {
        val st = cubie.stickers
        if (st.size == 3) {
            val ud = st.first { isUD(home[it]) }
            val rest = st.filter { it != ud }
            var a = rest[0]
            var b = rest[1]
            if (det(faceNormals.getValue(home[ud]), faceNormals.getValue(home[a]), faceNormals.getValue(home[b])) > 0) {
                val t = a
                a = b
                b = t
            }
            corners.add(listOf(ud, a, b))
        } else if (st.size == 2 && n >= 5 && inner(cubie.grid).single() == mid) {
            // Middle edge: oriented like a 3x3 edge.
            val primary = primaryOf(st)
            midges.add(listOf(primary, st.first { it != primary }))
        } else if (st.size == 2) {
            val primary: Int
            if (orbit != null) {
                // Wing facelets split into two orbits; the primary facelet is the one in the
                // orbit chosen first, so a wing's primary facelet always moves to a primary facelet.
                if (wingOrbit == -1) wingOrbit = orbit[st[0]]
                primary = st.firstOrNull { orbit[it] == wingOrbit }
                    ?: throw IllegalStateException("Wing orbit analysis failed")
            } else {
                primary = primaryOf(st)
            }
            edges.add(listOf(primary, st.first { it != primary }))
        } else if (st.size == 1) {
            val g = inner(cubie.grid)
            val onMid = g.count { it == mid }
            if (onMid == 2) continue // fixed centre of an odd cube
            centers.add(st[0])
            if (n >= 5) (if (onMid == 1) tCenters else xCenters).add(st[0])
        }
    }

    if (orbit != null && edges.any { orbit[it[1]] == wingOrbit }) {
        throw IllegalStateException("Wing orbit analysis failed")
    }

    corners.sortBy { it[0] }
    edges.sortBy { it[0] }
    midges.sortBy { it[0] }
    centers.sort()
    xCenters.sort()
    tCenters.sort()
    PieceSlots(n, corners, edges, centers, home, midges, xCenters, tCenters)
}

fun permutationParity(perm: List<Int>): Int {
    val seen = BooleanArray(perm.size)
    var parity = 0
    for (i in perm.indices) {
        if (seen[i]) continue
        var len = 0
        var j = i
        while (!seen[j]) {
            seen[j] = true
            len++
            j = perm[j]
        }
        parity = parity xor ((len - 1) and 1)
    }
    return parity
}

class CornerReading(
    val slot: Int,
    val target: Int, // home slot of the piece, -1 if unrecognisable
    val twist: Int,
    val mirrored: Boolean,
)

class EdgeReading(
    val slot: Int,
    val target: Int,
    val flip: Int, // 3x3: 0/1; 4x4: 1 means the wing reads mirrored (physically impossible)
)

fun readCorners(state: String, slots: PieceSlots): List<CornerReading> {
    val homeKey = HashMap<String, Int>()
    for (i in slots.corners.indices) {
        homeKey[slots.corners[i].map { slots.home[it] }.joinToString("")] = i
    }

    return slots.corners.indices.map { slot ->
        val colors = slots.corners[slot].map { state[it] }
        val udIdx = (0 until 3).filter { colors[it] == 'U' || colors[it] == 'D' }
        if (udIdx.size != 1) return@map CornerReading(slot, -1, 0, false)
        val k = udIdx[0]
        val rotated = "${colors[k]}${colors[(k + 1) % 3]}${colors[(k + 2) % 3]}"
        val target = homeKey[rotated]
        if (target != null) return@map CornerReading(slot, target, k, false)
        val mirrored = homeKey.containsKey("${colors[k]}${colors[(k + 2) % 3]}${colors[(k + 1) % 3]}")
        CornerReading(slot, -1, k, mirrored)
    }
}

/** Reads the wings (or 3x3 edges); pass `pairs = slots.midges` to read 5x5 middle edges. */
fun readEdges(state: String, slots: PieceSlots, pairs: List<List<Int>>? = null): List<EdgeReading> {
    val edges = pairs ?: slots.edges
    val homeKey = HashMap<String, Int>()
    for (i in edges.indices) {
        homeKey["${slots.home[edges[i][0]]}${slots.home[edges[i][1]]}"] = i
    }

    return edges.indices.map { slot ->
        val p = edges[slot][0]
        val s = edges[slot][1]
        val direct = homeKey["${state[p]}${state[s]}"]
        if (direct != null) return@map EdgeReading(slot, direct, 0)
        val flipped = homeKey["${state[s]}${state[p]}"]
        if (flipped != null) return@map EdgeReading(slot, flipped, 1)
        EdgeReading(slot, -1, 0)
    }
}

fun pieceName(facelets: List<Int>, slots: PieceSlots): String = facelets.map { slots.home[it] }.joinToString("-")
