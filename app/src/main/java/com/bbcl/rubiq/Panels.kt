// The three tabs under the cube: Paint & Solve, Solution, Checks.
package com.bbcl.rubiq

import android.content.ClipData
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private val sectionTitle = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = AppColors.textMuted)

@Composable
private fun PanelColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) { content() }
}

@Composable
private fun Card(modifier: Modifier = Modifier, border: Color = AppColors.panelBorder, background: Color = AppColors.bgTertiary, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(background).border(1.dp, border, shape).padding(12.dp)) { content() }
}

// ===================== Paint & Solve =====================

@Composable
fun PaintPanel(app: AppController) {
    val v = app.validation
    val perColor = app.puzzle.perColor
    PanelColumn {
        Text("COLOUR PALETTE", style = sectionTitle)
        Spacer(Modifier.height(10.dp))
        Row {
            for (code in app.puzzle.palette) {
                ColorChip(
                    code = code,
                    count = v.counts[code] ?: 0,
                    perColor = perColor,
                    selected = app.activeColor == code,
                    onTap = { app.pickColor(code) },
                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { v.painted.toFloat() / v.total },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
            color = AppColors.cyan,
            trackColor = AppColors.bgTertiary,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(6.dp))
        Text("${v.painted}/${v.total} stickers painted", fontSize = 12.sp, color = AppColors.textDim)
        Spacer(Modifier.height(12.dp))
        Row {
            OutlinedButton(onClick = app::resetToSolved, enabled = !app.locked, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Solved")
            }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = app::resetToBlank, enabled = !app.locked, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.FormatColorReset, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clear to paint", maxLines = 1)
            }
        }
        Spacer(Modifier.height(14.dp))
        SolveButton(app)
    }
}

@Composable
private fun ColorChip(code: Char, count: Int, perColor: Int, selected: Boolean, onTap: () -> Unit, modifier: Modifier) {
    val color = stickerColor(code)
    val countColor = when {
        count > perColor -> AppColors.rose
        count == perColor -> AppColors.emerald
        else -> AppColors.textMuted
    }
    val borderColor by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "chip border")
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .semantics {
                contentDescription = "${colorNames[code]}, $count of $perColor painted"
                this.selected = selected
            }
            .clickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .then(if (selected) Modifier.shadow(12.dp, shape, ambientColor = color, spotColor = color) else Modifier)
                .background(color, shape)
                .border(3.dp, borderColor, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Brush, null, tint = Color.Black.copy(alpha = 0.54f), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text("$count/$perColor", fontSize = 11.sp, color = countColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SolveButton(app: AppController) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .alpha(if (app.locked) 0.6f else 1f)
            .shadow(18.dp, shape, ambientColor = AppColors.indigo, spotColor = AppColors.indigo)
            .clip(shape)
            .background(AppColors.accentGradient)
            .clickable(enabled = !app.locked) { app.solve() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app.solving) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp)
        } else {
            Icon(Icons.Filled.AutoAwesome, null, tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (app.solving) {
                "CALCULATING SOLUTION…"
            } else {
                "SOLVE ${if (app.puzzle is CubePuzzle) "CUBE" else app.puzzle.label.uppercase()} NOW"
            },
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = Color.White,
        )
    }
}

// ===================== Solution =====================

