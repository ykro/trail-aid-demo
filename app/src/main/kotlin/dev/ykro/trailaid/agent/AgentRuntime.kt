package dev.ykro.trailaid.agent

import android.content.Context
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.events.Event
import com.google.adk.kt.events.ToolConfirmation
import com.google.adk.kt.litertlm.LiteRtLmModel
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.sessions.room.RoomSessionService
import com.google.adk.kt.tools.SkillToolset
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EngineConfig
import dev.ykro.trailaid.data.TrailStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import timber.log.Timber

sealed interface AgentUiEvent {
  data class ToolCall(val id: String, val name: String, val args: Map<String, Any?>, val isSkill: Boolean) : AgentUiEvent
  data class ToolResult(val id: String, val name: String, val isError: Boolean, val summary: String) : AgentUiEvent
  data class PartialText(val text: String) : AgentUiEvent
  data class FinalText(val text: String) : AgentUiEvent
  data class ConfirmationRequested(val confirmationCallId: String, val toolName: String, val args: Map<String, Any?>, val hint: String?) : AgentUiEvent
  data class Error(val message: String) : AgentUiEvent
  data class UserText(val text: String, val isSystem: Boolean) : AgentUiEvent
  data object Done : AgentUiEvent
}

/**
 * Application-scoped holder for the on-device engine and the ADK runner. The weights take seconds
 * to load, so the engine is created once per process and reused across incidents.
 */
class AgentRuntime(private val context: Context, private val store: TrailStore, val hardware: HardwareStateHolder) {
  val sessionService: SessionService = RoomSessionService.fromContext(context)
  val metronome = Metronome(hardware)
  private val deviceTools = DeviceTools(context, hardware)
  private val timerTools = TimerTools(metronome, hardware)
  val emergencyTools = EmergencyTools(context, store, hardware, deviceTools)

  private var runner: InMemoryRunner? = null
  private var model: LiteRtLmModel? = null
  @Volatile var lastLoadMs: Long = -1
    private set

  fun sessionKey(sessionId: String) = SessionKey(APP_NAME, USER_ID, sessionId)

  val isEngineLoaded: Boolean
    get() = runner != null

  fun isModelInstalled(): Boolean = ModelStore.find(context) != null

  /** Loads the weights (once) and builds the agent. Call off the main thread. */
  @Synchronized
  fun runner(): InMemoryRunner {
    runner?.let { return it }
    val file = ModelStore.find(context) ?: error("The on-device model is not installed. Download it in Prepare.")
    val started = System.currentTimeMillis()
    val m =
      LiteRtLmModel.create(
          EngineConfig(modelPath = file.absolutePath, backend = Backend.CPU(), visionBackend = Backend.CPU(), maxNumImages = 1, cacheDir = context.cacheDir.absolutePath),
          name = file.name,
        )
        .also { it.engine.initialize(); model = it }
    lastLoadMs = System.currentTimeMillis() - started
    Timber.i("Engine loaded in %d ms", lastLoadMs)
    val agent = FirstAidAgent.create(context, m, deviceTools, timerTools, emergencyTools)
    return InMemoryRunner(agent = agent, appName = APP_NAME, sessionService = sessionService).also { runner = it }
  }

  @Synchronized
  fun release() {
    metronome.stop()
    runCatching { model?.close() }
    model = null
    runner = null
  }

  suspend fun ensureSession(sessionId: String) {
    if (sessionService.getSession(sessionKey(sessionId)) == null) sessionService.createSession(sessionKey(sessionId))
  }

  suspend fun sessionEvents(sessionId: String): List<Event> = sessionService.getSession(sessionKey(sessionId))?.events.orEmpty()

  fun sendText(sessionId: String, text: String): Flow<AgentUiEvent> = run(sessionId, Content(role = Role.USER, parts = listOf(Part(text = text))))

