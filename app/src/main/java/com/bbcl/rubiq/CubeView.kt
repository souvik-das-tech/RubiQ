// Interactive 3D cube drawn on a Compose Canvas (no 3D engine needed: a cube is a few
// dozen convex boxes, drawn far-to-near with back faces culled).
//
// The view never owns cube state: it renders the facelet string it is given. A move is
// animated by rotating the affected cubies; when it ends the view shows the next state
// until the parent passes it in.
package com.bbcl.rubiq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/** What a 3D puzzle view offers its controller (cube and Pyraminx/Skewb views alike). */
interface PuzzleAnimator {
    /** Animates `token` on the current state; returns when the turn has finished. */
    suspend fun animateMove(token: String, durationMs: Int, next: String)
    fun recenter()
}

class CubeViewController {
    private var animator: PuzzleAnimator? = null

    fun attach(view: PuzzleAnimator) {
        animator = view
    }

    fun detach(view: PuzzleAnimator) {
        if (animator === view) animator = null
    }

    suspend fun animateMove(token: String, durationMs: Int, next: String) {
        animator?.animateMove(token, durationMs, next)
    }

    fun recenter() {
        animator?.recenter()
    }
}

// ---------- Shared camera, gestures and move queue ----------

private const val MAX_PITCH = 1.45

class MoveAnim<T>(val data: T, val target: Double, val durationMs: Int, val next: String) {
    val done = CompletableDeferred<Unit>()
    val startNanos = System.nanoTime()
}

abstract class PuzzleViewState<T>(private val defaultYaw: Double, private val defaultPitch: Double) : PuzzleAnimator {
    var yaw by mutableDoubleStateOf(defaultYaw)
    var pitch by mutableDoubleStateOf(defaultPitch)
    var zoom by mutableDoubleStateOf(1.0)
    var time by mutableDoubleStateOf(0.0)
    private var spinVelocity by mutableDoubleStateOf(0.0) // yaw radians per second after a fling

    /** Mirrors of the view's parameters that decide whether frames are needed. */
    var autoRotate by mutableStateOf(false)
    var hasHighlight by mutableStateOf(false)

    var anim by mutableStateOf<MoveAnim<T>?>(null)
        private set
    var animAngle by mutableDoubleStateOf(0.0)
        private set

    /** State shown after an animation until the parent catches up. */
    var override by mutableStateOf<String?>(null)

    /** Sticker outlines drawn in the last frame, near to far is last to first. */
    val hits = ArrayList<Pair<Int, List<Offset>>>()

    private val queue = Mutex()
    private var disposed = false

    val needsTicks get() = anim != null || autoRotate || hasHighlight || abs(spinVelocity) > 0.01

    /** Describes the animation of `token`, or null if it cannot be animated. */
    protected abstract fun animationFor(token: String): Pair<T, Double>?

    fun tick(dt: Double) {
        time += dt
        if (autoRotate) yaw += dt * 0.35
        if (abs(spinVelocity) > 0.01) {
            yaw += spinVelocity * dt
            spinVelocity *= 0.04.pow(dt)
        } else {
            spinVelocity = 0.0
        }
        val a = anim ?: return
        val p = min((System.nanoTime() - a.startNanos) / 1e6 / a.durationMs, 1.0)
        animAngle = a.target * easeInOutCubic(p)
        if (p >= 1) {
            override = a.next
            anim = null
            a.done.complete(Unit)
        }
    }

    private fun easeInOutCubic(p: Double) = if (p < 0.5) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2

    override suspend fun animateMove(token: String, durationMs: Int, next: String) = queue.withLock {
        val spec = if (disposed || durationMs < 30) null else animationFor(token)
        if (spec == null) {
            override = next
            return@withLock
        }
        val a = MoveAnim(spec.first, spec.second, durationMs, next)
        animAngle = 0.0
        anim = a
        a.done.await()
    }

