package com.capstone.snapspot.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.capstone.snapspot.domain.model.BranchRef
import com.capstone.snapspot.domain.model.Coords
import com.capstone.snapspot.domain.model.WindowState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class WindowStateDto(
    val at: Long,
    val here: CoordsDto? = null,
    val slots: Int,
    val byGroup: Map<String, Int> = emptyMap(),
    val branches: Map<String, BranchRefDto> = emptyMap(),
    val requests: Int = 0,
)

@Serializable
internal data class CoordsDto(val latitude: Double, val longitude: Double)

@Serializable
internal data class BranchRefDto(val name: String, val brandKey: String, val latitude: Double, val longitude: Double)

internal fun WindowState.toJson(): String = Json.encodeToString(
    WindowStateDto(
        at = at,
        here = here?.let { CoordsDto(it.latitude, it.longitude) },
        slots = slots,
        byGroup = byGroup,
        branches = branches.mapValues { (_, b) -> BranchRefDto(b.name, b.brandKey, b.latitude, b.longitude) },
        requests = requests,
    ),
)

/** Unreadable content gives null, as if no plan had been saved yet. */
internal fun windowStateFromJson(raw: String?): WindowState? {
    if (raw.isNullOrEmpty()) return null
    return try {
        val d = Json.decodeFromString<WindowStateDto>(raw)
        WindowState(
            at = d.at,
            here = d.here?.let { Coords(it.latitude, it.longitude) },
            slots = d.slots,
            byGroup = d.byGroup,
            branches = d.branches.mapValues { (_, b) -> BranchRef(b.name, b.brandKey, b.latitude, b.longitude) },
            requests = d.requests,
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * The plan currently registered with the OS, kept so a geofence alert woken in the background can
 * name the branch, and the status screen can show the slot split.
 */
@Singleton
class WindowRepository @Inject constructor(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("window_v1")

    fun observe(): Flow<WindowState?> = store.data.map { windowStateFromJson(it[key]) }

    suspend fun load(): WindowState? = observe().first()

    suspend fun save(state: WindowState) {
        store.edit { it[key] = state.toJson() }
    }
}
