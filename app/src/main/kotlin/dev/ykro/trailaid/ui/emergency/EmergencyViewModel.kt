package dev.ykro.trailaid.ui.emergency

import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import com.google.adk.kt.types.FunctionCall
import androidx.lifecycle.viewModelScope
import dev.ykro.trailaid.agent.AgentRuntime
import dev.ykro.trailaid.agent.AgentUiEvent
import dev.ykro.trailaid.agent.HardwareState
import dev.ykro.trailaid.data.IncidentDatabase
import dev.ykro.trailaid.data.IncidentEntity
import dev.ykro.trailaid.data.TrailStore
import dev.ykro.trailaid.ui.components.ToolChip
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

sealed interface TimelineItem {
  data class Tools(val chips: List<ToolChip>) : TimelineItem
  data class Agent(val text: String) : TimelineItem
  data class User(val text: String) : TimelineItem
  data class System(val text: String) : TimelineItem
  data class Failure(val text: String) : TimelineItem
}

data class PendingConfirmation(val callId: String, val toolName: String, val args: Map<String, Any?>, val hint: String?)

data class EmergencyUiState(
  val loadingEngine: Boolean = true,
  val loadMs: Long = -1,
  val items: List<TimelineItem> = emptyList(),
  val streamingText: String? = null,
  val busy: Boolean = true,
  val confirmation: PendingConfirmation? = null,
  val ttsEnabled: Boolean = true,
  val resumed: Boolean = false,
  val fatal: String? = null,
  val canRetry: Boolean = false,
  val hardware: HardwareState = HardwareState(),
  val closed: Boolean = false,
)

/**
 * One incident = one Room session ("incident-<timestamp>"). Everything the agent says is spoken
 * sentence by sentence as it streams, so the hiker does not have to look at the screen.
 */
