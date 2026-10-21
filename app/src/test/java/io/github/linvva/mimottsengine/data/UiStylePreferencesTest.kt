package io.github.linvva.mimottsengine.data

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UiStylePreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun missingAndUnknownStyleUseClassicWithoutOverwritingOtherPreferences() = runBlocking {
        withRepository { repository, store ->
            assertEquals(UiStyle.Classic, repository.uiStyle.first())
            repository.updateApiKey("test-credential")
            repository.updateLastSelectedTab("http")
            store.edit { it[stringPreferencesKey("ui_style")] = "future-style" }
            assertEquals(UiStyle.Classic, repository.uiStyle.first())
            assertEquals("test-credential", repository.settings.first().apiKey)
            assertEquals("http", repository.lastSelectedTab.first())
        }
    }

    @Test fun chosenStyleSurvivesReopeningDataStore() = runBlocking {
        val file = File(temporary.newFolder(), "settings.preferences_pb")
        withRepository(file) { repository, _ -> repository.updateUiStyle(UiStyle.LiquidGlass) }
        withRepository(file) { repository, _ ->
            assertEquals(UiStyle.LiquidGlass, repository.uiStyle.first())
            repository.updateUiStyle(UiStyle.Classic)
        }
        withRepository(file) { repository, _ -> assertEquals(UiStyle.Classic, repository.uiStyle.first()) }
    }

    @Test fun changingAppearanceKeepsSynthesisSettingsAndSelectedTab() = runBlocking {
        withRepository { repository, _ ->
            repository.updateApiKey("test-credential")
            repository.updateStylePrompt("中文提示词")
            repository.updateSpeed(1.2f)
            repository.updateLastSelectedTab("settings")
            val before = repository.settings.first()
            repository.updateUiStyle(UiStyle.LiquidGlass)
            assertEquals(before, repository.settings.first())
            assertEquals("settings", repository.lastSelectedTab.first())
        }
    }

    private suspend fun withRepository(
        file: File = File(temporary.newFolder(), "settings.preferences_pb"),
        block: suspend (SettingsRepository, DataStore<Preferences>) -> Unit,
    ) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        // Okio uses an atomic replacement that also works on Windows JVM tests.
        val store = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = { file.absolutePath.toPath() }),
            scope = scope,
        )
        try { block(SettingsRepository(store), store) } finally {
            scope.cancel()
            job.join()
        }
    }
}
