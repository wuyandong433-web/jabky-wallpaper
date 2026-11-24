package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.darken.ExtraDimDetector
import com.example.diagnostics.WallpaperDiagnosticsRecorder
import com.example.power.BatterySaverPolicy
import com.example.ui.AppLocaleHelper
import com.example.ui.ProvideAppLocale
import com.example.ui.WallpaperViewModel
import com.example.ui.components.DarkenConditionalDialog
import com.example.ui.screens.DiagnosticsConditionalDialog
import com.example.ui.screens.EffectsScreen
import com.example.ui.screens.MediaEditorScreen
import com.example.ui.screens.MediaLibraryScreen
import com.example.ui.screens.MediaPickerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppTab(
    @StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    STUDIO(R.string.tab_studio, Icons.Filled.Wallpaper, Icons.Outlined.Wallpaper, "tab_studio"),
    BEHAVIOR(R.string.tab_behavior, Icons.Filled.Tune, Icons.Outlined.Tune, "tab_behavior"),
    MEDIA(R.string.tab_library, Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary, "tab_media"),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "tab_settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: WallpaperViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WallpaperDiagnosticsRecorder.initialize(applicationContext)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkStatus()
    }
}

@Composable
fun MainAppScaffold(viewModel: WallpaperViewModel) {
    val appState by viewModel.appState.collectAsState()
    ProvideAppLocale(languageTag = appState.appLanguage) {
        MainAppScaffoldContent(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScaffoldContent(viewModel: WallpaperViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(AppTab.STUDIO) }
    var selectingMediaForPage by remember { mutableStateOf<Int?>(null) }
    var editingPageIndex by remember { mutableStateOf<Int?>(null) }
    var isSavingMedia by remember { mutableStateOf(false) }
    var isAssigningMedia by remember { mutableStateOf(false) }
    var showDarkenDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    val isWallpaperActive by viewModel.isWallpaperActive.collectAsState()
    val appState by viewModel.appState.collectAsState()
    val originals by viewModel.originals.collectAsState()
    val editorSplitFraction by viewModel.editorPreviewSplitFraction.collectAsState()
    val isSystemDarkMode by viewModel.isSystemDarkMode.collectAsState()
    val powerSnapshot by viewModel.powerSnapshot.collectAsState()
    val diagnostics by WallpaperDiagnosticsRecorder.state.collectAsState()
    val darkThemeInUi = isSystemInDarkTheme()

    LaunchedEffect(darkThemeInUi) {
        viewModel.refreshSystemDarkMode(darkThemeInUi)
    }

    val pageToEdit = editingPageIndex?.let { index ->
        appState.pages.find { it.pageIndex == index }?.let { page ->
            originals.find { it.id == page.originalId }?.let { media -> index to (page to media) }
        }
    }
    if (pageToEdit != null) {
        val (pageIndex, pageAndMedia) = pageToEdit
        val (page, media) = pageAndMedia
        MediaEditorScreen(
            media = media,
            page = page,
            pageCount = appState.calibration.pageCount,
            isSaving = isSavingMedia,
            initialSplitFraction = editorSplitFraction,
            onSplitFractionChange = viewModel::updateEditorPreviewSplitFraction,
            onDismiss = { if (!isSavingMedia) editingPageIndex = null },
            onSave = { updatedPage ->
                val screenWidth = context.resources.displayMetrics.widthPixels
                val screenHeight = context.resources.displayMetrics.heightPixels
                isSavingMedia = true
                viewModel.updatePageSettings(pageIndex, updatedPage, screenWidth, screenHeight) { success ->
                    isSavingMedia = false
                    if (success) {
                        editingPageIndex = null
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_save_page_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    } else if (selectingMediaForPage != null) {
        MediaPickerScreen(
            pageIndex = selectingMediaForPage!!,
            viewModel = viewModel,
            isAssigning = isAssigningMedia,
            onBack = { if (!isAssigningMedia) selectingMediaForPage = null },
            onSelectMedia = { media ->
                selectingMediaForPage?.let { pageIndex ->
                    val screenWidth = context.resources.displayMetrics.widthPixels
                    val screenHeight = context.resources.displayMetrics.heightPixels
                    isAssigningMedia = true
                    viewModel.assignOriginalToPage(media.id, pageIndex, screenWidth, screenHeight) { success ->
                        isAssigningMedia = false
                        if (success) {
                            selectingMediaForPage = null
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_assign_media_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            },
            onOpenLibrary = {
                selectingMediaForPage = null
                selectedTab = AppTab.MEDIA
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        Row(
                            modifier = Modifier.padding(start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(visible = selectedTab == AppTab.STUDIO) {
                                val isBatterySaverActive = BatterySaverPolicy.isBatterySaverActive(
                                    appState.batterySaver,
                                    powerSnapshot
                                )
                                val isDarkenActiveNow = ExtraDimDetector.isDimConditionActive(
                                    context = context,
                                    config = appState.darken,
                                    isDarkModeOverride = isSystemDarkMode,
                                    isBatterySaverActive = isBatterySaverActive
                                )
                                val darkenContainerColor = when {
                                    appState.darken.enabled && isDarkenActiveNow -> Color(0xFFF59E0B).copy(alpha = 0.22f)
                                    appState.darken.enabled -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                                }
                                val darkenIconColor = when {
                                    appState.darken.enabled && isDarkenActiveNow -> Color(0xFFD97706)
                                    appState.darken.enabled -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val darkenCd = stringResource(R.string.cd_conditional_menu_darken)

                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable(
                                            onClickLabel = darkenCd,
                                            role = Role.Button
                                        ) {
                                            showDarkenDialog = true
                                        }
                                        .testTag("conditional_menu_darken"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = darkenContainerColor,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Brightness4,
                                                contentDescription = darkenCd,
                                                tint = darkenIconColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            if (appState.darken.enabled && isDarkenActiveNow) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF10B981))
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = selectedTab == AppTab.SETTINGS) {
                                val hasOverlay = appState.showOffsetDebugOverlay ||
                                    appState.showFrameDebugIndicator ||
                                    appState.showSignalDebugOverlay
                                val diagContainerColor = when {
                                    diagnostics.isRecording -> MaterialTheme.colorScheme.errorContainer
                                    hasOverlay -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                                }
                                val diagIconColor = when {
                                    diagnostics.isRecording -> MaterialTheme.colorScheme.error
                                    hasOverlay -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val diagCd = stringResource(R.string.cd_conditional_menu_diagnostics)

                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable(
                                            onClickLabel = diagCd,
                                            role = Role.Button
                                        ) {
                                            showDiagnosticsDialog = true
                                        }
                                        .testTag("conditional_menu_diagnostics"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = diagContainerColor,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.BugReport,
                                                contentDescription = diagCd,
                                                tint = diagIconColor,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        val actionLabel = if (isWallpaperActive) {
                            stringResource(R.string.cd_preview_wallpaper_settings)
                        } else {
                            stringResource(R.string.cd_apply_live_wallpaper)
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (isWallpaperActive) {
                                Color(0xFF10B981).copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable(
                                    onClickLabel = actionLabel,
                                    role = Role.Button
                                ) {
                                    viewModel.applyWallpaperIntent(context)
                                }
                                .testTag("apply_wallpaper_button")
                        ) {
                            Text(
                                text = if (isWallpaperActive) {
                                    stringResource(R.string.status_active)
                                } else {
                                    stringResource(R.string.action_apply)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isWallpaperActive) {
                                    Color(0xFF047857)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    AppTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        val tabTitle = stringResource(tab.titleRes)
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                selectedTab = tab
                            },
                            icon = {
                                Icon(
                                    if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tabTitle,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tabTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ScreenTransition"
                ) { currentTab ->
                    when (currentTab) {
                        AppTab.STUDIO -> StudioScreen(
                            viewModel = viewModel,
                            onChooseMediaForPage = { pageIdx ->
                                selectingMediaForPage = pageIdx
                            },
                            onEditPage = { pageIndex ->
                                editingPageIndex = pageIndex
                            }
                        )
                        AppTab.BEHAVIOR -> EffectsScreen(viewModel = viewModel)
                        AppTab.MEDIA -> MediaLibraryScreen(viewModel = viewModel)
                        AppTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }

        if (showDarkenDialog && selectedTab == AppTab.STUDIO) {
            DarkenConditionalDialog(
                appState = appState,
                isSystemDarkMode = isSystemDarkMode,
                loadPageThumbnail = { pageIndex ->
                    viewModel.loadPagePreviewBitmap(pageIndex, 180, 320)
                },
                onDismiss = { showDarkenDialog = false },
                onConfirm = { newDarken, alreadyDarkFlags ->
                    viewModel.updateDarkenAndPageFlags(newDarken, alreadyDarkFlags)
                    showDarkenDialog = false
                }
            )
        }

        if (showDiagnosticsDialog && selectedTab == AppTab.SETTINGS) {
            DiagnosticsConditionalDialog(
                viewModel = viewModel,
                onDismiss = { showDiagnosticsDialog = false }
            )
        }
    }
}
