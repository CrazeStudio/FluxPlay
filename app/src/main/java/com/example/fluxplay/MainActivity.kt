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
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fluxplay.ui.bookmarks.BookmarksScreen
import com.example.fluxplay.ui.discover.DiscoverScreen
import com.example.fluxplay.ui.discover.DiscoverViewModel
import com.example.fluxplay.ui.downloads.DownloadsScreen
import com.example.fluxplay.ui.downloads.DownloadsViewModel
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
    DOWNLOADS("Downloads", Icons.Filled.Download, Icons.Outlined.Download, "nav_tab_downloads"),
    BOOKMARKS("Saved", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "nav_tab_bookmarks")
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
                return DiscoverViewModel(app.mediaRepository) as T
            }
        }
    }

    private val downloadsViewModel: DownloadsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DownloadsViewModel(app, app.downloadRepository) as T
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
                return SettingsViewModel(app, app.settingsRepository, app.mediaRepository, app.downloadRepository) as T
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!settingsViewModel.settings.value.backgroundPlayEnabled) {
            playerViewModel.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!settingsViewModel.settings.value.backgroundPlayEnabled) {
            playerViewModel.pause()
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

            // Global Lifecycle Observer to guarantee background playback pausing
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, settings.backgroundPlayEnabled) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if ((event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) && !settings.backgroundPlayEnabled) {
                        playerViewModel.pause()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            FluxplayTheme(themeMode = settings.selectedTheme) {
                FluxplayMainApp(
                    playerViewModel = playerViewModel,
                    discoverViewModel = discoverViewModel,
                    downloadsViewModel = downloadsViewModel,
                    historyViewModel = historyViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluxplayMainApp(
    playerViewModel: PlayerViewModel,
    discoverViewModel: DiscoverViewModel,
    downloadsViewModel: DownloadsViewModel,
    historyViewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.PLAYER) }
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

    var showHistorySheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (!playerState.isFullscreen) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "FluxPlay",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = playerState.selectedEngine.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        // Quick Download URL Action
                        IconButton(
                            onClick = { showDownloadDialog = true },
                            modifier = Modifier.testTag("top_quick_download_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Download Video",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Top 3-Dot Overflow Menu
                        Box {
                            IconButton(
                                onClick = { showTopMenu = true },
                                modifier = Modifier.testTag("top_overflow_menu_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More Options",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            DropdownMenu(
                                expanded = showTopMenu,
                                onDismissRequest = { showTopMenu = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Watch History", fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        showTopMenu = false
                                        showHistorySheet = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    modifier = Modifier.testTag("menu_item_history")
                                )

                                DropdownMenuItem(
                                    text = { Text("Settings & Preferences", fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        showTopMenu = false
                                        showSettingsSheet = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    modifier = Modifier.testTag("menu_item_settings")
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                                DropdownMenuItem(
                                    text = { Text("Download Video by URL", fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        showTopMenu = false
                                        showDownloadDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Download, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    },
                                    modifier = Modifier.testTag("menu_item_download")
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
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
            NavigationTab.DOWNLOADS -> {
                DownloadsScreen(
                    viewModel = downloadsViewModel,
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
        }
    }

    // History Sheet / Dialog from Top 3-Dot Menu
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            HistoryScreen(
                viewModel = historyViewModel,
                onPlayMedia = { media ->
                    playerViewModel.loadMedia(media)
                    showHistorySheet = false
                    selectedTab = NavigationTab.PLAYER
                },
                onBack = { showHistorySheet = false }
            )
        }
    }

    // Settings Sheet / Dialog from Top 3-Dot Menu
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxHeight(0.92f)
        ) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { showSettingsSheet = false }
            )
        }
    }

    // Quick Download Dialog from Top Bar Action
    if (showDownloadDialog) {
        var inputUrl by remember { mutableStateOf("") }
        var inputTitle by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Download Video", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter direct video URL to download to offline storage:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("Video URL (.mp4, .mkv, .webm, etc.)") },
                        placeholder = { Text("https://example.com/video.mp4") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("Title (Optional)") },
                        placeholder = { Text("My Movie") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputUrl.isNotBlank()) {
                            downloadsViewModel.startDownload(inputUrl.trim(), inputTitle.trim())
                            showDownloadDialog = false
                            selectedTab = NavigationTab.DOWNLOADS
                        }
                    },
                    enabled = inputUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Start Download", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