  /** Stretch: the injury photo goes to the same agent as inline bytes with a short instruction. */
  fun sendPhoto(sessionId: String, jpeg: ByteArray): Flow<AgentUiEvent> =
    run(sessionId, Content(role = Role.USER, parts = listOf(Part(inlineData = Blob(mimeType = "image/jpeg", data = jpeg)), Part(text = PHOTO_PROMPT))))

  fun sendConfirmation(sessionId: String, confirmationCallId: String, confirmed: Boolean): Flow<AgentUiEvent> =
    run(
      sessionId,
      Content(
        role = Role.USER,
        parts =
          listOf(
            Part(
              functionResponse =
                FunctionResponse(
                  name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                  id = confirmationCallId,
                  response = mapOf(ToolConfirmation.CONFIRMED_KEY to confirmed),
                )
            )
          ),
      ),
    )

  private fun run(sessionId: String, message: Content): Flow<AgentUiEvent> = flow {
    val partial = StringBuilder()
    var finalText: String? = null
    try {
      runner()
        // Non-streaming on purpose: LiteRT-LM 0.13.1's streaming parser rejects the model's tool-call
        // blocks ("Failed to parse tool calls from code block"); the same calls parse fine non-streaming.
        .runAsync(userId = USER_ID, sessionId = sessionId, newMessage = message, runConfig = RunConfig(streamingMode = StreamingMode.NONE, maxLlmCalls = 12))
        .collect { event ->
          event.errorMessage?.let { emit(AgentUiEvent.Error(it)) }
          if (!event.partial) {
            mapFunctionParts(event).forEach { emit(it) }
            if (event.content?.parts.orEmpty().any { p -> p.functionCall?.let { isCprSkillLoad(it.name, it.args) } == true }) autoStartMetronome().forEach { emit(it) }
          }
          if (event.author == FirstAidAgent.NAME) {
            val text = modelText(event)
            if (event.partial) {
              if (text.isNotEmpty()) { partial.append(text); emit(AgentUiEvent.PartialText(partial.toString())) }
            } else if (text.isNotBlank()) {
              finalText = text
              partial.setLength(0)
            }
          }
        }
      finalText?.let { emit(AgentUiEvent.FinalText(it.trim())) }
    } catch (e: Exception) {
      Timber.e(e, "Turn failed")
      val recovered = recoverMisformattedCall(e.message.orEmpty())
      if (recovered != null) {
        // The 2B model wrote a tool call the LiteRT-LM grammar could not parse (plain quotes instead
        // of the <|"|> string markers). Run the tool ourselves and hand the result back as text.
        val (name, args) = recovered
        val id = "recovered-" + System.currentTimeMillis()
        emit(AgentUiEvent.ToolCall(id, name, args, name in SKILL_TOOLS))
        if (isCprSkillLoad(name, args)) autoStartMetronome().forEach { emit(it) }
        val result = runCatching { executeRecovered(name, args) }.getOrNull()
        if (result == null) {
          emit(AgentUiEvent.ToolResult(id, name, isError = true, summary = "could not recover"))
          emit(AgentUiEvent.Error("The model wrote a tool call I could not run ($name). Ask it to continue."))
        } else {
          emit(AgentUiEvent.ToolResult(id, name, isError = false, summary = "recovered"))
          val followUp = "$SYSTEM_PREFIX Result of $name(${args.entries.joinToString { "${it.key}=${it.value}" }}):\n$result\nContinue with the next step."
          emitAll(run(sessionId, Content(role = Role.USER, parts = listOf(Part(text = followUp)))))
          return@flow
        }
      } else {
        emit(AgentUiEvent.Error(e.message?.lineSequence()?.firstOrNull() ?: e::class.simpleName.orEmpty()))
      }
    }
    Timber.i("Turn finished")
    emit(AgentUiEvent.Done)
  }

  private fun recoverMisformattedCall(message: String) = parseMisformattedCall(message)