    override fun recenter() {
        yaw = defaultYaw
        pitch = defaultPitch
        zoom = 1.0
        spinVelocity = 0.0
    }

    fun dispose() {
        disposed = true
        anim?.done?.complete(Unit)
        anim = null
    }

    fun onGestureStart() {
        spinVelocity = 0.0
    }

    fun onTransform(pan: Offset, zoomChange: Float, pointers: Int) {
        if (pointers >= 2) zoom = (zoom * zoomChange).coerceIn(0.6, 1.8)
        yaw += pan.x * 0.011
        pitch = (pitch + pan.y * 0.011).coerceIn(-MAX_PITCH, MAX_PITCH)
    }

    fun onFling(velocityX: Float) {
        if (abs(velocityX) > 300) spinVelocity = velocityX * 0.011 * 0.6
    }

    fun stickerAt(p: Offset): Int? = hits.lastOrNull { pointInPolygon(p, it.second) }?.first
}

/** Drives `tick` once per frame, but only while the view has something to animate. */
@Composable
fun TickEffect(state: PuzzleViewState<*>) {
    LaunchedEffect(state) {
        while (true) {
            snapshotFlow { state.needsTicks }.first { it }
            var last = 0L
            while (state.needsTicks) {
                withFrameNanos { now ->
                    state.tick(if (last == 0L) 0.016 else (now - last) / 1e9)
                    last = now
                }
            }
        }
    }
}

/** Drag to rotate (with fling), pinch to zoom, tap to pick a sticker. */
fun Modifier.orbitGestures(state: PuzzleViewState<*>, onTap: (Offset) -> Unit) = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        state.onGestureStart()
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var moved = false
        var multi = false
        var travel = Offset.Zero
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) multi = true
            val pan = event.calculatePan()
            val zoomChange = event.calculateZoom()
            if (!moved) {
                travel += pan
                if (travel.getDistance() > viewConfiguration.touchSlop || zoomChange != 1f) moved = true
            }
            if (moved) {
                state.onTransform(pan, zoomChange, pressed)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
            event.changes.firstOrNull { it.id == down.id }?.let { tracker.addPosition(it.uptimeMillis, it.position) }
        } while (event.changes.any { it.pressed })
        if (!moved && !multi) onTap(down.position) else if (!multi) state.onFling(tracker.calculateVelocity().x)
    }
}

fun pointInPolygon(p: Offset, poly: List<Offset>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** A polygon with rounded corners (each corner cut `t` of the way along its edges). */
fun roundedPath(q: List<Offset>, t: Float): Path {
    val path = Path()
    val n = q.size
    for (k in 0 until n) {
        val p = q[k]
        val prev = q[(k + n - 1) % n]
        val next = q[(k + 1) % n]
        val a = p + (prev - p) * t
        val b = p + (next - p) * t
        if (k == 0) path.moveTo(a.x, a.y) else path.lineTo(a.x, a.y)
        path.quadraticTo(p.x, p.y, b.x, b.y)
    }
    path.close()
    return path
}

fun polygonPath(pts: List<Offset>): Path = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    close()
}

val bodyColor = Color(0xFF0B1120)
val lightDir = P3(0.33, 0.77, 0.55)

fun shade(c: Color, lambert: Double): Color {
    val b = (0.62 + 0.38 * lambert.coerceIn(0.0, 1.0)).toFloat()
    return Color(c.red * b, c.green * b, c.blue * b)
}

/** Pulsing red overlay and outline for stickers flagged by the validator. */
fun DrawScope.drawHighlight(path: Path, time: Double) {
    val pulse = ((sin(time * 8) + 1) / 2).toFloat()
    drawPath(path, Color(1f, 40 / 255f, 40 / 255f, 0.15f + 0.6f * pulse))
    drawPath(path, Color(1f, 90 / 255f, 90 / 255f, 0.5f + 0.5f * pulse), style = Stroke(2.5f * density))
}

