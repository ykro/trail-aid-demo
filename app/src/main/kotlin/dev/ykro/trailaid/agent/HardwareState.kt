package dev.ykro.trailaid.agent

import android.media.AudioManager
import android.media.ToneGenerator
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NamedTimer(val name: String, val startedAt: LocalTime) {
  val label: String
    get() = "$name @ ${startedAt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

data class LocationFix(val latitude: Double, val longitude: Double, val accuracyM: Float, val altitudeM: Double?, val spoken: String, val isLastKnown: Boolean)

/** What the bottom status panel shows. Tools mutate it, so the audience sees the agent act. */
data class HardwareState(
  val metronomeBpm: Int? = null,
  val timers: List<NamedTimer> = emptyList(),
  val location: LocationFix? = null,
  val batteryPercent: Int? = null,
  val called: Boolean = false,
  val smsSent: Boolean = false,
  val protocolsUsed: List<String> = emptyList(),
) {
  val summaryLines: List<String>
    get() =
      buildList {
        if (protocolsUsed.isNotEmpty()) add("Protocols: ${protocolsUsed.joinToString()}")
        timers.forEach { add("Timer ${it.label}") }
        location?.let { add("Location ${it.spoken}") }
        if (called) add("Emergency contact called")
        if (smsSent) add("Location SMS sent")
      }
}

class HardwareStateHolder {
  private val _state = MutableStateFlow(HardwareState())
  val state: StateFlow<HardwareState> = _state
  fun update(transform: HardwareState.() -> HardwareState) = _state.update { it.transform() }
  fun reset() = _state.update { HardwareState() }
}

/** CPR metronome: an audible click at the requested rate (100–120 bpm). Audio only; vibration is not observable on the emulator. */
class Metronome(private val holder: HardwareStateHolder, private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
  private var job: Job? = null
  private var tone: ToneGenerator? = null

  @Synchronized
  fun start(bpm: Int): Int {
    val rate = bpm.coerceIn(100, 120)
    stop()
    tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    val interval = 60_000L / rate
    job =
      scope.launch {
        while (isActive) {
          runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60) }
          delay(interval)
        }
      }
    holder.update { copy(metronomeBpm = rate) }
    return rate
  }

  @Synchronized
  fun stop() {
    job?.cancel()
    job = null
    tone?.release()
    tone = null
    holder.update { copy(metronomeBpm = null) }
  }

  val isRunning: Boolean
    get() = job?.isActive == true
}