  /**
   * Safety net: a 2B model does not reliably follow "start the metronome for CPR", so the app starts
   * it deterministically the moment the CPR protocol is loaded. The chip is labelled so the audience
   * sees it was the app, not the model. Not applied on replay (a resumed incident should stay quiet).
   */
  private fun autoStartMetronome(): List<AgentUiEvent> {
    if (metronome.isRunning) return emptyList()
    val bpm = timerTools.startCprMetronome()["bpm"]
    val id = "app-metronome-" + System.currentTimeMillis()
    Timber.i("Metronome auto-started by the app on load_skill(cpr-adult)")
    return listOf(
      AgentUiEvent.ToolCall(id, "start_cpr_metronome", mapOf("by" to "app"), isSkill = false),
      AgentUiEvent.ToolResult(id, "start_cpr_metronome", isError = false, summary = "$bpm bpm · app"),
    )
  }

  private suspend fun executeRecovered(name: String, args: Map<String, String>): String? =
    when (name) {
      SkillToolset.TOOL_NAME_LOAD_SKILL -> {
        val skill = args["skill_name"] ?: return null
        hardware.update { if (skill in protocolsUsed) this else copy(protocolsUsed = protocolsUsed + skill) }
        readAsset("skills/$skill/SKILL.md")
      }
      SkillToolset.TOOL_NAME_LOAD_SKILL_RESOURCE -> {
        val skill = args["skill_name"] ?: return null
        val path = args["path"] ?: return null
        readAsset("skills/$skill/${path.trimStart('/')}")
      }
      SkillToolset.TOOL_NAME_LIST_SKILLS -> context.assets.list("skills")?.joinToString("\n") ?: return null
      "start_named_timer" -> timerTools.startNamedTimer(args["name"] ?: "timer").toString()
      "start_cpr_metronome" -> timerTools.startCprMetronome().toString()
      "stop_cpr_metronome" -> timerTools.stopCprMetronome().toString()
      "get_device_status" -> deviceTools.getDeviceStatus().toString()
      "get_location" -> deviceTools.getLocation().toString()
      else -> null // call/SMS need the confirmation round trip; the user can ask the agent to retry.
    }

  private fun readAsset(path: String): String? = runCatching { context.assets.open(path).bufferedReader().readText() }.getOrNull()

  fun replay(events: List<Event>): List<AgentUiEvent> {
    val out = mutableListOf<AgentUiEvent>()
    for (event in events) {
      if (event.partial) continue
      if (event.author == "user") {
        val text = event.content?.parts.orEmpty().mapNotNull { it.text }.joinToString("").trim()
        val hasImage = event.content?.parts.orEmpty().any { it.inlineData != null }
        if (hasImage) out += AgentUiEvent.UserText("Photo sent to the agent", isSystem = true)
        else if (text.isNotEmpty()) out += AgentUiEvent.UserText(text.removePrefix(SYSTEM_PREFIX).trim(), isSystem = text.startsWith(SYSTEM_PREFIX))
      }
      out += mapFunctionParts(event)
      if (event.author == FirstAidAgent.NAME) modelText(event).takeIf { it.isNotBlank() }?.let { out += AgentUiEvent.FinalText(it.trim()) }
    }
    return out
  }

  private fun modelText(event: Event): String =
    event.content?.parts.orEmpty().filter { it.text != null && it.thought != true }.joinToString("") { it.text.orEmpty() }

