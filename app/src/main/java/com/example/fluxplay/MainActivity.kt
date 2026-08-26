package com.example.fluxplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fluxplay.data.model.DiscoverItem
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.ui.bookmarks.BookmarksScreen
import com.example.fluxplay.ui.discover.DiscoverScreen
import com.example.fluxplay.ui.discover.DiscoverViewModel
import com.example.fluxplay.ui.history.HistoryScreen
import com.example.fluxplay.ui.history.HistoryViewModel
import com.example.fluxplay.ui.player.PlayerScreen
import com.example.fluxplay.ui.player.PlayerViewModel
import com.example.fluxplay.ui.settings.SettingsScreen
import com.example.fluxplay.ui.settings.SettingsViewModel
import com.example.fluxplay.ui.theme.FluxSuccess
import com.example.fluxplay.ui.theme.FluxWarning
import com.example.fluxplay.ui.theme.FluxError
import com.example.fluxplay.ui.theme.FluxplayTheme

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as FluxplayApp
                @Suppress("UNCHECKED_CAST")
                return PlayerViewModel(app, app.mediaRepository) as T
            }
        }
    }

    private val discoverViewModel: DiscoverViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as FluxplayApp
                @Suppress("UNCHECKED_CAST")
                return DiscoverViewModel(app.metadataRepository, app.mediaRepository) as T
            }
        }
    }

    private val historyViewModel: HistoryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as FluxplayApp
                @Suppress("UNCHECKED_CAST")
                return HistoryViewModel(app.mediaRepository) as T
            }
        }
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as FluxplayApp
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(app.settingsRepository, app.mediaRepository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()

            FluxplayTheme(settings = settings) {
                FluxplayMainApp(
                    playerViewModel = playerViewModel,
                    discoverViewModel = discoverViewModel,
                    historyViewModel = historyViewModel,
                    settingsViewModel = settingsViewModel,
                    statusText = playerUiState.statusText,
                    statusType = playerUiState.statusType,
                    isFullscreen = playerUiState.isFullscreen
                )
            }
        }
    }
}

enum class NavigationTab(val label: String, val testTag: String) {
    PLAYER("Player", "nav_player"),
    DISCOVER("Discover", "nav_discover"),
    HISTORY("History", "nav_history"),
    BOOKMARKS("Saved", "nav_bookmarks"),
    SETTINGS("Settings", "nav_settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluxplayMainApp(
    playerViewModel: PlayerViewModel,
    discoverViewModel: DiscoverViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    statusText: String,
    statusType: String,
    isFullscreen: Boolean
) {
    var currentTab by remember { mutableStateOf(NavigationTab.PLAYER) }

    if (isFullscreen) {
        // Render Player in Fullscreen without TopBar or BottomBar
        PlayerScreen(viewModel = playerViewModel)
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF141416)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_fluxplay_logo),
                                    contentDescription = "Fluxplay Logo",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Fluxplay",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    },
                    actions = {
                        // Live Status Badge
                        val (textColor, bgColor) = when (statusType) {
                            "live" -> FluxSuccess to FluxSuccess.copy(alpha = 0.12f)
                            "warning" -> FluxWarning to FluxWarning.copy(alpha = 0.12f)
                            "error" -> FluxError to FluxError.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceContainer
                        }

                        Surface(
                            shape = CircleShape,
                            color = bgColor,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = statusText,
                                color = textColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("main_navigation_bar")
                ) {
                    NavigationTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        val icon = when (tab) {
                            NavigationTab.PLAYER -> Icons.Default.PlayCircle
                            NavigationTab.DISCOVER -> Icons.Default.MovieFilter
                            NavigationTab.HISTORY -> Icons.Default.History
                            NavigationTab.BOOKMARKS -> Icons.Default.Bookmark
                            NavigationTab.SETTINGS -> Icons.Default.Settings
                        }

                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = { Icon(icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentTab) {
                    NavigationTab.PLAYER -> {
                        PlayerScreen(viewModel = playerViewModel)
                    }
                    NavigationTab.DISCOVER -> {
                        DiscoverScreen(
                            viewModel = discoverViewModel,
                            onPlayMedia = { item: DiscoverItem ->
                                playerViewModel.playFromDiscover(item)
                                currentTab = NavigationTab.PLAYER
                            }
                        )
                    }
                    NavigationTab.HISTORY -> {
                        HistoryScreen(
                            viewModel = historyViewModel,
                            onPlayMedia = { item: MediaItemEntity ->
                                playerViewModel.playUrl(item.url, item)
                                currentTab = NavigationTab.PLAYER
                            }
                        )
                    }
                    NavigationTab.BOOKMARKS -> {
                        BookmarksScreen(
                            viewModel = historyViewModel,
                            onPlayMedia = { item: MediaItemEntity ->
                                playerViewModel.playUrl(item.url, item)
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
}
