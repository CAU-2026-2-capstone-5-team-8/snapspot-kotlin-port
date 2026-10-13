package com.capstone.snapspot.data.permissions

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.capstone.snapspot.domain.PermissionStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the permission guide remembers across launches: whether it was shown once, and which steps the user
 * refused. A refused pop-up may never appear again, so those steps are sent to the settings screen next time.
 */
@Singleton
class PermissionPrefs @Inject constructor(private val store: DataStore<Preferences>) {
    private val seenKey = booleanPreferencesKey("permission_guide_seen_v1")
    private val deniedKey = stringSetPreferencesKey("permission_denied_v1")

    val guideSeen: Flow<Boolean> = store.data.map { it[seenKey] ?: false }

    val denied: Flow<Set<PermissionStep>> = store.data.map { prefs ->
        prefs[deniedKey].orEmpty().mapNotNull { name -> PermissionStep.entries.firstOrNull { it.name == name } }.toSet()
    }

    suspend fun markGuideSeen() {
        store.edit { it[seenKey] = true }
    }

    suspend fun setDenied(step: PermissionStep, denied: Boolean) {
        store.edit { prefs ->
            val now = prefs[deniedKey].orEmpty()
            prefs[deniedKey] = if (denied) now + step.name else now - step.name
        }
    }
}
