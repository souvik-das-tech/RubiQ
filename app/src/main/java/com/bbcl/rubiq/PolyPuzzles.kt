// Pyraminx and Skewb, described as cut solids for the generic geometry.
package com.bbcl.rubiq

import kotlin.math.sqrt

// ============== Pyraminx ==============
//
// A regular tetrahedron held with one face towards you and one tip up. Vertices: U (top),
// L (front left), R (front right), B (back). Faces (WCA colours): F green, L red, R blue,
// D yellow. Moves (WCA): U L R B turn a tip with the layer below it (two layers), u l r b
// turn only the tip; both clockwise as seen looking at that tip.

private val s2 = sqrt(2.0)
private val s6 = sqrt(6.0)

val pyraVertices = linkedMapOf(
    'U' to P3(0.0, 3.0, 0.0),
    'L' to P3(-s6, -1.0, s2),
    'R' to P3(s6, -1.0, s2),
    'B' to P3(0.0, -1.0, -2 * s2),
)

/** Faces with the vertex opposite each one. */
val pyraFaceOpposite = linkedMapOf('F' to 'B', 'L' to 'R', 'R' to 'L', 'D' to 'U')

val pyraminxGeometry: PolyGeometry by lazy {
    val v = pyraVertices
    // The vertex radius is 3, so the faces lie at distance 1 from the centre; the layer and
    // tip cuts sit at 1/3 and 2/3 of the height (4) measured from the opposite face.
    val layerDepth = 1.0 / 3
    val tipDepth = 5.0 / 3
    PolyGeometry.build(
        faces = listOf(
            FaceDef('F', 'F', listOf(v.getValue('U'), v.getValue('L'), v.getValue('R'))),
            FaceDef('L', 'R', listOf(v.getValue('U'), v.getValue('B'), v.getValue('L'))),
            FaceDef('R', 'B', listOf(v.getValue('U'), v.getValue('R'), v.getValue('B'))),
            FaceDef('D', 'D', listOf(v.getValue('L'), v.getValue('B'), v.getValue('R'))),
        ),
        planes = v.values.flatMap { listOf(it.unit to layerDepth, it.unit to tipDepth) },
        axes = v.entries.flatMap { (k, p) ->
            listOf(AxisDef("$k", p.unit, layerDepth), AxisDef("${k.lowercaseChar()}", p.unit, tipDepth))
        },
        netRoot = 'F',
        netParent = mapOf('L' to 'F', 'R' to 'F', 'D' to 'F'),
        netUp = P3(0.0, 1.0, 0.0),
    )
}

// ============== Skewb ==============
//
// A cube cut by four planes through its centre. WCA fixed-corner notation: R, U, L, B turn
// the half of the puzzle around the corner at bottom-right-back, top-left-back,
// bottom-left-front and bottom-left-back, clockwise as seen looking at that corner. The
// top-front-right corner never moves.

val skewbCorners = linkedMapOf(
    "R" to P3(1.0, -1.0, -1.0), // DRB
    "U" to P3(-1.0, 1.0, -1.0), // UBL
    "L" to P3(-1.0, -1.0, 1.0), // DFL
    "B" to P3(-1.0, -1.0, -1.0), // DBL
)

val skewbGeometry: PolyGeometry by lazy {
    val h = 1.5
    fun p(x: Int, y: Int, z: Int) = P3(x * h, y * h, z * h)
    PolyGeometry.build(
        faces = listOf(
            FaceDef('U', 'U', listOf(p(-1, 1, -1), p(1, 1, -1), p(1, 1, 1), p(-1, 1, 1))),
            FaceDef('R', 'R', listOf(p(1, 1, 1), p(1, 1, -1), p(1, -1, -1), p(1, -1, 1))),
            FaceDef('F', 'F', listOf(p(-1, 1, 1), p(1, 1, 1), p(1, -1, 1), p(-1, -1, 1))),
            FaceDef('D', 'D', listOf(p(-1, -1, 1), p(1, -1, 1), p(1, -1, -1), p(-1, -1, -1))),
            FaceDef('L', 'L', listOf(p(-1, 1, -1), p(-1, 1, 1), p(-1, -1, 1), p(-1, -1, -1))),
            FaceDef('B', 'B', listOf(p(1, 1, -1), p(-1, 1, -1), p(-1, -1, -1), p(1, -1, -1))),
        ),
        planes = listOf(P3(1.0, 1.0, 1.0), P3(1.0, 1.0, -1.0), P3(1.0, -1.0, 1.0), P3(-1.0, 1.0, 1.0)).map { it.unit to 0.0 },
        axes = skewbCorners.map { (k, c) -> AxisDef(k, c.unit, 0.0) },
        netRoot = 'F',
        netParent = mapOf('U' to 'F', 'D' to 'F', 'L' to 'F', 'R' to 'F', 'B' to 'R'),
        netUp = P3(0.0, 1.0, 0.0),
    )
}
