// Generic geometry for "cut polyhedron" puzzles (Pyraminx, Skewb): a convex solid cut by
// planes. Stickers are the regions the planes cut each face into; a move turns everything
// on the far side of one plane about that plane's normal. From this the module derives the
// sticker list, the pieces, move permutations and a flat net (faces unfolded about shared
// edges), so a new puzzle is just a list of faces, planes and move axes.
package com.bbcl.rubiq

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

data class P3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: P3) = P3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: P3) = P3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = P3(x * s, y * s, z * s)
    fun dot(o: P3) = x * o.x + y * o.y + z * o.z
    fun cross(o: P3) = P3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length get() = sqrt(dot(this))
    val unit get() = this * (1 / length)
    fun near(o: P3, eps: Double = 1e-6) = (this - o).length < eps

    /** Rotation about the unit axis `k` by `angle` (right-handed; Rodrigues' formula). */
    fun rotate(k: P3, angle: Double): P3 {
        val c = cos(angle)
        val s = sin(angle)
        return this * c + k.cross(this) * s + k * (k.dot(this) * (1 - c))
    }

    companion object {
        val ZERO = P3(0.0, 0.0, 0.0)
    }
}

fun centroid(pts: List<P3>): P3 = pts.fold(P3.ZERO) { a, p -> a + p } * (1.0 / pts.size)

/** Splits a convex polygon by the plane dir·p = d into the parts below and above it. */
fun splitPolygon(poly: List<P3>, dir: P3, d: Double): Pair<List<P3>, List<P3>> {
    val below = mutableListOf<P3>()
    val above = mutableListOf<P3>()
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        val da = a.dot(dir) - d
        val db = b.dot(dir) - d
        if (da <= 1e-9) below.add(a)
        if (da >= -1e-9) above.add(a)
        if ((da < -1e-9 && db > 1e-9) || (da > 1e-9 && db < -1e-9)) {
            val p = a + (b - a) * (da / (da - db))
            below.add(p)
            above.add(p)
        }
    }
    return below to above
}

fun polygonArea(poly: List<P3>): Double {
    var s = P3.ZERO
    for (i in poly.indices) s += poly[i].cross(poly[(i + 1) % poly.size])
    return s.length / 2
}

/**
 * The cross-section of a convex solid (given by its face polygons) with the plane
 * dir·p = d, ordered counter-clockwise around `dir`.
 */
fun crossSection(faces: List<List<P3>>, dir: P3, d: Double): List<P3> {
    val pts = mutableListOf<P3>()
    for (poly in faces) {
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val da = a.dot(dir) - d
            val db = b.dot(dir) - d
            if ((da < 0) != (db < 0)) {
                val p = a + (b - a) * (da / (da - db))
                if (pts.none { it.near(p, 1e-7) }) pts.add(p)
            }
        }
    }
    if (pts.size < 3) return emptyList()
    val c = centroid(pts)
    val u = (pts[0] - c).unit
    val v = dir.cross(u)
    pts.sortBy { atan2((it - c).dot(v), (it - c).dot(u)) }
    return pts
}

class FaceDef(
    val name: Char, // U, F, ...
    val color: Char, // colour code painted on this face when solved
    val polygon: List<P3>,
)

class AxisDef(
    val letter: String, // move letter, e.g. "U" or "u"
    val dir: P3, // unit vector; the move turns everything with dir·p > depth
    val depth: Double,
)

class PolyFace(val name: Char, val color: Char, val polygon: List<P3>, val normal: P3)

class PolySticker(
    val index: Int,
    val face: Int,
    val polygon: List<P3>,
    val center: P3,
    /** Polygon in the flat net (unit-free 2D coordinates, y up). */
    val net: List<Pair<Double, Double>>,
)

class PolyMove(
    val axis: AxisDef,
    val turns: Int, // 1 = clockwise as seen looking at the axis tip, -1 = counter-clockwise
) {
    /** Right-handed rotation angle about `axis.dir`. */
    val angle get() = -turns * 2 * PI / 3
}

