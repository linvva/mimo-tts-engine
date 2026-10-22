package io.github.linvva.mimottsengine.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

class SymbolPausePreferencesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun missingPauseUsesDefault() = runBlocking {
        assertEquals(300, TtsSettings().symbolPauseMs)
        assertEquals(true, TtsSettings().symbolPauseEnabled)
        withRepository { repository, _ ->
            assertEquals(300, repository.settings.first().symbolPauseMs)
            assertEquals(true, repository.settings.first().symbolPauseEnabled)
        }
    }

    @Test fun chosenPauseSurvivesReopeningDataStore() = runBlocking {
        val file = File(temporary.newFolder(), "settings.preferences_pb")
        withRepository(file) { repository, store ->
            repository.updateSymbolPauseMs(750)
            repository.updateSymbolPauseEnabled(false)
            assertEquals(false, store.data.first()[booleanPreferencesKey("symbol_pause_enabled")])
        }
        withRepository(file) { repository, _ ->
            assertEquals(750, repository.settings.first().symbolPauseMs)
            assertEquals(false, repository.settings.first().symbolPauseEnabled)
            repository.updateSymbolPauseEnabled(true)
        }
        withRepository(file) { repository, _ ->
            assertEquals(750, repository.settings.first().symbolPauseMs)
            assertEquals(true, repository.settings.first().symbolPauseEnabled)
        }
    }

    @Test fun pauseIsClampedWhenSavingAndReading() = runBlocking {
        val key = intPreferencesKey("symbol_pause_ms")
        withRepository { repository, store ->
            for ((input, expected) in listOf(Int.MIN_VALUE to 0, 0 to 0, 10000 to 10000, Int.MAX_VALUE to 10000)) {
                repository.updateSymbolPauseMs(input)
                assertEquals(expected, store.data.first()[key])
                assertEquals(expected, repository.settings.first().symbolPauseMs)
            }
            store.edit { it[key] = -1 }
            assertEquals(0, repository.settings.first().symbolPauseMs)
            store.edit { it[key] = 10001 }
            assertEquals(10000, repository.settings.first().symbolPauseMs)
        }
    }

    @Test fun disablingPauseKeepsDurationForReenabling() = runBlocking {
        withRepository { repository, _ ->
            repository.updateSymbolPauseMs(4500)
            repository.updateSymbolPauseEnabled(false)
            assertEquals(false, repository.settings.first().symbolPauseEnabled)
            assertEquals(4500, repository.settings.first().symbolPauseMs)
            repository.updateSymbolPauseEnabled(true)
            assertEquals(true, repository.settings.first().symbolPauseEnabled)
            assertEquals(4500, repository.settings.first().symbolPauseMs)
        }
    }

    @Test fun changingPauseKeepsSynthesisSettingsSelectedTabAndAppearance() = runBlocking {
        withRepository { repository, _ ->
            repository.updateApiKey("test-credential")
            repository.updateVoice("冰糖")
            repository.updateStylePrompt("中文提示词")
            repository.updateSpeed(1.2f)
            repository.updateLastSelectedTab("http")
            repository.updateUiStyle(UiStyle.LiquidGlass)
            val before = repository.settings.first()
            repository.updateSymbolPauseMs(500)
            repository.updateSymbolPauseEnabled(false)
            assertEquals(before.copy(symbolPauseMs = 500, symbolPauseEnabled = false), repository.settings.first())
            assertEquals("http", repository.lastSelectedTab.first())
            assertEquals(UiStyle.LiquidGlass, repository.uiStyle.first())
        }
    }

    private suspend fun withRepository(
        file: File = File(temporary.newFolder(), "settings.preferences_pb"),
        block: suspend (SettingsRepository, DataStore<Preferences>) -> Unit,
    ) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
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
