package com.moodlife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.moodlife.app.ui.MoodLifeNavHost
import com.moodlife.app.ui.theme.AppearanceId
import com.moodlife.app.ui.theme.MoodLifeTheme
import com.moodlife.app.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val appearanceRaw by themeViewModel.appearance.collectAsStateWithLifecycle()
            MoodLifeTheme(
                themeMode = themeMode,
                appearance = AppearanceId.parse(appearanceRaw),
            ) {
                MoodLifeNavHost(themeViewModel = themeViewModel)
            }
        }
    }
}
