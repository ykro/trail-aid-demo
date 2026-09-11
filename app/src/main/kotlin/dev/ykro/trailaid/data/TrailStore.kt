package dev.ykro.trailaid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "trail")

data class EmergencyContact(val name: String, val phone: String, val baseMessage: String) {
  val isConfigured: Boolean
    get() = phone.isNotBlank()
}

/** Emergency contact, base SMS text, the active incident session and the disclaimer acknowledgement. */
class TrailStore(private val context: Context) {
  private val name = stringPreferencesKey("contact_name")
  private val phone = stringPreferencesKey("contact_phone")
  private val message = stringPreferencesKey("base_message")
  private val incident = stringPreferencesKey("active_incident")
  private val accepted = booleanPreferencesKey("disclaimer_accepted")

  val contact: Flow<EmergencyContact> =
    context.dataStore.data.map { EmergencyContact(it[name].orEmpty(), it[phone].orEmpty(), it[message] ?: DEFAULT_MESSAGE) }
  val activeIncident: Flow<String?> = context.dataStore.data.map { it[incident] }
  val disclaimerAccepted: Flow<Boolean> = context.dataStore.data.map { it[accepted] ?: false }

  suspend fun contactNow(): EmergencyContact = contact.first()
  suspend fun activeIncidentNow(): String? = activeIncident.first()

  suspend fun saveContact(n: String, p: String, m: String) = context.dataStore.edit { it[name] = n; it[phone] = p; it[message] = m }
  suspend fun setActiveIncident(id: String?) = context.dataStore.edit { if (id == null) it.remove(incident) else it[incident] = id }
  suspend fun acceptDisclaimer() = context.dataStore.edit { it[accepted] = true }

  companion object {
    const val DEFAULT_MESSAGE = "Trail Aid: I need help on the trail."
  }
}