/** View rotation Rx(pitch) * Ry(yaw), and a perspective projection onto the canvas. */
class Camera(yaw: Double, pitch: Double, val dist: Double, zoom: Double, width: Float, height: Float) {
    private val cy = cos(yaw)
    private val sy = sin(yaw)
    private val cp = cos(pitch)
    private val sp = sin(pitch)
    val eye = P3(0.0, 0.0, dist)
    private val focal = (min(width, height) / 2) / tan(20 * PI / 180) * zoom
    private val cx = width / 2
    private val cyy = height / 2

    fun view(v: P3): P3 {
        val x1 = v.x * cy + v.z * sy
        val z1 = -v.x * sy + v.z * cy
        return P3(x1, v.y * cp - z1 * sp, v.y * sp + z1 * cp)
    }

    fun project(v: P3): Offset {
        val d = dist - v.z
        return Offset((cx + focal * v.x / d).toFloat(), (cyy - focal * v.y / d).toFloat())
    }
}

// ---------- The cube ----------

private val faceAxes = listOf(
    P3(1.0, 0.0, 0.0), P3(-1.0, 0.0, 0.0), P3(0.0, 1.0, 0.0), P3(0.0, -1.0, 0.0), P3(0.0, 0.0, 1.0), P3(0.0, 0.0, -1.0),
)

/** Two in-plane axes for a face with the given normal index. */
private fun planeAxes(face: Int): Pair<P3, P3> = when (face / 2) {
    0 -> P3(0.0, 1.0, 0.0) to P3(0.0, 0.0, 1.0)
    1 -> P3(1.0, 0.0, 0.0) to P3(0.0, 0.0, 1.0)
    else -> P3(1.0, 0.0, 0.0) to P3(0.0, 1.0, 0.0)
}

private fun faceIndexOf(n: Vec3): Int = when {
    n[0] != 0 -> if (n[0] > 0) 0 else 1
    n[1] != 0 -> if (n[1] > 0) 2 else 3
    else -> if (n[2] > 0) 4 else 5
}

private const val CUBIE_HALF = 0.475
private const val STICKER_HALF = 0.4085
private const val STICKER_LIFT = 0.481

// Default view: looking from front-right and above, like the web app's camera (6, 5, 8).
private val DEFAULT_YAW = -atan2(6.0, 8.0)
private val DEFAULT_PITCH = atan2(5.0, 10.0)

private class CubeLayout(n: Int) {
    val centers = ArrayList<P3>() // every cubie of the N^3 block, interior ones included
    val pos2 = ArrayList<Vec3>()
    val stickerOf: IntArray // [cubie * 6 + face] -> facelet index, or -1

    init {
        val index = HashMap<String, Int>()
        val h = (n - 1) / 2.0
        for (x in 0 until n) {
            for (y in 0 until n) {
                for (z in 0 until n) {
                    index["$x,$y,$z"] = centers.size
                    centers.add(P3(x - h, y - h, z - h))
                    pos2.add(intArrayOf(2 * x - (n - 1), 2 * y - (n - 1), 2 * z - (n - 1)))
                }
            }
        }
        stickerOf = IntArray(centers.size * 6) { -1 }
        for (s in getGeometry(n).stickers) {
            stickerOf[index.getValue(s.grid.joinToString(",")) * 6 + faceIndexOf(s.normal)] = s.index
        }
    }
}

class CubeTurn(val axis: P3, val moving: Set<Int>)

class CubeViewState(val n: Int) : PuzzleViewState<CubeTurn>(DEFAULT_YAW, DEFAULT_PITCH) {
    private val layout = CubeLayout(n)

    override fun animationFor(token: String): Pair<CubeTurn, Double>? {
        val spec = parseMove(token, n) ?: return null
        val quarter = if (spec.turns == 3) -1 else spec.turns
        val normal = faceNormals.getValue(spec.face)
        val moving = layout.pos2.indices.filter { layerDepth(layout.pos2[it], spec.face, n) in spec.layers }.toSet()
        val axis = P3(normal[0].toDouble(), normal[1].toDouble(), normal[2].toDouble())
        return CubeTurn(axis, moving) to -(PI / 2) * quarter
    }

