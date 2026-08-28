package com.example.fluxplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.example.fluxplay.data.model.PlayerEngine
import com.example.fluxplay.ui.bookmarks.BookmarksScreen
import com.example.fluxplay.ui.discover.DiscoverScreen
import com.example.fluxplay.ui.discover.DiscoverViewModel
import com.example.fluxplay.ui.history.HistoryScreen
import com.example.fluxplay.ui.history.HistoryViewModel
import com.example.fluxplay.ui.player.PlayerScreen
import com.example.fluxplay.ui.player.PlayerViewModel
import com.example.fluxplay.ui.settings.SettingsScreen
import com.example.fluxplay.ui.settings.SettingsViewModel
import com.example.fluxplay.ui.theme.DarkBackground
import com.example.fluxplay.ui.theme.DarkSurface
import com.example.fluxplay.ui.theme.FluxplayTheme
import com.example.fluxplay.ui.theme.IndigoPrimary
import com.example.fluxplay.ui.theme.TextPrimary
import com.example.fluxplay.ui.theme.TextSecondary

enum class NavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    PLAYER("Player", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircleOutline, "tab_player"),
    STREAMS("Streams", Icons.Filled.LiveTv, Icons.Outlined.LiveTv, "tab_streams"),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History, "tab_history"),
    BOOKMARKS("Saved", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "tab_saved"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "tab_settings")
}

@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels {
        val app = application as FluxplayApp
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlayerViewModel(app, app.mediaRepository, app.settingsRepository) as T
            }
        }
    }

    private val discoverViewModel: DiscoverViewModel by viewModels {
        val app = application as FluxplayApp
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DiscoverViewModel(app.metadataRepository, app.mediaRepository, app.settingsRepository) as T
            }
        }
    }

    private val historyViewModel: HistoryViewModel by viewModels {
        val app = application as FluxplayApp
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HistoryViewModel(app.mediaRepository) as T
            }
        }
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val app = application as FluxplayApp
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(app.settingsRepository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settings by settingsViewModel.settings.collectAsState()
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

    override fun onStop() {
        super.onStop()
        val settings = (application as FluxplayApp).settingsRepository.settings.value
        if (!settings.backgroundPlay) {
            playerViewModel.pause()
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
    var currentTab by remember { mutableStateOf(NavigationTab.PLAYER) }
    val playerState by playerViewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = !playerState.isFullscreen,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = TextPrimary
                ) {
                    NavigationTab.values().forEach { tab ->
                        val selected = currentTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    tint = if (selected) IndigoPrimary else TextSecondary
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    color = if (selected) IndigoPrimary else TextSecondary
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = IndigoPrimary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (playerState.isFullscreen) androidx.compose.foundation.layout.PaddingValues() else innerPadding)
                .background(DarkBackground)
        ) {
            when (currentTab) {
                NavigationTab.PLAYER -> {
                    PlayerScreen(viewModel = playerViewModel)
                }
                NavigationTab.STREAMS -> {
                    DiscoverScreen(
                        viewModel = discoverViewModel,
                        onSelectMedia = { media, engine ->
                            playerViewModel.playMedia(media, preferredEngine = engine)
                            currentTab = NavigationTab.PLAYER
                        }
                    )
                }
                NavigationTab.HISTORY -> {
                    HistoryScreen(
                        viewModel = historyViewModel,
                        onPlayMedia = { media, engine, startPos ->
                            playerViewModel.playMedia(media, preferredEngine = engine, startPositionMs = startPos)
                            currentTab = NavigationTab.PLAYER
                        }
                    )
                }
                NavigationTab.BOOKMARKS -> {
                    BookmarksScreen(
                        viewModel = historyViewModel,
                        onPlayMedia = { media, engine ->
                            playerViewModel.playMedia(media, preferredEngine = engine)
                            currentTab = NavigationTab.PLAYER
                        }
                    )
                }
                NavigationTab.SETTINGS -> {
                    SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}
