package me.rerere.rikkahub.data.starwish

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.utils.JsonInstant

private val Context.starWishDataStore: DataStore<Preferences> by preferencesDataStore(name = "star_wish")

class StarWishStore(
    private val context: Context,
    scope: AppScope,
    private val json: Json = JsonInstant,
) {
    private val stateKey = stringPreferencesKey("state")

    val state: StateFlow<StarWishState> = context.starWishDataStore.data
        .map { prefs ->
            prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<StarWishState>(raw) }.getOrNull()
            } ?: StarWishState()
        }
        .catch { emit(StarWishState()) }
        .stateIn(scope, SharingStarted.Eagerly, StarWishState())

    suspend fun update(transform: (StarWishState) -> StarWishState) {
        context.starWishDataStore.edit { prefs ->
            val current = prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<StarWishState>(raw) }.getOrNull()
            } ?: StarWishState()
            prefs[stateKey] = json.encodeToString(transform(current))
        }
    }
}
