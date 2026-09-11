package dev.ykro.trailaid.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.google.adk.kt.tools.FunctionTool
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dev.ykro.trailaid.data.TrailStore
import java.time.LocalTime
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** Hardware the agent can read. Results are tiny on purpose: a 2B model handles short tool outputs best. */
class DeviceTools(private val context: Context, private val hardware: HardwareStateHolder) {
  @Tool(name = "get_location", description = "Current GPS position: latitude, longitude, accuracy in meters and a short sentence to read aloud.")
  suspend fun getLocation(): Map<String, Any?> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      return mapOf(FunctionTool.ERROR_KEY to "Location permission not granted.")
    }
    val client = LocationServices.getFusedLocationProviderClient(context)
    val fresh =
      withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { cont ->
          val cts = CancellationTokenSource()
          @Suppress("MissingPermission")
          client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) }
          cont.invokeOnCancellation { cts.cancel() }
        }
      }
    val location =
      fresh
        ?: withTimeoutOrNull(2_000) {
          suspendCancellableCoroutine { cont ->
            @Suppress("MissingPermission")
            client.lastLocation.addOnSuccessListener { cont.resume(it) }.addOnFailureListener { cont.resume(null) }
          }
        }
        ?: return mapOf(FunctionTool.ERROR_KEY to "No GPS fix yet. Move to open sky and try again.")
    val spoken = String.format(Locale.US, "Latitude %.4f, longitude %.4f, accuracy %d meters", location.latitude, location.longitude, location.accuracy.toInt())
    val fix = LocationFix(location.latitude, location.longitude, location.accuracy, if (location.hasAltitude()) location.altitude else null, spoken, isLastKnown = fresh == null)
    hardware.update { copy(location = fix) }
    return mapOf("latitude" to fix.latitude, "longitude" to fix.longitude, "accuracyMeters" to fix.accuracyM.toInt(), "altitudeMeters" to fix.altitudeM?.toInt(), "spoken" to spoken, "lastKnown" to fix.isLastKnown)
  }

  @Tool(name = "get_device_status", description = "Battery level in percent, to decide whether to save power.")
  fun getDeviceStatus(): Map<String, Any?> {
    val bm = context.getSystemService(BatteryManager::class.java)
    val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    hardware.update { copy(batteryPercent = percent) }
    return mapOf("batteryPercent" to percent, "lowBattery" to (percent in 0..20))
  }
}

class TimerTools(private val metronome: Metronome, private val hardware: HardwareStateHolder) {
  // No numeric parameter on purpose: ADK 1.0.1 cannot persist LiteRT-LM function calls whose
  // arguments are numbers (Gson LazilyParsedNumber). 110 bpm sits inside the 100–120 guideline.
  @Tool(name = "start_cpr_metronome", description = "Starts an audible metronome for CPR chest compressions at 110 per minute.")
  fun startCprMetronome(): Map<String, Any?> {
    val rate = metronome.start(110)
    Timber.i("Metronome started at %d bpm", rate)
    return mapOf("running" to true, "bpm" to rate)
  }

  @Tool(name = "stop_cpr_metronome", description = "Stops the CPR metronome.")
  fun stopCprMetronome(): Map<String, Any?> {
    metronome.stop()
    return mapOf("running" to false)
  }

  @Tool(name = "start_named_timer", description = "Starts a named timer, for example 'tourniquet' or 'pressure', and records the clock time to tell rescuers later.")
  fun startNamedTimer(@Param("Short name for the timer") name: String): Map<String, Any?> {
    val timer = NamedTimer(name.trim().ifBlank { "timer" }, LocalTime.now().withSecond(0).withNano(0))
    hardware.update { copy(timers = timers + timer) }
    return mapOf("name" to timer.name, "startedAt" to timer.label.substringAfter("@ "))
  }
}

/**
 * Both tools need the user's approval (requireConfirmation). For the demo they act directly
 * (ACTION_CALL / SmsManager); a Play-published app would use ACTION_DIAL / ACTION_SENDTO instead.
 */
class EmergencyTools(private val context: Context, private val store: TrailStore, private val hardware: HardwareStateHolder, private val device: DeviceTools) {
  @Tool(name = "call_emergency_contact", description = "Calls the configured emergency contact. Needs the user's approval.", requireConfirmation = true)
  suspend fun callEmergencyContact(): Map<String, Any?> {
    val contact = store.contactNow()
    if (!contact.isConfigured) return mapOf(FunctionTool.ERROR_KEY to "No emergency contact configured.")
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
      return mapOf(FunctionTool.ERROR_KEY to "Call permission not granted.")
    }
    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.phone}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    hardware.update { copy(called = true) }
    Timber.i("Calling %s", contact.phone)
    return mapOf("calling" to contact.name.ifBlank { contact.phone }, "number" to contact.phone)
  }

  @Tool(name = "send_location_sms", description = "Sends an SMS with the current coordinates and a short message to the emergency contact. Needs the user's approval. Works without mobile data.", requireConfirmation = true)
  suspend fun sendLocationSms(@Param("One short sentence about the situation") message: String): Map<String, Any?> {
    val contact = store.contactNow()
    if (!contact.isConfigured) return mapOf(FunctionTool.ERROR_KEY to "No emergency contact configured.")
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
      return mapOf(FunctionTool.ERROR_KEY to "SMS permission not granted.")
    }
    // A small model often forgets to call get_location first; fetch a fix here so the SMS is useful anyway.
    val fix = hardware.state.value.location ?: runCatching { device.getLocation(); hardware.state.value.location }.getOrNull()
    val where = fix?.let { String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f (±%dm)", it.latitude, it.longitude, it.accuracyM.toInt()) } ?: "location unknown"
    val text = "${contact.baseMessage} $message $where".trim()
    return try {
      context.getSystemService(SmsManager::class.java).sendTextMessage(contact.phone, null, text, null, null)
      hardware.update { copy(smsSent = true) }
      Timber.i("SMS sent to %s: %s", contact.phone, text)
      mapOf("sent" to true, "to" to contact.phone, "text" to text)
    } catch (e: Exception) {
      mapOf(FunctionTool.ERROR_KEY to "SMS failed: ${e.message}")
    }
  }

  fun previewSms(message: String): String {
    val fix = hardware.state.value.location
    val where = fix?.let { String.format(Locale.US, "https://maps.google.com/?q=%.5f,%.5f (±%dm)", it.latitude, it.longitude, it.accuracyM.toInt()) } ?: "+ your GPS position (fetched when you approve)"
    return "$message $where"
  }
}