    fun draw(scope: DrawScope, facelets: String, highlight: Set<Int>) = with(scope) {
        val state = override ?: facelets
        val a = anim
        val angle = animAngle
        hits.clear()
        if (state.length != 6 * n * n) return@with

        val cam = Camera(yaw, pitch, n * 3.73, zoom, size.width, size.height)
        val eye = cam.eye

        // Cubies sorted far to near.
        val viewCenters = layout.centers.mapIndexed { i, c ->
            cam.view(if (a != null && i in a.data.moving) c.rotate(a.data.axis, angle) else c)
        }
        val depth = viewCenters.map { (eye - it).let { d -> d.dot(d) } }
        val order = layout.centers.indices.sortedByDescending { depth[it] }

        for (i in order) {
            val rotating = a != null && i in a.data.moving
            val center = viewCenters[i]
            val bodies = ArrayList<Pair<List<Offset>, Color>>()
            for (f in 0 until 6) {
                var normal = faceAxes[f]
                var (u, v) = planeAxes(f)
                if (rotating) {
                    normal = normal.rotate(a.data.axis, angle)
                    u = u.rotate(a.data.axis, angle)
                    v = v.rotate(a.data.axis, angle)
                }
                val nv = cam.view(normal)
                val uv = cam.view(u)
                val vv = cam.view(v)
                val faceCenter = center + nv * CUBIE_HALF
                if (nv.dot(eye - faceCenter) <= 0) continue // back face
                val lambert = nv.dot(lightDir)

                fun quad(c: P3, half: Double) = listOf(
                    cam.project(c + (uv + vv) * half),
                    cam.project(c + (uv - vv) * half),
                    cam.project(c + (uv * -1.0 - vv) * half),
                    cam.project(c + (vv - uv) * half),
                )

                val body = quad(faceCenter, CUBIE_HALF) to shade(bodyColor, lambert)
                val sticker = layout.stickerOf[i * 6 + f]
                if (sticker < 0) {
                    bodies.add(body)
                    continue
                }
                // Body first, then its sticker on top.
                drawPath(polygonPath(body.first), body.second)
                val corners = quad(center + nv * STICKER_LIFT, STICKER_HALF)
                val path = roundedPath(corners, 0.16f)
                drawPath(path, shade(stickerColor(state[sticker]), lambert))
                if (sticker in highlight) drawHighlight(path, time)
                hits.add(sticker to corners)
            }
            for ((pts, color) in bodies) drawPath(polygonPath(pts), color)
        }
    }
}

@Composable
fun CubeView(
    n: Int,
    facelets: String,
    controller: CubeViewController,
    highlight: List<Int>,
    autoRotate: Boolean,
    paintEnabled: Boolean,
    onStickerTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(n) { CubeViewState(n) }
    PuzzleViewEffects(state, controller, facelets, highlight, autoRotate)
    val hl = remember(highlight) { highlight.toSet() }
    Canvas(
        modifier
            .fillMaxSize()
            .orbitGestures(state) { p -> if (paintEnabled) state.stickerAt(p)?.let(onStickerTap) },
    ) { state.draw(this, facelets, hl) }
}

/** Attaches the view to its controller and keeps the state in step with the parameters. */
@Composable
fun PuzzleViewEffects(
    state: PuzzleViewState<*>,
    controller: CubeViewController,
    facelets: String,
    highlight: List<Int>,
    autoRotate: Boolean,
) {
    DisposableEffect(state, controller) {
        controller.attach(state)
        onDispose {
            controller.detach(state)
            state.dispose()
        }
    }
    SideEffect {
        state.autoRotate = autoRotate
        state.hasHighlight = highlight.isNotEmpty()
    }
    LaunchedEffect(state, facelets) { state.override = null }
    TickEffect(state)
}
