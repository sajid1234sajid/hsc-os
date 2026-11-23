package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.AiChatViewModel
import com.example.ai.configureAppCheck
import com.example.data.AppLanguage
import com.example.data.StringsProvider
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.MistakeNotebookScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.RoadmapScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.screens.WeeklyScreen
import com.example.ui.theme.HscStudyTheme
import com.example.ui.viewmodel.StudyViewModel

private sealed class AppDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val labelProvider: (AppLanguage) -> String
) {
    data object Today : AppDestination("today", Icons.Filled.Today, Icons.Outlined.Today, StringsProvider::navToday)
    data object Roadmap : AppDestination("roadmap", Icons.Filled.Map, Icons.Outlined.Map, StringsProvider::navRoadmap)
    data object Weekly : AppDestination(
        "weekly",
        Icons.AutoMirrored.Filled.FactCheck,
        Icons.AutoMirrored.Outlined.FactCheck,
        StringsProvider::navWeekly
    )
    data object Mistakes : AppDestination("mistakes", Icons.Filled.EditNote, Icons.Outlined.EditNote, StringsProvider::navMistakes)
    data object Progress : AppDestination("progress", Icons.Filled.Assessment, Icons.Outlined.Assessment, StringsProvider::navProgress)
    data object AiTutor : AppDestination("ai_tutor", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, StringsProvider::navAiTutor)
    data object Settings : AppDestination("settings", Icons.Filled.Settings, Icons.Outlined.Settings, StringsProvider::navSettings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check before rendering Compose UI or creating ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val viewModel: StudyViewModel = viewModel()
            val aiChatViewModel: AiChatViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            HscStudyTheme(darkTheme = state.settings.darkMode) {
                HscStudyApp(
                    viewModel = viewModel,
                    aiChatViewModel = aiChatViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HscStudyApp(
    viewModel: StudyViewModel,
    aiChatViewModel: AiChatViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val chatState by aiChatViewModel.uiState.collectAsStateWithLifecycle()
    val lang = state.settings.appLanguage()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(state.currentWeek, state.todaySessions) {
        aiChatViewModel.updateStudyContext(
            week = state.currentWeek,
            todaySubjects = state.todaySessions.map { it.subjectLabelBn },
            activeChapters = state.todaySessions.mapNotNull { it.activeChapter?.chapter?.title }
        )
    }

    val primaryTabs = listOf(
        AppDestination.Today,
        AppDestination.Roadmap,
        AppDestination.Weekly,
        AppDestination.Mistakes,
        AppDestination.Progress
    )
    val railTabs = primaryTabs + AppDestination.AiTutor + AppDestination.Settings

    val currentTitle = railTabs.firstOrNull { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }?.labelProvider?.invoke(lang) ?: StringsProvider.appHeaderTitle(lang)

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (currentDestination?.route == AppDestination.Today.route) {
                                StringsProvider.appHeaderTitle(lang)
                            } else {
                                currentTitle
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        Surface(
                            onClick = {
                                viewModel.setLanguage(if (lang == AppLanguage.BN) AppLanguage.EN else AppLanguage.BN)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("language_toggle_chip")
                        ) {
                            Text(
                                text = if (lang == AppLanguage.BN) "বাং" else "EN",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    },
                    actions = {
                        if (!useRail) {
                            val aiSelected = currentDestination?.route == AppDestination.AiTutor.route
                            Surface(
                                onClick = { navigateTo(AppDestination.AiTutor.route) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (aiSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("nav_ai_tutor_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = StringsProvider.navAiTutor(lang),
                                        tint = if (aiSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "AI",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (aiSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        }
                                    )
                                }
                            }

                            IconButton(
                                onClick = { navigateTo(AppDestination.Settings.route) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("nav_settings_button")
                            ) {
                                Icon(
                                    imageVector = if (currentDestination?.route == AppDestination.Settings.route) {
                                        Icons.Filled.Settings
                                    } else {
                                        Icons.Outlined.Settings
                                    },
                                    contentDescription = StringsProvider.navSettings(lang),
                                    tint = if (currentDestination?.route == AppDestination.Settings.route) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        primaryTabs.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.labelProvider(lang)
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.labelProvider(lang),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        railTabs.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.labelProvider(lang)
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.labelProvider(lang),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }

                NavHost(
                    navController = navController,
                    startDestination = AppDestination.Today.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(AppDestination.Today.route) {
                        TodayScreen(
                            state = state,
                            onToggleTimer = viewModel::toggleSessionTimer,
                            onResetTimer = viewModel::resetSessionTimer,
                            onToggleSlotDone = viewModel::toggleSlotDone,
                            onStartBreak = viewModel::startBreakTimer,
                            onStopBreak = viewModel::stopBreakTimer,
                            onUpdateChapterStatus = viewModel::updateChapterStatus,
                            onNavigateToRoadmap = { navigateTo(AppDestination.Roadmap.route) }
                        )
                    }
                    composable(AppDestination.Roadmap.route) {
                        RoadmapScreen(
                            state = state,
                            onUpdateStatus = viewModel::updateChapterStatus,
                            onEditChapter = viewModel::editChapter
                        )
                    }
                    composable(AppDestination.Weekly.route) {
                        WeeklyScreen(
                            state = state,
                            onSelectWeek = viewModel::selectWeeklyViewWeek,
                            onToggleMandatoryTask = viewModel::toggleWeeklyTask,
                            onToggleFridayItem = viewModel::toggleFridayChecklistItem,
                            onUpdateChapterStatus = viewModel::updateChapterStatus
                        )
                    }
                    composable(AppDestination.Mistakes.route) {
                        MistakeNotebookScreen(
                            state = state,
                            onAddMistake = viewModel::addMistake,
                            onToggleRevised = viewModel::toggleMistakeRevised,
                            onDeleteMistake = viewModel::deleteMistake
                        )
                    }
                    composable(AppDestination.Progress.route) {
                        ProgressScreen(state = state)
                    }
                    composable(AppDestination.AiTutor.route) {
                        AiChatScreen(
                            chatState = chatState,
                            studyState = state,
                            onSelectRole = aiChatViewModel::selectRole,
                            onSendMessage = { prompt -> aiChatViewModel.sendMessage(prompt, lang) },
                            onClearChat = aiChatViewModel::clearCurrentChat
                        )
                    }
                    composable(AppDestination.Settings.route) {
                        SettingsScreen(
                            state = state,
                            onSetDailyHours = viewModel::setDailyHours,
                            onSetLanguage = viewModel::setLanguage,
                            onToggleDarkMode = viewModel::toggleDarkMode,
                            onSetNotificationsEnabled = viewModel::setNotificationsEnabled,
                            onSendTestNotification = viewModel::sendTestNotification,
                            onEditChapter = viewModel::editChapter,
                            onAddChapter = viewModel::addChapter,
                            onDeleteChapter = viewModel::deleteChapter,
                            onExportJson = viewModel::exportBackupJson,
                            onImportJson = viewModel::importBackupJson,
                            onResetAll = viewModel::resetAllData
                        )
                    }
                }
            }
        }
    }
}