private val stagePrefix = Regex("^Stage \\d+: ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SolutionPanel(app: AppController, snackbar: SnackbarHostState) {
    val moves = app.moves
    val solution = app.solution
    val hasSolution = solution != null
    val current = app.currentMove
    val next = app.nextMove
    val stage = current?.stage?.replaceFirst(stagePrefix, "")
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    PanelColumn {
        // Current step card.
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(60.dp).background(AppColors.accentGradient, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val label = current?.move ?: if (app.finished) "✓" else "--"
                    Text(label, fontSize = if (label.length > 3) 18.sp else 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Step ${app.currentStep} of ${moves.size}${if (stage != null) " • $stage" else ""}",
                        fontSize = 12.sp,
                        color = AppColors.cyan,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            moves.isEmpty() -> "Press \"Solve cube now\" to compute a solution."
                            current != null -> current.explanation
                            app.finished -> "🎉 Cube completely solved!"
                            else -> "Press Play to start the step-by-step solution."
                        },
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    )
                    if (next != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildAnnotatedString {
                                append("Next: ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(next.move) }
                            },
                            fontSize = 12.sp,
                            color = AppColors.textDim,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Playback controls.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            PlayerButton(Icons.Filled.SkipPrevious, "Jump to start", enabled = hasSolution) { app.jumpTo(0) }
            PlayerButton(Icons.Filled.ChevronLeft, "Previous move", enabled = hasSolution && app.currentStep > 0, onClick = app::stepBackward)
            PlayerButton(
                if (app.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (app.isPlaying) "Pause" else "Play",
                primary = true,
                enabled = hasSolution,
                onClick = app::togglePlayback,
            )
            PlayerButton(Icons.Filled.ChevronRight, "Next move", enabled = hasSolution && !app.finished, onClick = app::nextStep)
            PlayerButton(Icons.Filled.SkipNext, "Jump to end", enabled = hasSolution) { app.jumpTo(moves.size) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Speed", fontSize = 12.sp, color = AppColors.textMuted)
            Slider(
                value = (1700 - app.speedMs).toFloat(),
                onValueChange = { app.setSpeed(1700 - it.roundToInt()) },
                valueRange = 200f..1500f,
                steps = 12,
                colors = SliderDefaults.colors(thumbColor = AppColors.cyan, activeTrackColor = AppColors.cyan),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(String.format(Locale.US, "%.1fs / move", app.speedMs / 1000.0), fontSize = 12.sp, color = AppColors.textMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SOLUTION (${moves.size} MOVES)", style = sectionTitle)
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = hasSolution,
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("RubiQ solution", app.solutionText)))
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar("Solution copied", duration = SnackbarDuration.Short)
                    }
                },
            ) {
                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
        }
        Spacer(Modifier.height(4.dp))
        if (moves.isEmpty()) {
            Text("Tap \"Solve cube now\" to compute the moves.", color = AppColors.textDim, fontSize = 13.sp)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                moves.forEachIndexed { i, m ->
                    MoveChip(m.move, active = i == app.currentStep - 1, done = i < app.currentStep - 1) { app.jumpTo(i + 1) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Every step is one ${app.puzzle.stepTurn} turn. ${app.puzzle.notationHelp}",
                fontSize = 11.5.sp,
                color = AppColors.textDim,
                lineHeight = 15.5.sp,
            )
        }
        if (solution != null && solution.stages.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(if (app.puzzle is CubePuzzle) "REDUCTION METHOD STAGES" else "SOLUTION STAGES", style = sectionTitle)
            Spacer(Modifier.height(8.dp))
            var offset = 0
            for (st in solution.stages) {
                val from = offset
                offset += st.moves.size
                StageCard(st, active = app.currentStep > from && app.currentStep <= offset) { app.jumpTo(from) }
            }
        }
    }
}

@Composable
private fun StageCard(st: SolveStage, active: Boolean, onClick: () -> Unit) {
    val accent = if (st.isParity) AppColors.rose else AppColors.cyan
    Card(
        Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        border = if (active) accent else AppColors.panelBorder,
        background = if (active) accent.copy(alpha = 0.12f) else AppColors.bgTertiary,
    ) {
        Row {
            Text(st.title, fontWeight = FontWeight.Bold, color = accent, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${st.moves.size} moves", fontSize = 11.5.sp, color = AppColors.textDim)
        }
        Spacer(Modifier.height(4.dp))
        Text(st.description, fontSize = 12.sp, color = AppColors.textMuted)
        Spacer(Modifier.height(6.dp))
        Text(st.moves.joinToString(" "), fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = AppColors.textDim)
    }
}

@Composable
private fun PlayerButton(icon: ImageVector, tooltip: String, primary: Boolean = false, enabled: Boolean, onClick: () -> Unit) {
    if (primary) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(56.dp).alpha(if (enabled) 1f else 0.5f).background(AppColors.accentGradient, CircleShape),
        ) { Icon(icon, contentDescription = tooltip, tint = Color.White, modifier = Modifier.size(30.dp)) }
    } else {
        FilledTonalIconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = tooltip) }
    }
}

@Composable
private fun MoveChip(move: String, active: Boolean, done: Boolean, onTap: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .widthIn(min = 38.dp)
            .clip(shape)
            .then(
                when {
                    active -> Modifier.background(AppColors.accentGradient)
                    done -> Modifier.background(AppColors.indigo.copy(alpha = 0.12f))
                    else -> Modifier.background(AppColors.bgTertiary)
                },
            )
            .border(1.dp, if (active) Color.Transparent else AppColors.panelBorder, shape)
            .clickable(onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            move,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = if (active) Color.White else if (done) AppColors.textDim else AppColors.textMain,
        )
    }
}

// ===================== Checks =====================

private class CheckInfo(val icon: ImageVector, val color: Color, val title: String, val ok: String, val bad: String, val desc: String)