class EmergencyViewModel(
  context: Context,
  private val sessionId: String,
  private val runtime: AgentRuntime,
  private val store: TrailStore,
  private val db: IncidentDatabase,
) : ViewModel() {
  private val _state = MutableStateFlow(EmergencyUiState())
  val state: StateFlow<EmergencyUiState> = _state
  private var tts: TextToSpeech? = null
  @Volatile private var ttsReady = false
  /** TTS binder calls can block while the engine rebinds (it gets killed under memory pressure); keep them off the main thread. */
  private val ttsExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "tts").apply { isDaemon = true } }
  private var spokenChars = 0
  private val startedAt = System.currentTimeMillis()

  init {
    ttsExecutor.execute {
      tts = TextToSpeech(context.applicationContext) { status -> ttsReady = status == TextToSpeech.SUCCESS; ttsExecutor.execute { runCatching { tts?.language = Locale.US } } }
    }
    viewModelScope.launch { runtime.hardware.state.collect { hw -> _state.update { it.copy(hardware = hw) } } }
    viewModelScope.launch { start() }
  }

  private suspend fun start() {
    try {
      withContext(Dispatchers.IO) { runtime.runner() }
    } catch (e: Exception) {
      Timber.e(e, "Engine load failed")
      _state.update { it.copy(loadingEngine = false, busy = false, fatal = e.message ?: "Could not load the on-device model.") }
      return
    }
    _state.update { it.copy(loadingEngine = false, loadMs = runtime.lastLoadMs) }
    runtime.ensureSession(sessionId)
    val events = runtime.sessionEvents(sessionId)
    if (events.isEmpty()) {
      send(AgentRuntime.KICKOFF, showAsUser = false)
    } else {
      val replay = runtime.replay(events)
      replay.forEach { apply(it, speak = false) }
      _state.update { it.copy(busy = false, resumed = true, streamingText = null) }
      if (_state.value.confirmation == null && replay.lastOrNull() !is AgentUiEvent.FinalText) {
        send("${AgentRuntime.SYSTEM_PREFIX} The app was reopened. Continue from the last step; do not ask what happened again.", showAsUser = false)
      }
    }
  }

  fun say(text: String) {
    if (text.isBlank() || _state.value.busy) return
    viewModelScope.launch { send(text.trim(), showAsUser = true) }
  }

  fun sendPhoto(bitmap: Bitmap) {
    if (_state.value.busy) return
    viewModelScope.launch {
      val bytes = withContext(Dispatchers.Default) {
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > 640) Bitmap.createScaledBitmap(bitmap, bitmap.width * 640 / longest, bitmap.height * 640 / longest, true) else bitmap
        ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 80, out); out.toByteArray() }
      }
      _state.update { it.copy(busy = true, canRetry = false, items = it.items + TimelineItem.System("Photo sent to the agent (on device)")) }
      runtime.sendPhoto(sessionId, bytes).flowOn(Dispatchers.IO).collect { apply(it, speak = true) }
    }
  }

  fun continueAfterError() {
    if (_state.value.busy) return
    viewModelScope.launch { send("${AgentRuntime.SYSTEM_PREFIX} The previous step failed. Use only the listed tools and continue.", showAsUser = false) }
  }

  fun resolveConfirmation(confirmed: Boolean) {
    val pending = _state.value.confirmation ?: return
    _state.update { it.copy(confirmation = null, busy = true, items = it.items + TimelineItem.System(if (confirmed) "You approved ${pending.toolName}" else "You declined ${pending.toolName}")) }
    viewModelScope.launch { runtime.sendConfirmation(sessionId, pending.callId, confirmed).flowOn(Dispatchers.IO).collect { apply(it, speak = true) } }
  }

  fun toggleTts() {
    _state.update { it.copy(ttsEnabled = !it.ttsEnabled) }
    if (!_state.value.ttsEnabled) ttsExecutor.execute { runCatching { tts?.stop() } }
  }

  fun previewSms(): String = runtime.emergencyTools.previewSms(((_state.value.confirmation?.args?.get("message")) as? String).orEmpty())

  /**
   * Closes the incident: stops hardware, writes the journal entry, clears the active session pointer.
   * The summary is rebuilt from the ADK session events (the source of truth), not from in-memory
   * state, so it survives the process restarts an incident typically goes through.
   */
  fun closeIncident(onDone: () -> Unit) {
    viewModelScope.launch {
      runtime.metronome.stop()
      val events = runCatching { runtime.replay(runtime.sessionEvents(sessionId)) }.getOrDefault(emptyList())
      val calls = events.filterIsInstance<AgentUiEvent.ToolCall>()
      val okResults = events.filterIsInstance<AgentUiEvent.ToolResult>().filter { !it.isError }
      val protocols = calls.filter { it.name == "load_skill" }.mapNotNull { it.args["skill_name"] as? String }.distinct()
      val timers = okResults.filter { it.name == "start_named_timer" }.map { it.summary }
      val location = okResults.lastOrNull { it.name == "get_location" }?.let { runtime.hardware.state.value.location?.spoken ?: "GPS fix ${it.summary}" }
      val transcript = events.filterIsInstance<AgentUiEvent.FinalText>().joinToString("\n") { it.text }
      db.incidents()
        .insert(
          IncidentEntity(
            sessionId = sessionId,
            startedAtEpochMs = startedAt,
            closedAtEpochMs = System.currentTimeMillis(),
            protocols = protocols.joinToString(","),
            timers = timers.joinToString("\n"),
            location = location,
            called = okResults.any { it.name == "call_emergency_contact" },
            smsSent = okResults.any { it.name == "send_location_sms" },
            summary = transcript.take(2000),
          )
        )
      store.setActiveIncident(null)
      runtime.hardware.reset()
      _state.update { it.copy(closed = true) }
      onDone()
    }
  }

  private suspend fun send(text: String, showAsUser: Boolean) {
    _state.update { it.copy(busy = true, canRetry = false, items = it.items + (if (showAsUser) TimelineItem.User(text) else TimelineItem.System(describe(text)))) }
    runtime.sendText(sessionId, text).flowOn(Dispatchers.IO).collect { apply(it, speak = true) }
  }

  private fun describe(text: String) =
    when {
      text.contains("Emergency started") -> "Emergency started"
      text.contains("reopened") -> "App reopened, resuming"
      text.contains("previous step failed") -> "Asked the agent to continue"
      else -> text.removePrefix(AgentRuntime.SYSTEM_PREFIX).trim()
    }

  private fun apply(event: AgentUiEvent, speak: Boolean) {
    when (event) {
      is AgentUiEvent.ToolCall -> addChip(ToolChip(event.id, event.name, event.isSkill))
      is AgentUiEvent.ToolResult -> {
        completeChip(event)
        if (event.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME) _state.update { it.copy(confirmation = null) }
      }
      is AgentUiEvent.PartialText -> {
        _state.update { it.copy(streamingText = event.text) }
        if (speak) speakNewSentences(event.text)
      }
      is AgentUiEvent.FinalText -> {
        if (speak) speakSentences(event.text)
        spokenChars = 0
        _state.update { it.copy(items = it.items + TimelineItem.Agent(event.text), streamingText = null) }
      }
      is AgentUiEvent.ConfirmationRequested -> {
        _state.update { it.copy(confirmation = PendingConfirmation(event.confirmationCallId, event.toolName, event.args, event.hint), busy = false, streamingText = null) }
        if (speak) speak(if (event.toolName == "call_emergency_contact") "Do you want me to call your emergency contact?" else "Do you want me to send your location by SMS?")
      }
      is AgentUiEvent.Error -> _state.update { it.copy(items = it.items + TimelineItem.Failure(event.message), streamingText = null, canRetry = true) }
      is AgentUiEvent.UserText -> _state.update { it.copy(items = it.items + if (event.isSystem) TimelineItem.System(describe(event.text)) else TimelineItem.User(event.text)) }
      AgentUiEvent.Done -> _state.update { it.copy(busy = false, streamingText = null) }
    }
  }

  /** Speaks each finished sentence as soon as it streams in. */
  private fun speakNewSentences(text: String) {
    val unspoken = text.drop(spokenChars)
    val end = unspoken.indexOfLast { it == '.' || it == '!' || it == '?' }
    if (end < 0) return
    val sentence = unspoken.substring(0, end + 1).trim()
    spokenChars += end + 1
    speak(sentence)
  }

  /** Queues the final answer one sentence at a time so the first sentence starts immediately. */
  private fun speakSentences(text: String) {
    val rest = text.drop(spokenChars).trim()
    if (rest.isEmpty()) return
    Regex("[^.!?]+[.!?]?").findAll(rest).map { it.value.trim() }.filter { it.isNotEmpty() }.forEach { speak(it) }
  }

  private fun speak(text: String) {
    if (!_state.value.ttsEnabled || !ttsReady) return
    ttsExecutor.execute { runCatching { tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "tts-${System.nanoTime()}") } }
  }

  private fun addChip(chip: ToolChip) =
    _state.update { s ->
      val last = s.items.lastOrNull()
      s.copy(items = if (last is TimelineItem.Tools) s.items.dropLast(1) + last.copy(chips = last.chips + chip) else s.items + TimelineItem.Tools(listOf(chip)))
    }

  private fun completeChip(result: AgentUiEvent.ToolResult) =
    _state.update { s ->
      s.copy(items = s.items.map { item -> if (item !is TimelineItem.Tools) item else item.copy(chips = item.chips.map { c -> if (c.id == result.id || (c.name == result.name && !c.done)) c.copy(done = true, isError = result.isError, summary = result.summary) else c }) })
    }

  override fun onCleared() {
    ttsExecutor.execute { runCatching { tts?.stop(); tts?.shutdown() } }
    ttsExecutor.shutdown()
    super.onCleared()
  }
}
