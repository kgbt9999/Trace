package com.moodlife.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moodlife.app.R
import com.moodlife.app.ui.components.CrisisPlanHeaderAction
import com.moodlife.app.ui.components.FitOneLineText
import com.moodlife.app.ui.navigation.CrisisChipUiState
import com.moodlife.app.ui.navigation.NavHostViewModel
import com.moodlife.app.ui.screens.TodayScreen
import com.moodlife.app.ui.screens.calendar.CalendarScreen
import com.moodlife.app.ui.screens.forecast.ForecastScreen
import com.moodlife.app.ui.screens.meds.MedsScreen
import com.moodlife.app.ui.screens.physical.PhysicalScreen
import com.moodlife.app.ui.screens.reports.ReportsScreen
import com.moodlife.app.ui.screens.guide.GuideScreen
import com.moodlife.app.ui.screens.selfhelp.SelfHelpScreen
import com.moodlife.app.ui.screens.settings.SettingsScreen
import com.moodlife.app.ui.screens.sources.SourcesScreen
import com.moodlife.app.ui.theme.LocalDarkTheme
import com.moodlife.app.ui.theme.ThemeViewModel

enum class MoodLifeTab(val route: String, val labelRes: Int) {
    Today("today", R.string.tab_today),
    Calendar("calendar", R.string.tab_calendar),
    Meds("meds", R.string.tab_meds),
    Physical("physical", R.string.tab_physical),
    Reports("reports", R.string.tab_reports),
    Forecast("forecast", R.string.tab_forecast),
    SelfHelp("selfhelp", R.string.tab_selfhelp),
    Settings("settings", R.string.tab_settings),
    Sources("sources", R.string.tab_sources),
    Guide("guide", R.string.guide_title),
}

@Composable
fun MoodLifeNavHost(
    navHostViewModel: NavHostViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val selfHelpEnabled by navHostViewModel.selfHelpTabEnabled.collectAsStateWithLifecycle()
    val tabs = buildList {
        add(MoodLifeTab.Today)
        add(MoodLifeTab.Calendar)
        add(MoodLifeTab.Reports)
        add(MoodLifeTab.Forecast)
        if (selfHelpEnabled) add(MoodLifeTab.SelfHelp)
        add(MoodLifeTab.Settings)
    }
    val crisisState by navHostViewModel.crisisState.collectAsStateWithLifecycle()
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val dark = LocalDarkTheme.current

    LaunchedEffect(navHostViewModel) {
        navHostViewModel.dayNavigation.switchTab.collect { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val selectedRoutes = navBackStackEntry?.destination?.hierarchy?.mapNotNull { it.route }?.toSet().orEmpty()
    val onSelect: (MoodLifeTab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            Column {
                MoodLifeHeader(
                    dark = dark,
                    crisisState = crisisState,
                    onOpenCrisis = navHostViewModel::openCrisisSettings,
                    onToggleTheme = { themeViewModel.toggleDark(dark) },
                    onOpenSelfHelp = {
                        navController.navigate(MoodLifeTab.SelfHelp.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenGuide = {
                        navController.navigate(MoodLifeTab.Guide.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSources = {
                        navController.navigate(MoodLifeTab.Sources.route) {
                            launchSingleTop = true
                        }
                    },
                )
                if (isWide) {
                    DesktopTabRail(
                        tabs = tabs,
                        selectedRoutes = selectedRoutes,
                        onSelect = onSelect,
                    )
                }
            }
        },
        bottomBar = {
            if (!isWide) {
                CompactTabBar(
                    tabs = tabs,
                    selectedRoutes = selectedRoutes,
                    onSelect = onSelect,
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = MoodLifeTab.Today.route,
            ) {
                composable(MoodLifeTab.Today.route) { TodayScreen() }
                composable(MoodLifeTab.Calendar.route) { CalendarScreen() }
                composable(MoodLifeTab.Meds.route) { MedsScreen() }
                composable(MoodLifeTab.Physical.route) { PhysicalScreen() }
                composable(MoodLifeTab.Reports.route) { ReportsScreen() }
                composable(MoodLifeTab.Forecast.route) { ForecastScreen() }
                composable(MoodLifeTab.SelfHelp.route) { SelfHelpScreen() }
                composable(MoodLifeTab.Settings.route) { SettingsScreen() }
                composable(MoodLifeTab.Sources.route) { SourcesScreen() }
                composable(MoodLifeTab.Guide.route) { GuideScreen() }
            }
        }
    }
}

@Composable
private fun MoodLifeHeader(
    dark: Boolean,
    crisisState: CrisisChipUiState,
    onOpenCrisis: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSelfHelp: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSources: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_brand_mark),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(max = 120.dp),
            )
            Spacer(Modifier.weight(1f))
            CrisisPlanHeaderAction(
                state = crisisState,
                onOpenSettings = onOpenCrisis,
            )
            val selfHelpCd = stringResource(R.string.tab_selfhelp)
            IconButton(
                onClick = onOpenSelfHelp,
                modifier = Modifier.semantics { contentDescription = selfHelpCd },
            ) {
                Icon(
                    imageVector = Icons.Filled.SelfImprovement,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onOpenGuide,
                modifier = Modifier.semantics { contentDescription = "Инструкция" },
            ) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onOpenSources,
                modifier = Modifier.semantics { contentDescription = "Источники" },
            ) {
                Icon(
                    imageVector = Icons.Outlined.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier.semantics {
                    contentDescription = if (dark) {
                        "Включить светлую тему"
                    } else {
                        "Включить тёмную тему"
                    }
                },
            ) {
                Icon(
                    imageVector = if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DesktopTabRail(
    tabs: List<MoodLifeTab>,
    selectedRoutes: Set<String>,
    onSelect: (MoodLifeTab) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab.route in selectedRoutes
            val label = stringResource(tab.labelRes)
            val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            val fg = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(tab) },
                        role = Role.Tab,
                    )
                    .semantics { contentDescription = label }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MoodLifeIcons.forTab(tab),
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CompactTabBar(
    tabs: List<MoodLifeTab>,
    selectedRoutes: Set<String>,
    onSelect: (MoodLifeTab) -> Unit,
) {
    NavigationBar(
        tonalElevation = 0.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.selectableGroup(),
    ) {
        tabs.forEach { tab ->
            val selected = tab.route in selectedRoutes
            val label = stringResource(tab.labelRes)
            val color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(tab) },
                        role = Role.Tab,
                    )
                    .semantics { contentDescription = label }
                    .padding(horizontal = 1.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Icon(
                    imageVector = MoodLifeIcons.forTab(tab),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                FitOneLineText(text = label, color = color)
            }
        }
    }
}