/** The three checks shown for each puzzle, in the order cornerTwist, edgeFlip, permutation. */
private fun checksFor(p: Puzzle): List<CheckInfo> {
    val cornerTwist = CheckInfo(
        Icons.Filled.Sync, AppColors.amber, "Corner twist parity", "Valid (0 mod 3)", "Twisted corner",
        "The orientation sum of all 8 corners must be 0 (mod 3). Otherwise one corner was twisted by hand.",
    )
    return when (p.kind) {
        PuzzleKind.CUBE3 -> listOf(
            cornerTwist,
            CheckInfo(
                Icons.Filled.Repeat, AppColors.cyan, "Edge flip parity", "Valid (0 mod 2)", "Flipped edge",
                "The orientation sum of all 12 edges must be 0 (mod 2). If odd, an edge was flipped by hand.",
            ),
            CheckInfo(
                Icons.Filled.Shuffle, AppColors.purple, "Permutation parity", "Balanced", "Swapped pieces",
                "Corner permutation parity must equal edge permutation parity: on a 3x3, two pieces can never be swapped alone.",
            ),
        )
        PuzzleKind.CUBE4 -> listOf(
            cornerTwist,
            CheckInfo(
                Icons.Filled.Repeat, AppColors.cyan, "Edge wing orientation", "All wings legal", "Mirrored wing",
                "A 4x4 edge wing can never be flipped in place, so every wing must read in its one legal orientation.",
            ),
            CheckInfo(
                Icons.Filled.Shuffle, AppColors.purple, "Permutation parity", "Handled by solver", "Swapped pieces",
                "On a 4x4 any permutation is reachable; the solver fixes OLL/PLL parity automatically.",
            ),
        )
        PuzzleKind.CUBE5 -> listOf(
            cornerTwist,
            CheckInfo(
                Icons.Filled.Repeat, AppColors.cyan, "Edge orientation", "All edges legal", "Flipped edge or wing",
                "The 12 middle edges follow 3x3 rules (flip sum 0 mod 2), and an edge wing can never be flipped in place.",
            ),
            CheckInfo(
                Icons.Filled.Shuffle, AppColors.purple, "Permutation parity", "Balanced", "Swapped pieces",
                "With the centres fixed, corner and middle-edge permutation parity must match, as on a 3x3. " +
                    "The wings may be in any order: the solver fixes the 5x5 edge parity up front.",
            ),
        )
        PuzzleKind.PYRAMINX -> listOf(
            CheckInfo(
                Icons.Filled.ChangeHistory, AppColors.amber, "Tips & centres", "All legal", "Impossible piece",
                "Each tip and each centre can sit in any of its three rotations, but its colours must match the centre and never be in mirror order.",
            ),
            CheckInfo(
                Icons.Filled.Repeat, AppColors.cyan, "Edge flip parity", "Valid (0 mod 2)", "Flipped edge",
                "The number of flipped edges is always even. If it is odd, an edge was flipped by hand.",
            ),
            CheckInfo(
                Icons.Filled.Shuffle, AppColors.purple, "Edge permutation", "Even", "Swapped edges",
                "Every turn cycles three edges, so the edges always form an even permutation: two edges can never be swapped alone.",
            ),
        )
        PuzzleKind.SKEWB -> listOf(
            CheckInfo(
                Icons.Filled.Sync, AppColors.amber, "Corner twists", "Legal", "Twisted corner",
                "The twists of each group of four corners must add up to a total set by the other group's arrangement; otherwise a corner was twisted by hand.",
            ),
            CheckInfo(
                Icons.Filled.CropSquare, AppColors.cyan, "Centre permutation", "Even", "Swapped centres",
                "Every turn cycles three centres, so two centres can never be swapped alone.",
            ),
            CheckInfo(
                Icons.Filled.Shuffle, AppColors.purple, "Corner permutation", "Even", "Swapped corners",
                "Corners only move within their group of four, three at a time: two corners can never be swapped alone.",
            ),
        )
    }
}

@Composable
fun ChecksPanel(app: AppController) {
    val c = app.validation.checks
    val p = app.puzzle
    val infos = checksFor(p)
    val states = listOf(c.cornerTwist, c.edgeFlip, c.permutation)
    val name = if (p is CubePuzzle) "cube" else p.label
    PanelColumn {
        Text("${if (p is CubePuzzle) "Rubik's Cube" else p.label} physics & parity", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "A real $name obeys the rules of permutation groups. If a piece is twisted or swapped by hand, the $name " +
                "enters a state that no legal turns can solve — these checks catch that before solving.",
            fontSize = 12.5.sp,
            color = AppColors.textMuted,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(12.dp))
        for (i in 0 until 3) CheckCard(infos[i], states[i])
        if (p.kind == PuzzleKind.CUBE3 || p.kind == PuzzleKind.CUBE4) ParityCard()
    }
}

@Composable
private fun ParityCard() {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bolt, null, tint = AppColors.rose, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("4x4 parity algorithms", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "A 4x4 has no fixed centres and has inner slices, so two parity cases can appear after reduction:",
            fontSize = 12.5.sp,
            color = AppColors.textMuted,
        )
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Column {
                Text("OLL parity (single flipped edge):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(OLL_PARITY_ALGORITHM, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = AppColors.cyan)
                Spacer(Modifier.height(6.dp))
                Text("PLL parity (two edges swapped):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(PLL_PARITY_ALGORITHM, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = AppColors.amber)
            }
        }
    }
}

@Composable
private fun CheckCard(info: CheckInfo, state: CheckState) {
    val (label, statusColor) = when (state) {
        CheckState.OK -> info.ok to AppColors.emerald
        CheckState.ERROR -> info.bad to AppColors.rose
        CheckState.PENDING -> "Waiting for a complete cube" to AppColors.textDim
    }
    Card(Modifier.padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(info.icon, null, tint = info.color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(info.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                label,
                fontSize = 11.sp,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(info.desc, fontSize = 12.5.sp, color = AppColors.textMuted, lineHeight = 17.sp)
    }
}
