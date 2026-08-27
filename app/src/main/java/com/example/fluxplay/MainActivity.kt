package com.example.fluxplay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fluxplay.ui.bookmarks.BookmarksScreen
import com.example.fluxplay.ui.discover.DiscoverScreen
import com.example.fluxplay.ui.discover.DiscoverViewModel
import com.example.fluxplay.ui.history.HistoryScreen
import com.example.fluxplay.ui.history.HistoryViewModel
import com.example.fluxplay.ui.player.PlayerScreen
import com.example.fluxplay.ui.player.PlayerViewModel
import com.example.fluxplay.ui.settings.SettingsScreen
import com.example.fluxplay.ui.settings.SettingsViewModel
import com.example.fluxplay.ui.theme.FluxplayTheme

enum class NavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    PLAYER("Player", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircleOutline, "nav_tab_player"),
    STREAMS("Streams", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary, "nav_tab_streams"),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History, "nav_tab_history"),
    BOOKMARKS("Saved", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "nav_tab_bookmarks"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings")
}

class MainActivity : ComponentActivity() {

    private val app by lazy { application as FluxplayApp }

    private val playerViewModel: PlayerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlayerViewModel(app, app.mediaRepository, app.settingsRepository) as T
            }
        }
    }

    private val discoverViewModel: DiscoverViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DiscoverViewModel(app.metadataRepository, app.mediaRepository) as T
            }
        }
    }

    private val historyViewModel: HistoryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryViewModel(app.mediaRepository) as T
            }
        }
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(app, app.settingsRepository, app.mediaRepository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val context = LocalContext.current

            // Auto-request notification permission on Android 13+
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* granted -> no-op */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            FluxplayTheme(themeMode = settings.selectedTheme) {
                FluxplayMainApp(
                    playerViewModel = playerViewModel,
                    discoverViewModel = discoverViewModel,
                    historyViewModel = historyViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

@Composable
fun FluxplayMainApp(
    playerViewModel: PlayerViewModel,
    discoverViewModel: DiscoverViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.PLAYER) }
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (!playerState.isFullscreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = if (playerState.isFullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        }

        when (selectedTab) {
            NavigationTab.PLAYER -> {
                PlayerScreen(
                    viewModel = playerViewModel,
                    modifier = contentModifier
                )
            }
            NavigationTab.STREAMS -> {
                DiscoverScreen(
                    viewModel = discoverViewModel,
                    onPlayMedia = { media ->
                        playerViewModel.loadMedia(media)
                        selectedTab = NavigationTab.PLAYER
                    },
                    modifier = contentModifier
                )
            }
            NavigationTab.HISTORY -> {
                HistoryScreen(
                    viewModel = historyViewModel,
                    onPlayMedia = { media ->
                        playerViewModel.loadMedia(media)
                        selectedTab = NavigationTab.PLAYER
                    },
                    modifier = contentModifier
                )
            }
            NavigationTab.BOOKMARKS -> {
                BookmarksScreen(
                    viewModel = historyViewModel,
                    onPlayMedia = { media ->
                        playerViewModel.loadMedia(media)
                        selectedTab = NavigationTab.PLAYER
                    },
                    modifier = contentModifier
                )
            }
            NavigationTab.SETTINGS -> {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    modifier = contentModifier
                )
            }
        }
    }
}
