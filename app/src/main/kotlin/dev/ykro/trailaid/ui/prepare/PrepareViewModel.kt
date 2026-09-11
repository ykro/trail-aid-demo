package dev.ykro.trailaid.ui.prepare

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ykro.trailaid.agent.AgentRuntime
import dev.ykro.trailaid.agent.ModelStore
import dev.ykro.trailaid.data.EmergencyContact
import dev.ykro.trailaid.data.TrailStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class PrepareUiState(
  val modelPresent: Boolean = false,
  val modelFileName: String? = null,
  val downloading: Boolean = false,
  val progress: Float = 0f,
  val error: String? = null,
  val pushCommand: String = "",
  val totalRamGb: Double = 0.0,
  val meetsRequirements: Boolean = true,
  val contact: EmergencyContact = EmergencyContact("", "", TrailStore.DEFAULT_MESSAGE),
  val testing: Boolean = false,
  val testResult: String? = null,
  val disclaimerAccepted: Boolean = false,
  val activeIncident: String? = null,
)

/** "Before you leave": model download, contact, self-test. Everything here needs Wi-Fi; nothing in Emergency does. */
class PrepareViewModel(private val context: Context, private val store: TrailStore, private val runtime: AgentRuntime) : ViewModel() {
  private val _state = MutableStateFlow(PrepareUiState(pushCommand = ModelStore.pushCommand(context)))
  val state: StateFlow<PrepareUiState> = _state
  private var downloadJob: Job? = null

  init {
    val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
    val gb = mem.totalMem / 1024.0 / 1024.0 / 1024.0
    _state.update { it.copy(totalRamGb = gb, meetsRequirements = gb >= MIN_RAM_GB) }
    refreshModel()
    viewModelScope.launch { store.contact.collect { c -> _state.update { it.copy(contact = c) } } }
    viewModelScope.launch { store.disclaimerAccepted.collect { a -> _state.update { it.copy(disclaimerAccepted = a) } } }
    viewModelScope.launch { store.activeIncident.collect { id -> _state.update { it.copy(activeIncident = id) } } }
  }

  fun refreshModel() {
    val file = ModelStore.find(context)
    _state.update { it.copy(modelPresent = file != null, modelFileName = file?.name) }
  }

  fun saveContact(name: String, phone: String, message: String) = viewModelScope.launch { store.saveContact(name.trim(), phone.trim(), message.trim().ifBlank { TrailStore.DEFAULT_MESSAGE }) }

  fun acceptDisclaimer() = viewModelScope.launch { store.acceptDisclaimer() }

  fun download() {
    if (downloadJob?.isActive == true) return
    downloadJob =
      viewModelScope.launch {
        _state.update { it.copy(downloading = true, progress = 0f, error = null) }
        try {
          ModelStore.download(context).collect { p -> _state.update { it.copy(progress = p) } }
        } catch (e: Exception) {
          Timber.e(e, "Model download failed")
          _state.update { it.copy(error = e.message ?: "Download failed") }
        }
        _state.update { it.copy(downloading = false) }
        refreshModel()
      }
  }

  fun cancelDownload() {
    downloadJob?.cancel()
    _state.update { it.copy(downloading = false) }
  }

  fun deleteModel() {
    runtime.release()
    ModelStore.delete(context)
    refreshModel()
  }

  /** Loads the engine and asks one trivial question, reporting load time and first-answer time. */
  fun testModel() {
    if (_state.value.testing) return
    viewModelScope.launch {
      _state.update { it.copy(testing = true, testResult = null) }
      val result =
        runCatching {
          withContext(Dispatchers.IO) {
            runtime.runner()
            val sessionId = "selftest-" + System.currentTimeMillis()
            runtime.ensureSession(sessionId)
            val started = System.currentTimeMillis()
            var answer = ""
            runtime.sendText(sessionId, "${AgentRuntime.SYSTEM_PREFIX} Self-test. Reply with the single word READY.").collect { ev ->
              if (ev is dev.ykro.trailaid.agent.AgentUiEvent.FinalText) answer = ev.text
            }
            val elapsed = System.currentTimeMillis() - started
            runCatching { runtime.sessionService.deleteSession(runtime.sessionKey(sessionId)) }
            "Model loaded in ${runtime.lastLoadMs / 1000.0} s · answered \"${answer.take(40)}\" in ${elapsed / 1000.0} s"
          }
        }
          .getOrElse { "Test failed: ${it.message}" }
      _state.update { it.copy(testing = false, testResult = result) }
    }
  }

  companion object {
    const val MIN_RAM_GB = 6.0
  }
}
