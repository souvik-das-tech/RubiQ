// A short confetti burst from the top centre, played whenever `trigger` changes.
package com.bbcl.rubiq

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private class Particle(val angle: Double, val speed: Double, val spin: Double, val colorIndex: Int, val w: Float, val h: Float)

private const val PARTICLES = 40
private const val BURST_MS = 900L
private const val FALL_MS = 3000L

@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    val colors = remember { cubeColors.filterKeys { it != 'X' }.values.toList() }
    var particles by remember { mutableStateOf<List<Particle>>(emptyList()) }
    var elapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val rng = Random(System.nanoTime())
        // Explosive: every direction, at varied speeds.
        particles = List(PARTICLES) {
            Particle(rng.nextDouble(0.0, 2 * PI), rng.nextDouble(250.0, 900.0), rng.nextDouble(-8.0, 8.0), rng.nextInt(colors.size), 8f + rng.nextFloat() * 6, 5f + rng.nextFloat() * 4)
        }
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            elapsedMs = (now - start) / 1_000_000
            if (elapsedMs > BURST_MS + FALL_MS) break
        }
        particles = emptyList()
    }

    if (particles.isEmpty()) return
    Canvas(modifier) {
        val t = elapsedMs / 1000.0
        val gravity = 400 * density // px/s²
        val fade = (1f - ((elapsedMs - BURST_MS).coerceAtLeast(0) / FALL_MS.toFloat())).coerceIn(0f, 1f)
        // Air drag (decay rate k) slows the burst: distance = v (1 - e^(-kt)) / k.
        val k = 2.5
        val travel = (1 - exp(-k * t)) / k
        for (p in particles) {
            val x = (size.width / 2 + cos(p.angle) * p.speed * density * travel).toFloat()
            val y = (sin(p.angle) * p.speed * density * travel + 0.5 * gravity * t * t).toFloat()
            rotate((p.spin * t * 57.3).toFloat(), pivot = Offset(x, y)) {
                drawRect(
                    colors[p.colorIndex].copy(alpha = fade),
                    topLeft = Offset(x - p.w * density / 2, y - p.h * density / 2),
                    size = Size(p.w * density, p.h * density),
                )
            }
        }
    }
}
