package com.bbcl.rubiq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.filled._360
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: AppController, onOpenGuide: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AppColors.bgPrimary,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.bgPrimary),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandIcon()
                            Spacer(Modifier.width(10.dp))
                            Text("RubiQ Offline", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    actions = {
                        ModeSwitch(app)
                        IconButton(onClick = app::scramble, enabled = !app.locked) {
                            Icon(Icons.Filled.Shuffle, contentDescription = "Scramble")
                        }
                        MoreMenu(app, onOpenGuide)
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Viewport(app, Modifier.weight(11f))
                BannerView(app)
                Panel(app, snackbar, Modifier.weight(10f))
            }
        }
        Confetti(app.celebrations, Modifier.fillMaxSize())
    }
}

@Composable
private fun MoreMenu(app: AppController, onOpenGuide: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Reset to solved") },
                leadingIcon = { Icon(Icons.Filled.RestartAlt, null) },
                enabled = !app.locked,
                onClick = { open = false; app.resetToSolved() },
            )
            DropdownMenuItem(
                text = { Text("Clear to paint") },
                leadingIcon = { Icon(Icons.Filled.FormatColorReset, null) },
                enabled = !app.locked,
                onClick = { open = false; app.resetToBlank() },
            )
            DropdownMenuItem(
                text = { Text("How to use") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
                onClick = { open = false; onOpenGuide() },
            )
        }
    }
}

