package dev.ykro.trailaid

import dev.ykro.trailaid.agent.HardwareState
import dev.ykro.trailaid.agent.HardwareStateHolder
import dev.ykro.trailaid.agent.LocationFix
import dev.ykro.trailaid.agent.NamedTimer
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The status panel and the incident summary are built from this state; pin their shape. */
class HardwareStateTest {
  @Test
  fun `named timer label carries the clock time rescuers need`() {
    val t = NamedTimer("tourniquet", LocalTime.of(14, 32))
    assertEquals("tourniquet @ 14:32", t.label)
  }

  @Test
  fun `summary lines list protocols, timers, location and contact actions`() {
    val state =
      HardwareState(
        protocolsUsed = listOf("bleeding", "fracture-sprain"),
        timers = listOf(NamedTimer("pressure", LocalTime.of(9, 5))),
        location = LocationFix(19.35, -99.29, 12f, null, "Latitude 19.3500, longitude -99.2900, accuracy 12 meters", isLastKnown = false),
        called = true,
        smsSent = false,
      )
    val lines = state.summaryLines
    assertEquals("Protocols: bleeding, fracture-sprain", lines[0])
    assertEquals("Timer pressure @ 09:05", lines[1])
    assertTrue(lines[2].startsWith("Location Latitude"))
    assertEquals("Emergency contact called", lines[3])
    assertEquals(4, lines.size)
  }

  @Test
  fun `holder updates are atomic and reset clears everything`() {
    val holder = HardwareStateHolder()
    holder.update { copy(metronomeBpm = 110) }
    holder.update { copy(batteryPercent = 80) }
    assertEquals(110, holder.state.value.metronomeBpm)
    assertEquals(80, holder.state.value.batteryPercent)
    holder.reset()
    assertEquals(HardwareState(), holder.state.value)
  }
}
