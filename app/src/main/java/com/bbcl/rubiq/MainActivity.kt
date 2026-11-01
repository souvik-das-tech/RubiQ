package com.bbcl.rubiq

import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView

class MainActivity : ComponentActivity() {
    private val app: AppController by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            RubiQTheme {
                val view = LocalView.current
                DisposableEffect(view) {
                    app.haptics = { kind ->
                        view.performHapticFeedback(
                            when (kind) {
                                Haptic.SELECTION -> HapticFeedbackConstants.CLOCK_TICK
                                Haptic.LIGHT -> HapticFeedbackConstants.VIRTUAL_KEY
                                Haptic.MEDIUM -> HapticFeedbackConstants.LONG_PRESS
                            },
                        )
                    }
                    onDispose { app.haptics = null }
                }
                var guide by rememberSaveable { mutableStateOf(false) }
                if (guide) {
                    BackHandler { guide = false }
                    GuideScreen(onBack = { guide = false })
                } else {
                    HomeScreen(app, onOpenGuide = { guide = true })
                }
            }
        }
    }
}