  private fun mapFunctionParts(event: Event): List<AgentUiEvent> {
    val out = mutableListOf<AgentUiEvent>()
    for (part in event.content?.parts.orEmpty()) {
      part.functionCall?.let { call ->
        if (call.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME) {
          val original = call.args["originalFunctionCall"] as? Map<*, *>
          val confirmation = call.args["toolConfirmation"] as? Map<*, *>
          out +=
            AgentUiEvent.ConfirmationRequested(
              confirmationCallId = call.id.orEmpty(),
              toolName = original?.get("name") as? String ?: "tool",
              args = (original?.get("args") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty(),
              hint = confirmation?.get("hint") as? String,
            )
        } else {
          if (call.name == SkillToolset.TOOL_NAME_LOAD_SKILL) (call.args["skill_name"] as? String)?.let { s -> hardware.update { if (s in protocolsUsed) this else copy(protocolsUsed = protocolsUsed + s) } }
          out += AgentUiEvent.ToolCall(call.id ?: call.name, call.name, call.args, call.name in SKILL_TOOLS)
        }
      }
      part.functionResponse?.let { resp ->
        if (resp.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME) {
          out += AgentUiEvent.ToolResult(resp.id ?: resp.name, resp.name, isError = false, summary = "answered")
        } else {
          out += AgentUiEvent.ToolResult(resp.id ?: resp.name, resp.name, resp.response.containsKey("error"), summarize(resp.name, unwrap(resp.response)))
        }
      }
    }
    return out
  }

  /** KSP-generated tools return `{"result": ...}`; skill tools and errors return their map directly. */
  private fun unwrap(response: Map<String, Any?>): Map<String, Any?> =
    (response["result"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: response

  private fun summarize(name: String, response: Map<String, Any?>): String =
    when (name) {
      SkillToolset.TOOL_NAME_LOAD_SKILL -> "protocol loaded"
      SkillToolset.TOOL_NAME_LOAD_SKILL_RESOURCE -> "steps loaded"
      SkillToolset.TOOL_NAME_LIST_SKILLS -> "7 protocols"
      "get_location" -> (response["error"] ?: "±${response["accuracyMeters"]} m").toString()
      "get_device_status" -> "${response["batteryPercent"]} %"
      "start_cpr_metronome" -> "${response["bpm"]} bpm"
      "start_named_timer" -> "${response["name"]} @ ${response["startedAt"]}"
      "call_emergency_contact" -> (response["error"] ?: "calling").toString()
      "send_location_sms" -> (response["error"] ?: "sent").toString()
      else -> if (response.containsKey("error")) response["error"].toString() else "ok"
    }

  companion object {
    const val APP_NAME = "TrailAid"
    const val USER_ID = "hiker"
    const val MODEL_LABEL = "Gemma 4 E2B · on device"
    const val SYSTEM_PREFIX = "[app]"
    const val PHOTO_PROMPT = "Look at this injury photo and pick the protocol that matches what you see."
    val SKILL_TOOLS = setOf(SkillToolset.TOOL_NAME_LIST_SKILLS, SkillToolset.TOOL_NAME_LOAD_SKILL, SkillToolset.TOOL_NAME_LOAD_SKILL_RESOURCE)
    const val KICKOFF = "$SYSTEM_PREFIX Emergency started. Ask the hiker what happened, in one short sentence."
  }
}

/** True when a tool call loads the adult CPR protocol (skill name may come quoted from the recovery path). */
internal fun isCprSkillLoad(name: String, args: Map<String, Any?>): Boolean =
  name == SkillToolset.TOOL_NAME_LOAD_SKILL && (args["skill_name"] as? String)?.trim('"', '\'', ' ') == "cpr-adult"

/** Extracts `call:name{key:value,...}` from LiteRT-LM's parse error. Tolerates ", ', <|"|> and bare values. */
internal fun parseMisformattedCall(message: String): Pair<String, Map<String, String>>? {
  val call = Regex("call:([A-Za-z_][A-Za-z0-9_]*)\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL).find(message) ?: return null
  val name = call.groupValues[1]
  val body = call.groupValues[2]
  val args = mutableMapOf<String, String>()
  Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*[:=]\\s*(<\\|\"\\|>(.*?)<\\|\"\\|>|\"(.*?)\"|'(.*?)'|([^,}]+))", RegexOption.DOT_MATCHES_ALL).findAll(body).forEach { m ->
    val value = m.groupValues[3].ifEmpty { m.groupValues[4] }.ifEmpty { m.groupValues[5] }.ifEmpty { m.groupValues[6] }.trim()
    args[m.groupValues[1]] = value
  }
  return name to args
}
