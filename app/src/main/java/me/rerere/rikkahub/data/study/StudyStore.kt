package me.rerere.rikkahub.data.study

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.utils.JsonInstant
import java.time.LocalDate
import kotlin.random.Random

private val Context.studyDataStore: DataStore<Preferences> by preferencesDataStore(name = "study_reward")

class StudyStore(
    private val context: Context,
    scope: CoroutineScope,
    private val json: Json = JsonInstant,
) {
    private val stateKey = stringPreferencesKey("state")

    val state: StateFlow<StudyState> = context.studyDataStore.data
        .map { prefs ->
            prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<StudyState>(raw) }.getOrNull()
            } ?: StudyState(today = LocalDate.now().toString())
        }
        .catch { emit(StudyState(today = LocalDate.now().toString())) }
        .map { StudyRules.refreshShopIfNeeded(it.ensureToday(), LocalDate.now(), Random.Default) }
        .stateIn(scope, SharingStarted.Eagerly, StudyState(today = LocalDate.now().toString()))

    suspend fun update(transform: (StudyState) -> StudyState) {
        context.studyDataStore.edit { prefs ->
            val current = prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<StudyState>(raw) }.getOrNull()
            } ?: StudyState(today = LocalDate.now().toString())
            prefs[stateKey] = json.encodeToString(transform(current.ensureToday()))
        }
    }

    suspend fun set(state: StudyState) {
        context.studyDataStore.edit { prefs ->
            prefs[stateKey] = json.encodeToString(state)
        }
    }
}

private fun StudyState.ensureToday(date: LocalDate = LocalDate.now()): StudyState {
    val dateText = date.toString()
    if (today == dateText) return this
    val hadStudy = tasks.any { it.done } || stats.totalPomodoros > 0
    val nextInactive = if (hadStudy) 0 else inactiveStudyDays + 1
    return copy(
        today = dateText,
        tasks = emptyList(),
        inactiveStudyDays = nextInactive,
        superMomentAvailable = false,
        purchasedShopItemIds = emptySet(),
    )
}
