package com.bbcl.rubiq

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val guide = listOf(
    "1. Pick your puzzle and hold it" to listOf(
        "Choose 3x3, 4x4, 5x5, Pyraminx or Skewb from the puzzle button at the top.",
        "Cubes: hold it with White on top and Green facing you. Keep it that way the whole time: " +
            "the app reads and solves the cube in this orientation.",
        "Pyraminx: hold it with one face towards you and one tip pointing up. Any colour can be in front — " +
            "the centres tell the app which colour belongs on each face.",
        "Skewb: hold it any way you like, but keep holding it the same way. The top-front-right corner " +
            "never moves during a solve, so it tells the app which colour belongs on each face.",
    ),
    "2. Paint your cube" to listOf(
        "Tap Clear to paint (in the ⋮ menu or the Paint tab). On a 3x3 and 5x5 the middle centre stickers stay fixed.",
        "Pick a colour from the palette, then tap stickers on the 3D cube. Drag to rotate, pinch to zoom.",
        "Switch to the flat net (grid button) to paint the back and bottom faces more easily.",
        "Each colour needs exactly 9 stickers on a 3x3 (16 on a 4x4, 25 on a 5x5, 9 on a Pyraminx, 5 on a Skewb). " +
            "The counters turn green when right.",
    ),
    "3. Check for mistakes" to listOf(
        "The banner under the cube checks your painting live. A twisted corner, flipped edge or swapped " +
            "pieces cannot happen on a real cube, so they usually mean a painting mistake. The bad stickers glow red.",
        "If the cube was taken apart and put back wrong, use \"Auto-fix & solve\".",
    ),
    "4. Solve and follow along" to listOf(
        "Tap Solve. Playback starts on its own; every step is a single 90° turn (120° on a Pyraminx or Skewb).",
        "Pyraminx and Skewb solutions are as short as possible. Bigger cubes are solved with the reduction " +
            "method: centres, then edges, then like a 3x3.",
        "Use Play/Pause, the arrows, or tap any move or stage to jump there. Adjust the speed with the slider.",
    ),
    "Notation" to listOf(
        "R  — turn the Right face 90° clockwise (as if you were looking straight at that face).",
        "R' — turn it 90° counter-clockwise.",
        "U, D, F, B, L — the Up, Down, Front, Back and Left faces, the same way.",
        "Rw — (4x4) turn the right two layers together.",
        "2R — (4x4, 5x5) turn only the inner slice next to the right face; 3R — (5x5) the middle slice.",
        "Pyraminx: U, L, R, B turn the top, left, right or back corner with the layer below it, as seen " +
            "looking at that tip; u, l, r, b turn just the tip.",
        "Skewb: R, U, L, B turn the half around the bottom-right-back, top-left-back, bottom-left-front or " +
            "bottom-left-back corner, as seen looking at that corner.",
        "Half turns are always shown as two separate steps, so you never have to count.",
    ),
    "Offline" to listOf(
        "Everything runs on your phone. The app needs no internet connection and sends nothing anywhere.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = AppColors.bgPrimary,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.bgPrimary),
                title = { Text("How to use") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            for ((title, points) in guide) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.cyan)
                Spacer(Modifier.height(8.dp))
                for (p in points) {
                    Row(Modifier.padding(bottom = 6.dp)) {
                        Text("•  ", color = AppColors.textDim)
                        Text(p, fontSize = 14.sp, lineHeight = 19.6.sp, color = AppColors.textMuted)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
