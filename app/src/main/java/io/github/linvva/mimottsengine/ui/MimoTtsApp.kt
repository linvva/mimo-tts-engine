@file:Suppress("UnusedMaterial3ScaffoldPaddingParameter")

package io.github.linvva.mimottsengine.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SettingsVoice
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.linvva.mimottsengine.data.DEFAULT_STYLE_PROMPT
import io.github.linvva.mimottsengine.data.SettingsRepository
import io.github.linvva.mimottsengine.data.TtsSettings
import io.github.linvva.mimottsengine.data.UiStyle
import io.github.linvva.mimottsengine.ui.theme.LocalGlassBackdrop
import io.github.linvva.mimottsengine.ui.theme.LocalUiStyle
import io.github.linvva.mimottsengine.ui.theme.UiStyleProvider
import io.github.linvva.mimottsengine.ui.theme.uiAppearance
import io.github.linvva.mimottsengine.ui.components.AppButton
import io.github.linvva.mimottsengine.ui.components.AppOutlinedButton
import io.github.linvva.mimottsengine.ui.components.AppCard
import io.github.linvva.mimottsengine.ui.components.AppTextField
import io.github.linvva.mimottsengine.ui.components.AppSlider
import io.github.linvva.mimottsengine.ui.components.AppFilterChip
import io.github.linvva.mimottsengine.ui.components.GlassBackdrop
import io.github.linvva.mimottsengine.ui.components.glassSurface
import io.github.linvva.mimottsengine.ui.components.GlassBottomNavigation
import io.github.linvva.mimottsengine.ui.components.UiStylePicker
import io.github.linvva.mimottsengine.data.VoicePresets
import io.github.linvva.mimottsengine.http.LocalTtsHttpService
import io.github.linvva.mimottsengine.http.legadoConfig
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MimoTtsApp(
    settingsRepository: SettingsRepository,
    onTestSpeak: (String, (String, Boolean) -> Unit) -> Unit,
    isNotificationPermissionGranted: () -> Boolean,
    isIgnoringBatteryOptimizations: () -> Boolean,
    isLocalHttpServiceRunning: () -> Boolean,
    localHttpServiceError: () -> String?,
    permissionStateVersion: Int,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onStartLocalHttpService: () -> Unit,
    onStopLocalHttpService: () -> Unit,
) {
    val settings by settingsRepository.settings.collectAsState(initial = null)
    val uiStyle by settingsRepository.uiStyle.collectAsState(initial = null)
    val initialTab by produceState<MainTab?>(initialValue = null, key1 = settingsRepository) {
        val savedTab = settingsRepository.lastSelectedTab.first()
        val initialSettings = settingsRepository.settings.first()
        value = MainTab.entries.firstOrNull { it.storageKey == savedTab }
            ?: if (initialSettings.isReady) MainTab.Tts else MainTab.Settings
    }
    val scope = rememberCoroutineScope()
    val loadedSettings = settings ?: return
    val loadedInitialTab = initialTab ?: return
    val loadedUiStyle = uiStyle ?: return

    UiStyleProvider(loadedUiStyle) {
        MimoTtsScreen(
            settings = loadedSettings,
            onUiStyleChange = { scope.launch { settingsRepository.updateUiStyle(it) } },
            initialTab = loadedInitialTab,
            onTabSettled = { settingsRepository.updateLastSelectedTab(it) },
            onApiKeyChange = { scope.launch { settingsRepository.updateApiKey(it) } },
            onVoiceChange = { scope.launch { settingsRepository.updateVoice(it) } },
            onSpeedChange = { scope.launch { settingsRepository.updateSpeed(it) } },
            onSymbolPauseEnabledChange = { scope.launch { settingsRepository.updateSymbolPauseEnabled(it) } },
            onSymbolPauseChange = { scope.launch { settingsRepository.updateSymbolPauseMs(it) } },
            onPromptChange = { scope.launch { settingsRepository.updateStylePrompt(it) } },
            onResetPrompt = { scope.launch { settingsRepository.updateStylePrompt(DEFAULT_STYLE_PROMPT) } },
            onTestSpeak = onTestSpeak,
            isNotificationPermissionGranted = isNotificationPermissionGranted,
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
            isLocalHttpServiceRunning = isLocalHttpServiceRunning,
            localHttpServiceError = localHttpServiceError,
            permissionStateVersion = permissionStateVersion,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestBatteryOptimizationExemption = onRequestBatteryOptimizationExemption,
            onOpenAppDetails = onOpenAppDetails,
            onStartLocalHttpService = onStartLocalHttpService,
            onStopLocalHttpService = onStopLocalHttpService,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MimoTtsScreen(
    settings: TtsSettings,
    onUiStyleChange: (UiStyle) -> Unit,
    initialTab: MainTab,
    onTabSettled: suspend (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSymbolPauseEnabledChange: (Boolean) -> Unit,
    onSymbolPauseChange: (Int) -> Unit,
    onPromptChange: (String) -> Unit,
    onResetPrompt: () -> Unit,
    onTestSpeak: (String, (String, Boolean) -> Unit) -> Unit,
    isNotificationPermissionGranted: () -> Boolean,
    isIgnoringBatteryOptimizations: () -> Boolean,
    isLocalHttpServiceRunning: () -> Boolean,
    localHttpServiceError: () -> String?,
    permissionStateVersion: Int,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onStartLocalHttpService: () -> Unit,
    onStopLocalHttpService: () -> Unit,
) {
    val initialTabIndex = rememberSaveable {
        initialTab.ordinal
    }
    val pagerState = rememberPagerState(
        initialPage = initialTabIndex,
        pageCount = { MainTab.entries.size },
    )
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var prompt by remember { mutableStateOf(settings.stylePrompt) }
    var testText by remember { mutableStateOf("今晚的风很安静，适合慢慢听完这一章。") }
    var httpTestText by remember { mutableStateOf("这是来自本地 HTTP 接口的朗读测试。") }
    var httpTestResult by remember { mutableStateOf("") }
    var isHttpTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(isNotificationPermissionGranted()) }
    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations()) }
    var localHttpRunning by remember { mutableStateOf(isLocalHttpServiceRunning()) }
    var localHttpError by remember { mutableStateOf(localHttpServiceError()) }
    val focusManager = LocalFocusManager.current
    val navigationScope = rememberCoroutineScope()
    val hazeState = rememberHazeState()
    val glassBackdrop = LocalGlassBackdrop.current
    val glass = LocalUiStyle.current == UiStyle.LiquidGlass
    val density = LocalDensity.current
    var bottomBarHeight by remember { mutableStateOf(112.dp) }
    val topAppBarHeight = 64.dp * density.fontScale.coerceAtLeast(1f)
    val topContentPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + topAppBarHeight + 8.dp
    val selectedTab = MainTab.entries[pagerState.currentPage]

    fun refreshKeepAliveState() {
        notificationGranted = isNotificationPermissionGranted()
        batteryUnrestricted = isIgnoringBatteryOptimizations()
        localHttpRunning = isLocalHttpServiceRunning()
        localHttpError = localHttpServiceError()
    }

    LaunchedEffect(settings.apiKey) { apiKey = settings.apiKey }
    LaunchedEffect(settings.stylePrompt) { prompt = settings.stylePrompt }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            onTabSettled(MainTab.entries[page].storageKey)
        }
    }
    LaunchedEffect(permissionStateVersion) { refreshKeepAliveState() }

    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            containerColor = Color.Transparent,
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topContentPadding + 16.dp),
                ) {
                    if (glass) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                                .padding(horizontal = 16.dp)
                                .height(topAppBarHeight)
                                .glassSurface(RoundedCornerShape(26.dp), source = hazeState, tint = uiAppearance().fieldColor),
                        )
                    } else {
                        TopProgressiveBlur(hazeState = hazeState)
                    }
                    TopAppBar(
                        expandedHeight = topAppBarHeight,
                        title = {
                            Column(Modifier.padding(start = if (glass) 16.dp else 0.dp)) {
                                Text(selectedTab.title)
                                Text(
                                    text = selectedTab.subtitle(settings.isReady),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                    )
                }
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (glass) {
                        Box(
                            modifier = Modifier
                                .padding(WindowInsets.navigationBars.asPaddingValues())
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            GlassBottomNavigation(pagerState, hazeState) { tab ->
                                navigationScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().height(112.dp)) { BottomProgressiveBlur(hazeState) }
                        NavigationBar(containerColor = Color.Transparent) {
                            MainTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    modifier = Modifier.testTag("nav_${tab.storageKey}"),
                                    selected = selectedTab == tab,
                                    onClick = {
                                        navigationScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(tab.title) },
                                )
                            }
                        }
                    }
                }
            },
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
            ) {
                // Cards sample the wallpaper alone; the fixed bars also sample scrolling content.
                Box(Modifier.fillMaxSize().then(
                    if (glass && glassBackdrop != null) Modifier.hazeSource(glassBackdrop) else Modifier,
                )) {
                    if (glass) GlassBackdrop() else TonalBackdrop()
                }
                HorizontalPager(
                    state = pagerState,
                    // Keep the three tab forms composed between gestures.
                    beyondViewportPageCount = MainTab.entries.lastIndex,
                    modifier = Modifier
                        .fillMaxSize(),
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .testTag("page_${MainTab.entries[page].storageKey}")
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Spacer(Modifier.height(topContentPadding))
                        when (MainTab.entries[page]) {
                            MainTab.Tts -> TtsTabContent(
                                settings = settings,
                                onOpenSettings = {
                                    navigationScope.launch {
                                        pagerState.animateScrollToPage(MainTab.Settings.ordinal)
                                    }
                                },
                                onVoiceChange = onVoiceChange,
                                onSpeedChange = onSpeedChange,
                                testText = testText,
                                onTestTextChange = { testText = it },
                                testResult = testResult,
                                isTesting = isTesting,
                                onTestSpeak = {
                                    focusManager.clearFocus()
                                    onTestSpeak(testText) { message, running ->
                                        testResult = message
                                        isTesting = running
                                    }
                                },
                            )

                            MainTab.Http -> HttpTabContent(
                                settings = settings,
                                onOpenSettings = {
                                    navigationScope.launch {
                                        pagerState.animateScrollToPage(MainTab.Settings.ordinal)
                                    }
                                },
                                running = localHttpRunning,
                                error = localHttpError,
                                onStart = {
                                    onStartLocalHttpService()
                                    refreshKeepAliveState()
                                },
                                onStop = {
                                    onStopLocalHttpService()
                                    refreshKeepAliveState()
                                },
                                onVoiceChange = onVoiceChange,
                                testText = httpTestText,
                                onTestTextChange = { httpTestText = it },
                                testResult = httpTestResult,
                                isTesting = isHttpTesting,
                                onTestSpeak = {
                                    focusManager.clearFocus()
                                    onTestSpeak(httpTestText) { message, running ->
                                        httpTestResult = message
                                        isHttpTesting = running
                                    }
                                },
                            )

                            MainTab.Settings -> SettingsTabContent(
                                settings = settings,
                                onSymbolPauseEnabledChange = onSymbolPauseEnabledChange,
                                onSymbolPauseChange = onSymbolPauseChange,
                                onUiStyleChange = onUiStyleChange,
                                apiKey = apiKey,
                                showApiKey = showApiKey,
                                onShowApiKeyChange = { showApiKey = it },
                                onApiKeyChange = {
                                    apiKey = it
                                    onApiKeyChange(it)
                                },
                                prompt = prompt,
                                onPromptChange = {
                                    prompt = it
                                    onPromptChange(it)
                                },
                                onResetPrompt = onResetPrompt,
                                notificationGranted = notificationGranted,
                                batteryUnrestricted = batteryUnrestricted,
                                onRequestNotificationPermission = {
                                    onRequestNotificationPermission()
                                    refreshKeepAliveState()
                                },
                                onRequestBatteryOptimizationExemption = {
                                    onRequestBatteryOptimizationExemption()
                                    refreshKeepAliveState()
                                },
                                onOpenAppDetails = onOpenAppDetails,
                            )
                        }
                        Spacer(Modifier.height(bottomBarHeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun TonalBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                        0.28f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.30f),
                    ),
                )
            ),
    )
}

