package dev.ykro.trailaid.ui.emergency

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ykro.trailaid.R
import dev.ykro.trailaid.agent.AgentRuntime
import dev.ykro.trailaid.agent.HardwareState
import dev.ykro.trailaid.ui.components.ConfirmationSheet
import dev.ykro.trailaid.ui.components.ToolCallChip
import dev.ykro.trailaid.ui.theme.Forest
import dev.ykro.trailaid.ui.theme.ForestSoft
import dev.ykro.trailaid.ui.theme.Ink
import dev.ykro.trailaid.ui.theme.OrangeSoft
import dev.ykro.trailaid.ui.theme.Red
import dev.ykro.trailaid.ui.theme.RedSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(viewModel: EmergencyViewModel, contactLabel: String, onClosed: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  var input by remember { mutableStateOf("") }
  var showCamera by remember { mutableStateOf(false) }

  // Runtime permissions the hardware tools need. Asked once when the emergency opens.
  val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
  LaunchedEffect(Unit) { permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.CAMERA)) }
  LaunchedEffect(state.items.size, state.streamingText) {
    val count = listState.layoutInfo.totalItemsCount
    if (count > 0) listState.animateScrollToItem(count - 1)
  }

  if (showCamera) {
    PhotoCaptureDialog(onDismiss = { showCamera = false }, onCaptured = { bmp: Bitmap -> showCamera = false; viewModel.sendPhoto(bmp) })
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Emergency", style = MaterialTheme.typography.titleLarge, color = Red)
            Text(AgentRuntime.MODEL_LABEL + if (state.loadMs > 0) " · loaded in ${state.loadMs / 1000.0} s" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        actions = {
          IconButton(onClick = viewModel::toggleTts) { Icon(if (state.ttsEnabled) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff, "Read aloud") }
          IconButton(onClick = { showCamera = true }, enabled = !state.busy && !state.loadingEngine) { Icon(Icons.Outlined.PhotoCamera, "Injury photo") }
          IconButton(onClick = { viewModel.closeIncident(onClosed) }) { Icon(Icons.Default.Close, "End emergency") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        StatusPanel(state.hardware)
        if (state.fatal == null) {
          Row(Modifier.padding(12.dp).imePadding(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = input,
              onValueChange = { input = it },
              modifier = Modifier.weight(1f),
              placeholder = { Text(if (state.loadingEngine) "Loading model…" else if (state.busy) "Thinking…" else "Say what you see") },
              enabled = !state.busy && !state.loadingEngine,
              maxLines = 3,
            )
            FilledIconButton(onClick = { viewModel.say(input); input = "" }, enabled = !state.busy && input.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
          }
        }
      }
    },
  ) { padding ->
    if (state.fatal != null) {
      Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(state.fatal!!, color = Red, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { viewModel.closeIncident(onClosed) }) { Text("Back to Prepare") }
      }
      return@Scaffold
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (state.loadingEngine) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Loading the model on this phone (about 5 s)…", style = MaterialTheme.typography.bodyLarge)
          }
        }
      }
      if (state.resumed) item { Text("Resumed the incident from the saved session", style = MaterialTheme.typography.labelLarge, color = Forest) }
      items(state.items) { TimelineRow(it) }
      state.streamingText?.let { item { AgentBubble(it, streaming = true) } }
      if (state.busy && state.streamingText == null && !state.loadingEngine) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      if (state.canRetry && !state.busy) item { OutlinedButton(onClick = viewModel::continueAfterError) { Text("Ask the agent to continue") } }
      item { Spacer(Modifier.height(8.dp)) }
    }
  }

  state.confirmation?.let { pending ->
    val isCall = pending.toolName == "call_emergency_contact"
    ConfirmationSheet(
      toolName = pending.toolName,
      args = pending.args,
      hint = pending.hint,
      busy = state.busy,
      title = if (isCall) "Call $contactLabel?" else "Send SMS to $contactLabel?",
      preview = if (isCall) "The phone will dial the emergency contact now." else viewModel.previewSms(),
      confirmLabel = if (isCall) "Call" else "Send SMS",
      onConfirm = { viewModel.resolveConfirmation(true) },
      onCancel = { viewModel.resolveConfirmation(false) },
    )
  }
}

/** Fixed panel: changes live when tools run, so the audience sees the agent act on hardware. */
@Composable
private fun StatusPanel(hw: HardwareState) {
  Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      StatusItem(Icons.Outlined.Favorite, hw.metronomeBpm?.let { "Metronome $it bpm" } ?: "Metronome off", active = hw.metronomeBpm != null)
      StatusItem(Icons.Outlined.Place, hw.location?.let { "±${it.accuracyM.toInt()} m" } ?: "No fix", active = hw.location != null)
      StatusItem(Icons.Outlined.BatteryStd, hw.batteryPercent?.let { "$it %" } ?: "Battery ?", active = hw.batteryPercent != null)
      hw.timers.forEach { StatusItem(Icons.Outlined.Timer, it.label, active = true) }
    }
    hw.location?.let { Text(it.spoken, style = MaterialTheme.typography.labelMedium, color = ForestSoft) }
  }
}

@Composable
private fun StatusItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Icon(icon, null, Modifier.size(16.dp), tint = if (active) OrangeSoft else Color(0xFF8A9A8C))
    Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) Color.White else Color(0xFF8A9A8C))
  }
}

@Composable
private fun TimelineRow(item: TimelineItem) {
  when (item) {
    is TimelineItem.Tools -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { item.chips.forEach { ToolCallChip(it) } }
    is TimelineItem.Agent -> AgentBubble(item.text, streaming = false)
    is TimelineItem.User ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(item.text, Modifier.background(Forest, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, style = MaterialTheme.typography.bodyLarge)
      }
    is TimelineItem.System -> Text(item.text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    is TimelineItem.Failure -> Text(item.text, Modifier.fillMaxWidth().background(RedSoft, RoundedCornerShape(12.dp)).padding(12.dp), color = Red, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun AgentBubble(text: String, streaming: Boolean) {
  Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Image(painterResource(R.drawable.agent_avatar), null, Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)))
    Text(
      text,
      Modifier.weight(1f, fill = false).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = if (streaming) FontWeight.Normal else FontWeight.Medium,
      color = if (streaming) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
  }
}