@Composable
private fun BrandIcon() {
    val colors = listOf(Color.White, Color(0xFFFACC15), Color(0xFFEF4444), Color(0xFF22C55E))
    Column(
        Modifier
            .size(28.dp)
            .background(AppColors.accentGradient, RoundedCornerShape(8.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (row in 0 until 2) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (col in 0 until 2) {
                    Box(Modifier.weight(1f).fillMaxSize().background(colors[row * 2 + col], RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

@Composable
private fun ModeSwitch(app: AppController) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.accentGradient)
                .clickable(enabled = !app.locked) { open = true }
                .padding(start = 12.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(app.puzzle.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose puzzle", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (p in Puzzle.all) {
                DropdownMenuItem(
                    text = { Text(p.title) },
                    leadingIcon = {
                        if (p.kind == app.puzzle.kind) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp))
                    },
                    onClick = { open = false; app.switchPuzzle(p.kind) },
                )
            }
        }
    }
}

@Composable
private fun Viewport(app: AppController, modifier: Modifier) {
    val paintEnabled = app.paintMode && !app.scrambling
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 8.dp)
            .clip(shape)
            .background(Brush.radialGradient(listOf(Color(0xFF131F38), AppColors.bgPrimary)))
            .border(1.dp, AppColors.panelBorder, shape),
    ) {
        val p = app.puzzle
        if (app.showNet) {
            Box(Modifier.fillMaxSize().padding(start = 8.dp, top = 40.dp, end = 8.dp, bottom = 28.dp)) {
                when (p) {
                    is PolyPuzzle -> PolyNetView(p.geometry, app.facelets, app.highlight, paintEnabled, app::paintSticker)
                    is CubePuzzle -> NetView(p.n, app.facelets, app.highlight, paintEnabled, app::paintSticker)
                }
            }
        } else {
            when (p) {
                is PolyPuzzle -> {
                    val pyra = p.kind == PuzzleKind.PYRAMINX
                    PolyView(
                        geometry = p.geometry,
                        facelets = app.facelets,
                        controller = app.cubeView,
                        highlight = app.highlight,
                        autoRotate = app.autoRotate,
                        paintEnabled = paintEnabled,
                        onStickerTap = app::paintSticker,
                        viewDistance = if (pyra) 10.5 else 11.2,
                        defaultYaw = if (pyra) -0.75 else -0.6435,
                        defaultPitch = if (pyra) 0.3 else 0.4636,
                    )
                }
                is CubePuzzle -> CubeView(
                    n = p.n,
                    facelets = app.facelets,
                    controller = app.cubeView,
                    highlight = app.highlight,
                    autoRotate = app.autoRotate,
                    paintEnabled = paintEnabled,
                    onStickerTap = app::paintSticker,
                )
            }
        }
        Badge(p.title, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp))
        Column(Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 4.dp)) {
            ToolButton(
                if (app.showNet) Icons.Filled.ViewInAr else Icons.Filled.GridView,
                if (app.showNet) "Show 3D cube" else "Show flat net",
                enabled = !app.scrambling,
                onClick = app::toggleNet,
            )
            if (!app.showNet) {
                ToolButton(Icons.Filled.CenterFocusStrong, "Recenter view", onClick = app.cubeView::recenter)
                ToolButton(Icons.AutoMirrored.Filled._360, "Auto-rotate", active = app.autoRotate, onClick = app::toggleAutoRotate)
            }
            ToolButton(
                Icons.Filled.Brush,
                if (app.paintMode) "Tap-to-paint is on" else "Tap-to-paint is off",
                active = app.paintMode,
                onClick = app::togglePaintMode,
            )
        }
        Text(
            when {
                app.showNet -> if (paintEnabled) "Tap a sticker to paint it" else "Painting is off"
                paintEnabled -> "Drag to rotate • Pinch to zoom • Tap a sticker to paint"
                else -> "Drag to rotate • Pinch to zoom"
            },
            textAlign = TextAlign.Center,
            fontSize = 11.5.sp,
            color = AppColors.textDim,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.35f), shape)
            .border(1.dp, AppColors.panelBorder, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(AppColors.emerald, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 11.5.sp, color = AppColors.textMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ToolButton(icon: ImageVector, tooltip: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (active) AppColors.indigo.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.3f),
            contentColor = if (active) Color.White else AppColors.textMuted,
        ),
        modifier = Modifier.padding(vertical = 2.dp).border(1.dp, AppColors.panelBorder, CircleShape),
    ) { Icon(icon, contentDescription = tooltip, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun BannerView(app: AppController) {
    val b = app.banner
    val color = when (b.type) {
        BannerType.VALID -> AppColors.emerald
        BannerType.WARNING -> AppColors.amber
        BannerType.ERROR -> AppColors.rose
        BannerType.INFO -> AppColors.cyan
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f), shape)
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
    ) {
        Icon(b.icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(b.title, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(b.desc, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 12.5.sp, color = AppColors.textMuted, lineHeight = 16.sp)
            if (b.showFix) {
                FilledTonalButton(
                    onClick = { app.solve(allowAutoFix = true) },
                    enabled = !app.locked,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = color.copy(alpha = 0.2f), contentColor = color),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.padding(top = 6.dp).height(32.dp),
                ) {
                    Icon(Icons.Filled.Build, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Auto-fix & solve")
                }
            }
        }
        if (app.notice != null) {
            IconButton(onClick = app::dismissNotice, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = AppColors.textDim, modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun Panel(app: AppController, snackbar: SnackbarHostState, modifier: Modifier) {
    val pager = rememberPagerState { PanelTab.entries.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(app) { app.requestedTab.collect { pager.animateScrollToPage(it.ordinal) } }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(12.dp, 8.dp)
            .clip(shape)
            .background(AppColors.bgSecondary)
            .border(1.dp, AppColors.panelBorder, shape),
    ) {
        // The default indicator takes the primary colour; the tabs use cyan.
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = AppColors.cyan)) {
            PrimaryTabRow(
                selectedTabIndex = pager.currentPage,
                containerColor = Color.Transparent,
                contentColor = AppColors.textMain,
                divider = { Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.panelBorder)) },
            ) {
                listOf("Paint & Solve", "Solution", "Checks").forEachIndexed { i, label ->
                    Tab(
                        selected = pager.currentPage == i,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        selectedContentColor = AppColors.textMain,
                        unselectedContentColor = AppColors.textMuted,
                        modifier = Modifier.height(42.dp),
                        text = { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                    )
                }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            when (PanelTab.entries[page]) {
                PanelTab.PAINT -> PaintPanel(app)
                PanelTab.SOLUTION -> SolutionPanel(app, snackbar)
                PanelTab.CHECKS -> ChecksPanel(app)
            }
        }
    }
}