@Composable
private fun TopProgressiveBlur(hazeState: HazeState) {
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                performanceMode = HazePerformanceMode.Default,
                style = HazeBlurStyle {
                    backgroundColor(surfaceContainer)
                    colorEffects(listOf(HazeColorEffect.tint(surfaceContainerHighest.copy(alpha = 0.34f))))
                    blurRadius(32.dp)
                    noiseFactor(0.04f)
                    progressive(HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0f,
                    ))
                },
            )
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to surfaceContainerHighest.copy(alpha = 0.58f),
                        0.44f to surfaceContainerHigh.copy(alpha = 0.34f),
                        1f to Color.Transparent,
                    ),
                )
            ),
    )
}

@Composable
private fun BottomProgressiveBlur(hazeState: HazeState) {
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                performanceMode = HazePerformanceMode.Default,
                style = HazeBlurStyle {
                    backgroundColor(surfaceContainer)
                    colorEffects(listOf(HazeColorEffect.tint(surfaceContainerHighest.copy(alpha = 0.34f))))
                    blurRadius(32.dp)
                    noiseFactor(0.04f)
                    progressive(HazeProgressive.verticalGradient(
                        startIntensity = 0f,
                        endIntensity = 1f,
                    ))
                },
            )
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.56f to surfaceContainerHigh.copy(alpha = 0.32f),
                        1f to surfaceContainerHighest.copy(alpha = 0.58f),
                    ),
                )
            ),
    )
}

