// 3D view and flat net for the cut-polyhedron puzzles (Pyraminx, Skewb), drawn on a Compose
// Canvas like the cube view.
//
// The puzzle is a convex solid, so drawing its front-facing faces needs no depth sorting.
// While a move animates, the turning part and the rest are each convex too (each is the
// solid on one side of the cut plane, closed off by the plane's cross-section), so they are
// drawn as two solids, the one behind the cut plane first.
package com.bbcl.rubiq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/** One polygon of a solid to draw, with its outward normal and face (-1 for a cut cap). */
private class SolidPolygon(val points: List<P3>, val normal: P3, val face: Int)

/** One convex solid to draw: its polygons and the stickers it carries. */
private class Solid(val polygons: List<SolidPolygon>, val stickers: Set<Int>, val axis: P3? = null, val angle: Double = 0.0)

class PolyViewState(
    val geometry: PolyGeometry,
    private val viewDistance: Double,
    defaultYaw: Double,
    defaultPitch: Double,
) : PuzzleViewState<PolyMove>(defaultYaw, defaultPitch) {
    override fun animationFor(token: String): Pair<PolyMove, Double>? {
        val move = geometry.parse(token) ?: return null
        return move to move.angle
    }

    fun draw(scope: DrawScope, facelets: String, highlight: Set<Int>) = with(scope) {
        val g = geometry
        val state = override ?: facelets
        val a = anim
        hits.clear()
        if (state.length != g.stickers.size) return@with

        val cam = Camera(yaw, pitch, viewDistance, zoom, size.width, size.height)
        val eye = cam.eye

        val allStickers = g.stickers.indices.toSet()
        val solids: List<Solid> = if (a == null) {
            listOf(Solid(g.faces.mapIndexed { f, face -> SolidPolygon(face.polygon, face.normal, f) }, allStickers))
        } else {
            val u = a.data.axis.dir
            val d = a.data.axis.depth
            val cap = crossSection(g.faces.map { it.polygon }, u, d)
            val still = mutableListOf<SolidPolygon>()
            val turning = mutableListOf<SolidPolygon>()
            for ((f, face) in g.faces.withIndex()) {
                val (below, above) = splitPolygon(face.polygon, u, d)
                if (below.size >= 3 && polygonArea(below) > 1e-6) still.add(SolidPolygon(below, face.normal, f))
                if (above.size >= 3 && polygonArea(above) > 1e-6) turning.add(SolidPolygon(above, face.normal, f))
            }
            if (cap.isNotEmpty()) {
                still.add(SolidPolygon(cap, u, -1))
                turning.add(SolidPolygon(cap, u * -1.0, -1))
            }
            val moving = g.stickers.filter { it.center.dot(u) > d }.map { it.index }.toSet()
            val stillSolid = Solid(still, allStickers - moving)
            val turningSolid = Solid(turning, moving, u, animAngle)
            // The eye's side of the cut plane: draw the solid on the other side first.
            val eyeSide = cam.view(u).dot(eye - cam.view(u * d)) > 0
            if (eyeSide) listOf(stillSolid, turningSolid) else listOf(turningSolid, stillSolid)
        }

        for (solid in solids) {
            fun place(p: P3) = cam.view(if (solid.axis == null) p else p.rotate(solid.axis, solid.angle))
            val origin = place(P3.ZERO)
            for (poly in solid.polygons) {
                val nv = place(poly.normal) - origin
                val pts = poly.points.map(::place)
                if (nv.dot(eye - centroid(pts)) <= 0) continue // back face
                val lambert = nv.dot(lightDir)
                drawPath(polygonPath(pts.map(cam::project)), shade(bodyColor, lambert))
                if (poly.face < 0) continue
                for (st in g.stickers) {
                    if (st.face != poly.face || st.index !in solid.stickers) continue
                    val c = st.center
                    val corners = st.polygon.map { cam.project(place(c + (it - c) * 0.84)) }
                    val path = roundedPath(corners, 0.2f)
                    drawPath(path, shade(stickerColor(state[st.index]), lambert))
                    if (st.index in highlight) drawHighlight(path, time)
                    hits.add(st.index to corners)
                }
            }
        }
    }
}

@Composable
fun PolyView(
    geometry: PolyGeometry,
    facelets: String,
    controller: CubeViewController,
    highlight: List<Int>,
    autoRotate: Boolean,
    paintEnabled: Boolean,
    onStickerTap: (Int) -> Unit,
    viewDistance: Double,
    defaultYaw: Double,
    defaultPitch: Double,
    modifier: Modifier = Modifier,
) {
    val state = remember(geometry) { PolyViewState(geometry, viewDistance, defaultYaw, defaultPitch) }
    PuzzleViewEffects(state, controller, facelets, highlight, autoRotate)
    val hl = remember(highlight) { highlight.toSet() }
    Canvas(
        modifier
            .fillMaxSize()
            .orbitGestures(state) { p -> if (paintEnabled) state.stickerAt(p)?.let(onStickerTap) },
    ) { state.draw(this, facelets, hl) }
}

// ===================== Flat net =====================

@Composable
fun PolyNetView(
    geometry: PolyGeometry,
    facelets: String,
    highlight: List<Int>,
    paintEnabled: Boolean,
    onStickerTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hits = remember { ArrayList<Pair<Int, List<Offset>>>() }
    val measurer = rememberTextMeasurer()
    val hl = remember(highlight) { highlight.toSet() }
    val enabled = rememberUpdatedState(paintEnabled)
    val onTap = rememberUpdatedState(onStickerTap)
    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(geometry) {
                detectTapGestures { p ->
                    if (enabled.value) hits.lastOrNull { pointInPolygon(p, it.second) }?.let { onTap.value(it.first) }
                }
            },
    ) {
        val g = geometry
        hits.clear()
        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (st in g.stickers) {
            for ((x, y) in st.net) {
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
            }
        }
        val scale = min((size.width - 16) / (maxX - minX), (size.height - 16) / (maxY - minY))
        val ox = (size.width - (maxX - minX) * scale) / 2
        val oy = (size.height - (maxY - minY) * scale) / 2
        fun at(x: Double, y: Double) = Offset((ox + (x - minX) * scale).toFloat(), (oy + (maxY - y) * scale).toFloat())

        for (st in g.stickers) {
            val pts = st.net.map { (x, y) -> at(x, y) }
            val c = pts.reduce { a, b -> a + b } / pts.size.toFloat()
            val corners = pts.map { c + (it - c) * 0.86f }
            val path = roundedPath(corners, 0.18f)
            drawPath(path, stickerColor(if (facelets.length > st.index) facelets[st.index] else 'X'))
            if (st.index in hl) drawPath(path, AppColors.rose, style = Stroke(2.5f * density))
            hits.add(st.index to corners)
        }
        // Face letters, faint, in the middle of each face.
        val style = TextStyle(fontSize = (scale * 0.9 / density / fontScale).sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.18f))
        for (f in g.faces.indices) {
            val pts = g.stickers.filter { it.face == f }.flatMap { st -> st.net.map { (x, y) -> at(x, y) } }
            val c = pts.reduce { a, b -> a + b } / pts.size.toFloat()
            val layout = measurer.measure("${g.faces[f].name}", style)
            drawText(layout, topLeft = c - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
    }
}