class PolyGeometry private constructor(
    val faces: List<PolyFace>,
    val stickers: List<PolySticker>,
    val axes: Map<String, AxisDef>,
    /** Stickers grouped into physical pieces (same side of every cut plane). */
    val pieces: List<List<Int>>,
    val planes: List<Pair<P3, Double>>,
) {
    private val perms = ConcurrentHashMap<String, ShortArray>()

    fun parse(token: String): PolyMove? {
        if (token.isEmpty()) return null
        val prime = token.endsWith("'")
        val letter = if (prime) token.dropLast(1) else token
        val axis = axes[letter] ?: return null
        return PolyMove(axis, if (prime) -1 else 1)
    }

    fun stickerAt(center: P3): Int =
        stickers.firstOrNull { it.center.near(center, 1e-5) }?.index
            ?: throw IllegalStateException("Puzzle geometry mismatch")

    /** Gather permutation of rotating every sticker with dir·c > depth about dir by angle. */
    fun rotationPerm(dir: P3, depth: Double, angle: Double): ShortArray {
        val perm = ShortArray(stickers.size) { it.toShort() }
        for (s in stickers) {
            if (s.center.dot(dir) <= depth) continue
            perm[stickerAt(s.center.rotate(dir, angle))] = s.index.toShort()
        }
        return perm
    }

    fun movePermutation(token: String): ShortArray = perms.getOrPut(token) {
        val m = parse(token) ?: throw IllegalArgumentException("Invalid move \"$token\"")
        rotationPerm(m.axis.dir, m.axis.depth, m.angle)
    }

    fun applyMove(state: String, token: String): String = permute(state, movePermutation(token))

    val solvedState: String by lazy { stickers.joinToString("") { faces[it.face].color.toString() } }

    /** The stickers of a piece in counter-clockwise order as seen from outside the piece. */
    fun orderedPiece(piece: List<Int>): List<Int> {
        if (piece.size < 3) return piece
        val c = centroid(piece.map { stickers[it].center })
        val axis = c.unit
        val u = (stickers[piece[0]].center - c).unit
        val v = axis.cross(u)
        fun ang(i: Int): Double {
            val d = stickers[i].center - c
            return atan2(d.dot(v), d.dot(u))
        }
        return piece.sortedBy(::ang)
    }

    companion object {
        /**
         * Builds the geometry. `netParent` maps each face (except `netRoot`) to the face it
         * unfolds onto; `netUp` is the direction that points up in the net's root face.
         */
        fun build(
            faces: List<FaceDef>,
            planes: List<Pair<P3, Double>>,
            axes: List<AxisDef>,
            netRoot: Char,
            netParent: Map<Char, Char>,
            netUp: P3,
        ): PolyGeometry {
            val solidCenter = centroid(faces.flatMap { it.polygon })
            val polyFaces = faces.map { f ->
                val c = centroid(f.polygon)
                var nrm = (f.polygon[1] - f.polygon[0]).cross(f.polygon[2] - f.polygon[0]).unit
                if (nrm.dot(c - solidCenter) < 0) nrm *= -1.0
                PolyFace(f.name, f.color, f.polygon, nrm)
            }

            // Net: each face is carried into its parent's plane by hinging about the shared edge.
            val byName = polyFaces.indices.associateBy { polyFaces[it].name }
            val hinges = HashMap<Int, Triple<P3, P3, Double>>() // face -> (edge point, edge dir, angle)
            for ((childName, parentName) in netParent) {
                val child = polyFaces[byName.getValue(childName)]
                val parent = polyFaces[byName.getValue(parentName)]
                val shared = child.polygon.filter { p -> parent.polygon.any { it.near(p) } }
                if (shared.size != 2) throw IllegalStateException("Faces $childName and $parentName share no edge")
                val dir = (shared[1] - shared[0]).unit
                val angle = acos(child.normal.dot(parent.normal).coerceIn(-1.0, 1.0))
                val sign = if (child.normal.rotate(dir, angle).near(parent.normal, 1e-6)) 1.0 else -1.0
                hinges[byName.getValue(childName)] = Triple(shared[0], dir, sign * angle)
            }
            val rootIndex = byName.getValue(netRoot)
            val root = polyFaces[rootIndex]
            val up = (netUp - root.normal * netUp.dot(root.normal)).unit
            val right = up.cross(root.normal)
            fun unfold(face: Int, point: P3): Pair<Double, Double> {
                var f = face
                var p = point
                while (f != rootIndex) {
                    val (a, dir, angle) = hinges.getValue(f)
                    p = a + (p - a).rotate(dir, angle)
                    f = byName.getValue(netParent.getValue(polyFaces[f].name))
                }
                return p.dot(right) to p.dot(up)
            }

            // Stickers: each face cut by every plane, ordered top-to-bottom, left-to-right in the net.
            val stickers = mutableListOf<PolySticker>()
            for (f in polyFaces.indices) {
                var regions = listOf(polyFaces[f].polygon)
                for ((dir, d) in planes) {
                    regions = regions.flatMap { r ->
                        val (below, above) = splitPolygon(r, dir, d)
                        listOf(below, above).filter { it.size >= 3 && polygonArea(it) > 1e-6 }
                    }
                }
                val withNet = regions.map { r ->
                    val c = centroid(r)
                    Triple(r, c, r.map { unfold(f, it) }) to unfold(f, c)
                }.sortedWith { a, b ->
                    val dy = b.second.second - a.second.second
                    if (abs(dy) > 1e-4) sign(dy).toInt() else sign(a.second.first - b.second.first).toInt()
                }
                for ((sticker, _) in withNet) {
                    val (poly, c, net) = sticker
                    stickers.add(PolySticker(stickers.size, f, poly, c, net))
                }
            }

            // Pieces: stickers on the same side of every plane belong to one piece.
            val groups = LinkedHashMap<String, MutableList<Int>>()
            for (s in stickers) {
                val key = planes.joinToString("") { (dir, d) -> if (s.center.dot(dir) > d) "+" else "-" }
                groups.getOrPut(key) { mutableListOf() }.add(s.index)
            }

            return PolyGeometry(polyFaces, stickers, axes.associateBy { it.letter }, groups.values.toList(), planes)
        }
    }
}