internal enum class MainTab(
    val storageKey: String,
    val title: String,
    val icon: ImageVector,
) {
    Tts("tts", "TTS", Icons.Rounded.RecordVoiceOver),
    Http("http", "HTTP", Icons.Rounded.Language),
    Settings("settings", "设置", Icons.Rounded.Key);

    fun subtitle(isReady: Boolean): String {
        return when (this) {
            Tts -> if (isReady) "系统朗读引擎" else "需要先配置 API Key"
            Http -> if (isReady) "本地 HTTP 与 Legado" else "需要先配置 API Key"
            Settings -> if (isReady) "通用配置与系统权限" else "配置 API Key 后启用"
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TtsTabContent(
    settings: TtsSettings,
    onOpenSettings: () -> Unit,
    onVoiceChange: (String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    testText: String,
    onTestTextChange: (String) -> Unit,
    testResult: String,
    isTesting: Boolean,
    onTestSpeak: () -> Unit,
) {
    MissingApiKeyBanner(settings = settings, onOpenSettings = onOpenSettings)

    SettingsSection(title = "声音", icon = Icons.Rounded.RecordVoiceOver) {
        VoicePicker(settings.voice, onVoiceChange)
        SpeedControl(settings = settings, onSpeedChange = onSpeedChange)
    }

    TestPanel(
        settings = settings,
        testText = testText,
        onTestTextChange = onTestTextChange,
        testResult = testResult,
        isTesting = isTesting,
        onTestSpeak = onTestSpeak,
    )
}

@Composable
private fun HttpTabContent(
    settings: TtsSettings,
    onOpenSettings: () -> Unit,
    running: Boolean,
    error: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onVoiceChange: (String) -> Unit,
    testText: String,
    onTestTextChange: (String) -> Unit,
    testResult: String,
    isTesting: Boolean,
    onTestSpeak: () -> Unit,
) {
    MissingApiKeyBanner(settings = settings, onOpenSettings = onOpenSettings)

    LocalHttpSection(
        settings = settings,
        running = running,
        error = error,
        onStart = onStart,
        onStop = onStop,
    )

    SettingsSection(title = "HTTP 音色", icon = Icons.Rounded.RecordVoiceOver) {
        VoicePicker(settings.voice, onVoiceChange)
    }

    TestPanel(
        settings = settings,
        testText = testText,
        onTestTextChange = onTestTextChange,
        testResult = testResult,
        isTesting = isTesting,
        onTestSpeak = onTestSpeak,
    )
}

@Composable
private fun SymbolPauseSection(
    settings: TtsSettings,
    onSymbolPauseEnabledChange: (Boolean) -> Unit,
    onSymbolPauseChange: (Int) -> Unit,
) {
    SettingsSection(title = "纯符号段落", icon = Icons.Rounded.Tune) {
        val appearance = uiAppearance()
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("本地静音处理", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = settings.symbolPauseEnabled,
                onCheckedChange = onSymbolPauseEnabledChange,
                modifier = Modifier.testTag("symbol_pause_enabled"),
                colors = if (appearance.isGlass) SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                    checkedTrackColor = appearance.accentColor.copy(alpha = 0.26f),
                    checkedBorderColor = appearance.borderColor,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = appearance.fieldColor,
                    uncheckedBorderColor = appearance.borderColor,
                ) else SwitchDefaults.colors(),
            )
        }
        if (settings.symbolPauseEnabled) {
            val pauseSteps = remember {
                (0..1000 step 50).toList() + (1100..3000 step 100) + (3500..10000 step 500)
            }
            val pauseStep = pauseSteps.indices.minBy { abs(pauseSteps[it] - settings.symbolPauseMs) }
            Text(
                if (settings.symbolPauseMs == 0) "停顿时长：0 毫秒（尽快跳过）" else "停顿时长：${settings.symbolPauseMs} 毫秒",
                style = MaterialTheme.typography.titleSmall,
            )
            AppSlider(
                value = pauseStep.toFloat(),
                onValueChange = { onSymbolPauseChange(pauseSteps[it.roundToInt().coerceIn(pauseSteps.indices)]) },
                valueRange = 0f..pauseSteps.lastIndex.toFloat(),
                steps = pauseSteps.size - 2,
            )
        }
        Text(
            if (settings.symbolPauseEnabled) {
                "整段不含文字或数字时（如……、“……”、——），本地静音停顿，不请求 MiMo；TTS 与 HTTP 共用此设置。0 毫秒表示尽快跳过，播放器仍可能有极短间隔。"
            } else {
                "已关闭，纯符号段落将交给 MiMo 合成；再次开启会恢复已保存的停顿时长。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsTabContent(
    settings: TtsSettings,
    onSymbolPauseEnabledChange: (Boolean) -> Unit,
    onSymbolPauseChange: (Int) -> Unit,
    onUiStyleChange: (UiStyle) -> Unit,
    apiKey: String,
    showApiKey: Boolean,
    onShowApiKeyChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onResetPrompt: () -> Unit,
    notificationGranted: Boolean,
    batteryUnrestricted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    onOpenAppDetails: () -> Unit,
) {
    SettingsSection(title = "外观", icon = Icons.Rounded.Tune) {
        UiStylePicker(onUiStyleChange)
    }

    SettingsSection(title = "连接", icon = Icons.Rounded.Key) {
        ApiKeyField(
            apiKey = apiKey,
            showApiKey = showApiKey,
            onShowApiKeyChange = onShowApiKeyChange,
            onApiKeyChange = onApiKeyChange,
        )
    }

    SettingsSection(title = "朗读风格", icon = Icons.Rounded.Tune) {
        PromptEditor(
            prompt = prompt,
            onPromptChange = onPromptChange,
            onResetPrompt = onResetPrompt,
        )
    }

    SymbolPauseSection(
        settings = settings,
        onSymbolPauseEnabledChange = onSymbolPauseEnabledChange,
        onSymbolPauseChange = onSymbolPauseChange,
    )

    SettingsSection(title = "后台保活", icon = Icons.Rounded.Notifications) {
        KeepAliveItem(
            label = "通知权限",
            granted = notificationGranted,
            grantedText = "已允许",
            missingText = "锁屏朗读时前台通知可能无法显示",
        )
        KeepAliveItem(
            label = "电池优化",
            granted = batteryUnrestricted,
            grantedText = "已忽略电池优化",
            missingText = "可能被 MIUI 省电策略暂停",
        )
        KeepAliveActions(
            notificationGranted = notificationGranted,
            batteryUnrestricted = batteryUnrestricted,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestBatteryOptimizationExemption = onRequestBatteryOptimizationExemption,
            onOpenAppDetails = onOpenAppDetails,
        )
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = "在 MIUI 中请将省电策略设为无限制，并允许后台运行。",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ApiKeyField(
    apiKey: String,
    showApiKey: Boolean,
    onShowApiKeyChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
) {
    AppTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        modifier = Modifier.fillMaxWidth().testTag("api_key"),
        label = { Text("Mimo API Key") },
        supportingText = {
            Text(if (apiKey.isBlank()) "保存后即可启用系统 TTS 引擎" else "已本机保存，用于请求 Mimo TTS")
        },
        singleLine = true,
        visualTransformation = if (showApiKey || apiKey.isBlank()) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            if (apiKey.isNotBlank()) {
                IconButton(onClick = { onShowApiKeyChange(!showApiKey) }) {
                    Icon(
                        imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = null,
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedControl(
    settings: TtsSettings,
    onSpeedChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("语速", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${"%.1f".format(settings.speed)}x",
                style = MaterialTheme.typography.labelLarge,
                color = if (uiAppearance().isGlass) uiAppearance().accentColor else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AppSlider(
            value = settings.speed,
            onValueChange = onSpeedChange,
            valueRange = 0.6f..1.4f,
            steps = 7,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("0.8x" to 0.8f, "1.0x" to 1.0f, "1.2x" to 1.2f).forEach { (label, value) ->
                AppFilterChip(
                    selected = settings.speed == value,
                    onClick = { onSpeedChange(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun PromptEditor(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onResetPrompt: () -> Unit,
) {
    AppTextField(
        value = prompt,
        onValueChange = onPromptChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp).testTag("style_prompt"),
        label = { Text("Prompt") },
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = onResetPrompt,
            colors = if (uiAppearance().isGlass) ButtonDefaults.textButtonColors(
                contentColor = uiAppearance().accentColor,
            ) else ButtonDefaults.textButtonColors(),
        ) {
            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("恢复默认")
        }
    }
}

@Composable
private fun MissingApiKeyBanner(
    settings: TtsSettings,
    onOpenSettings: () -> Unit,
) {
    if (settings.isReady) {
        return
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "需要配置 API Key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                text = "配置后才能使用 TTS 朗读和本地 HTTP 服务。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f),
            )
            AppButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("去设置")
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun KeepAliveItem(
    label: String,
    granted: Boolean,
    grantedText: String,
    missingText: String,
) {
    ListItem(
        supportingContent = {
            Text(if (granted) grantedText else missingText)
        },
        leadingContent = {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun KeepAliveActions(
    notificationGranted: Boolean,
    batteryUnrestricted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    onOpenAppDetails: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!notificationGranted) {
            KeepAliveActionButton(
                text = "允许通知",
                icon = Icons.Rounded.Notifications,
                primary = true,
                onClick = onRequestNotificationPermission,
            )
        }
        if (!batteryUnrestricted) {
            KeepAliveActionButton(
                text = "忽略电池优化",
                icon = Icons.Rounded.PowerSettingsNew,
                primary = !notificationGranted,
                onClick = onRequestBatteryOptimizationExemption,
            )
        }
        KeepAliveActionButton(
            text = "应用详情",
            icon = Icons.Rounded.Info,
            primary = false,
            onClick = onOpenAppDetails,
        )
    }
}

@Composable
private fun KeepAliveActionButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
) {
    if (primary) {
        AppButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(text)
        }
    } else {
        AppOutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(text)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalHttpSection(
    settings: TtsSettings,
    running: Boolean,
    error: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    var showLegadoConfig by remember { mutableStateOf(false) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val appearance = uiAppearance()
    val isDebug = context.packageName.endsWith(".debug")
    val config = remember(settings.voice, isDebug, showLegadoConfig) {
        legadoConfig(settings.voice, isDebug)
    }
    fun copyLegadoConfig(streaming: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val freshConfig = legadoConfig(settings.voice, isDebug, streaming)
        clipboard.setPrimaryClip(ClipData.newPlainText("Legado Mimo TTS", freshConfig))
    }

    SettingsSection(title = "本地 HTTP 接口", icon = Icons.Rounded.Http) {
        KeepAliveItem(
            label = "服务状态",
            granted = running && settings.isReady,
            grantedText = "运行中 · ${LocalTtsHttpService.BASE_URL}",
            missingText = when {
                !settings.isReady -> "需要先配置 Mimo API Key"
                error != null -> error
                else -> "未运行"
            },
        )
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${LocalTtsHttpService.BASE_URL}/tts",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Legado 在线朗读源会请求本机接口，由本应用支持 AAC 流式返回和完整 WAV。边合成边播放需在阅读中开启流式朗读。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (running) {
            AppOutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp),
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("停止本地服务")
            }
        } else {
            AppButton(
                onClick = onStart,
                enabled = settings.isReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp),
            ) {
                Icon(Icons.Rounded.Http, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("启动本地服务")
            }
        }
        LegadoConfigSplitButton(
            onCopy = { copyLegadoConfig() },
            onShow = { showLegadoConfig = true },
        )
    }

    if (showLegadoConfig) {
        ModalBottomSheet(
            onDismissRequest = { showLegadoConfig = false },
            sheetState = sheetState,
            modifier = if (appearance.isGlass) Modifier.glassSurface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tint = appearance.popupTint,
                soft = true,
            ) else Modifier,
            shape = if (appearance.isGlass) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else BottomSheetDefaults.ExpandedShape,
            containerColor = if (appearance.isGlass) Color.Transparent else BottomSheetDefaults.ContainerColor,
        ) {
            LegadoConfigSheet(
                config = config,
                onCopy = { copyLegadoConfig() },
                onCopyWav = { copyLegadoConfig(streaming = false) },
                onClose = { showLegadoConfig = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LegadoConfigSplitButton(
    onCopy: () -> Unit,
    onShow: () -> Unit,
) {
    if (uiAppearance().isGlass) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(onClick = onCopy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("复制 Legado 配置")
            }
            AppOutlinedButton(onClick = onShow, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Visibility, contentDescription = "查看配置")
            }
        }
        return
    }
    val size = 46.dp
    val trailingButtonWidth = 48.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val leadingButtonWidth = maxWidth - trailingButtonWidth - SplitButtonDefaults.Spacing

        SplitButtonLayout(
            modifier = Modifier.fillMaxWidth(),
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .width(leadingButtonWidth)
                        .heightIn(size),
                    shapes = SplitButtonDefaults.leadingButtonShapesFor(size),
                    contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(size),
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(size)),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("复制 Legado 配置")
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    onClick = onShow,
                    modifier = Modifier
                        .width(trailingButtonWidth)
                        .heightIn(size),
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(size),
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(size),
                ) {
                    Icon(
                        Icons.Rounded.Visibility,
                        contentDescription = "查看配置",
                        modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(size)),
                    )
                }
            },
        )
    }
}

@Composable
private fun LegadoConfigSheet(
    config: String,
    onCopy: () -> Unit,
    onCopyWav: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Legado 配置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = config,
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        AppButton(
            onClick = onCopy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp),
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("复制流式配置")
        }
        AppOutlinedButton(
            onClick = onCopyWav,
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
        ) {
            Text("复制 WAV 兼容配置")
        }
        Text(
            text = "首次升级请替换旧源并重新选择角色音色。流式 AAC 为有损编码，生成期间时长可能未知；精确定位可使用 WAV 兼容配置。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppOutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp),
        ) {
            Text("关闭")
        }
    }
}

@Composable
private fun TestPanel(
    settings: TtsSettings,
    testText: String,
    onTestTextChange: (String) -> Unit,
    testResult: String,
    isTesting: Boolean,
    onTestSpeak: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = if (uiAppearance().isGlass) uiAppearance().accentColor else MaterialTheme.colorScheme.primary)
                Text("测试朗读", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            AppTextField(
                value = testText,
                onValueChange = onTestTextChange,
                modifier = Modifier.fillMaxWidth().testTag("test_text"),
                label = { Text("测试文本") },
                minLines = 2,
            )
            AppButton(
                onClick = onTestSpeak,
                enabled = settings.isReady && testText.isNotBlank() && !isTesting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (uiAppearance().isGlass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                }
                Spacer(Modifier.size(8.dp))
                Text(if (isTesting) "正在朗读" else "测试朗读")
            }
            AnimatedVisibility(testResult.isNotBlank()) {
                Text(
                    text = testResult,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(selectedVoice: String, onVoiceChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = VoicePresets.firstOrNull { it.id == selectedVoice } ?: VoicePresets.first()
    val appearance = uiAppearance()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        AppTextField(
            value = "${selected.label} · ${selected.description}",
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .testTag("voice_picker"),
            readOnly = true,
            label = { Text("音色") },
            leadingIcon = { Icon(Icons.Rounded.SettingsVoice, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTag("voice_menu").then(
                if (appearance.isGlass) Modifier.glassSurface(appearance.cardShape, tint = appearance.popupTint) else Modifier,
            ),
            shape = if (appearance.isGlass) appearance.cardShape else MenuDefaults.shape,
            containerColor = if (appearance.isGlass) Color.Transparent else MenuDefaults.containerColor,
            tonalElevation = if (appearance.isGlass) 0.dp else MenuDefaults.TonalElevation,
            shadowElevation = if (appearance.isGlass) 0.dp else MenuDefaults.ShadowElevation,
        ) {
            VoicePresets.forEach { voice ->
                val isSelected = voice.id == selected.id
                DropdownMenuItem(
                    modifier = Modifier.testTag("voice_option_${voice.id}").then(
                        if (appearance.isGlass) Modifier
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(appearance.fieldShape)
                            .background(if (isSelected) appearance.selectionColor else Color.Transparent)
                            .semantics { this.selected = isSelected }
                        else Modifier,
                    ),
                    trailingIcon = if (appearance.isGlass && isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = appearance.accentColor) }
                    } else null,
                    text = {
                        Column {
                            Text(voice.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                voice.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onVoiceChange(voice.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
