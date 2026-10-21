package io.github.linvva.mimottsengine.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import io.github.linvva.mimottsengine.data.SettingsRepository
import io.github.linvva.mimottsengine.data.UiStyle
import io.github.linvva.mimottsengine.data.VoicePresets
import io.github.linvva.mimottsengine.ui.theme.MimoTtsTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class UiStyleSwitchTest {
    @get:Rule val compose = createComposeRule()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private lateinit var directory: File
    private lateinit var repository: SettingsRepository

    @Before fun prepare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.cacheDir, "ui-style-test-${System.nanoTime()}").apply { mkdirs() }
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "settings.preferences_pb") }
        repository = SettingsRepository(store)
    }

    @After fun cleanUp() = runBlocking {
        scope.cancel()
        job.join()
        directory.deleteRecursively()
        Unit
    }

    @Test fun selectingGlassKeepsSettingsTabAndEditedPrompt() {
        showApp()
        compose.onNodeWithTag("style_prompt").performScrollTo().performTextReplacement("保留这段中文提示词")
        compose.onNodeWithTag("style_liquid_glass").performScrollTo().performClick()
        waitForGlass(true)
        compose.onNodeWithTag("nav_settings").assertIsSelected()
        compose.onNodeWithTag("style_liquid_glass").assertIsSelected()
        compose.onNodeWithTag("style_prompt").performScrollTo().assertTextContains("保留这段中文提示词")
        compose.runOnIdle {
            runBlocking { check(repository.uiStyle.first() == UiStyle.LiquidGlass) }
        }
    }

    @Test fun changingStyleKeepsTtsDraftAndCurrentTab() = runBlocking {
        repository.updateApiKey("test-credential")
        repository.updateLastSelectedTab("tts")
        showApp()
        val field = compose.onNode(hasTestTag("test_text") and hasAnyAncestor(hasTestTag("page_tts")))
        field.performScrollTo().performTextReplacement("切换外观后继续编辑")
        repository.updateUiStyle(UiStyle.LiquidGlass)
        waitForGlass(true)
        compose.onNodeWithTag("nav_tts").assertIsSelected()
        field.assertTextContains("切换外观后继续编辑")
        repository.updateUiStyle(UiStyle.Classic)
        waitForGlass(false)
        compose.onNodeWithTag("nav_tts").assertIsSelected()
        field.assertTextContains("切换外观后继续编辑")
    }

    @Test fun glassVoiceMenuSelectsAndRestoresChineseVoice() = runBlocking {
        repository.updateApiKey("test-credential")
        repository.updateLastSelectedTab("tts")
        repository.updateUiStyle(UiStyle.LiquidGlass)
        val initial = VoicePresets.first()
        val next = VoicePresets.first { it.id == "冰糖" }
        showApp()
        waitForGlass(true)
        val picker = compose.onNode(hasTestTag("voice_picker") and hasAnyAncestor(hasTestTag("page_tts")))
        picker.performScrollTo().performClick()
        compose.onNodeWithTag("voice_option_${initial.id}").assertIsSelected()
        compose.onNodeWithTag("voice_option_${next.id}").performScrollTo().performClick()
        compose.onNodeWithTag("voice_menu").assertDoesNotExist()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("voice_picker") and hasText("${next.label} · ${next.description}"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        picker.performClick()
        compose.onNodeWithTag("voice_option_${next.id}").assertIsSelected()
        compose.onNodeWithTag("voice_option_${next.id}").performClick()
        compose.onNodeWithTag("voice_menu").assertDoesNotExist()
        compose.onNodeWithTag("nav_tts").assertIsSelected()
    }

    private fun waitForGlass(visible: Boolean) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("glass_navigation").fetchSemanticsNodes().isNotEmpty() == visible
        }
    }

    private fun showApp() {
        compose.setContent {
            MimoTtsTheme {
                MimoTtsApp(
                    settingsRepository = repository,
                    onTestSpeak = { _, _ -> },
                    isNotificationPermissionGranted = { true },
                    isIgnoringBatteryOptimizations = { true },
                    isLocalHttpServiceRunning = { false },
                    localHttpServiceError = { null },
                    permissionStateVersion = 0,
                    onRequestNotificationPermission = {},
                    onRequestBatteryOptimizationExemption = {},
                    onOpenAppDetails = {},
                    onStartLocalHttpService = {},
                    onStopLocalHttpService = {},
                )
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("nav_tts").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
