package dev.ykro.trailaid.ui.prepare

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ykro.trailaid.R
import dev.ykro.trailaid.agent.ModelStore
import dev.ykro.trailaid.ui.theme.Forest
import dev.ykro.trailaid.ui.theme.OrangeSoft
import dev.ykro.trailaid.ui.theme.Red
import dev.ykro.trailaid.ui.theme.Success

data class Protocol(val skill: String, val title: String, val image: Int)

val PROTOCOLS =
  listOf(
    Protocol("cpr-adult", "CPR (adult)", R.drawable.protocol_cpr_adult),
    Protocol("bleeding", "Bleeding", R.drawable.protocol_bleeding),
    Protocol("fracture-sprain", "Fracture or sprain", R.drawable.protocol_fracture_sprain),
    Protocol("hypothermia", "Hypothermia", R.drawable.protocol_hypothermia),
    Protocol("heat-stroke", "Heat stroke", R.drawable.protocol_heat_stroke),
    Protocol("bite-sting", "Bite or sting", R.drawable.protocol_bite_sting),
    Protocol("choking", "Choking", R.drawable.protocol_choking),
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareScreen(viewModel: PrepareViewModel, onEmergency: () -> Unit, onJournal: () -> Unit, onProtocols: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var name by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  LaunchedEffect(state.contact) { name = state.contact.name; phone = state.contact.phone; message = state.contact.baseMessage }
  val ready = state.modelPresent && state.meetsRequirements

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Trail Aid") },
        actions = { IconButton(onClick = onJournal) { Icon(Icons.Outlined.MenuBook, "Incident journal") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      Column(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Button(
          onClick = onEmergency,
          modifier = Modifier.fillMaxWidth().height(76.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
          shape = RoundedCornerShape(20.dp),
        ) {
          Image(painterResource(R.drawable.emergency_glyph), null, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
          Spacer(Modifier.size(12.dp))
          Text(if (state.activeIncident != null) "RESUME EMERGENCY" else if (ready) "EMERGENCY" else "EMERGENCY (protocols only)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        Text(
          if (ready) "Works with no signal. Loads the model in a few seconds." else "The agent needs the model below; without it you get the protocols as text.",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 6.dp),
        )
      }
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Image(painterResource(R.drawable.prepare_hero), null, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
      Text("Prepare before you leave", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      Text("Everything below needs Wi-Fi once. Out on the trail, Trail Aid runs entirely on this phone.", style = MaterialTheme.typography.bodyMedium)

      Section("1 · On-device model") {
        if (!state.meetsRequirements) {
          Text("This device has ${"%.1f".format(state.totalRamGb)} GB of RAM; the model needs about ${PrepareViewModel.MIN_RAM_GB.toInt()} GB. The Emergency screen shows the protocols as text only.", color = Red)
        } else if (state.modelPresent) {
          Text("Downloaded: ${state.modelFileName} (${ModelStore.SIZE_LABEL})", color = Success)
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::testModel, enabled = !state.testing) { Text(if (state.testing) "Testing…" else "Test model") }
            OutlinedButton(onClick = viewModel::deleteModel) { Text("Delete") }
          }
          state.testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        } else if (state.downloading) {
          Text("Downloading ${ModelStore.FILE_NAME} (${ModelStore.SIZE_LABEL})…")
          LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
          Text("${(state.progress * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
          OutlinedButton(onClick = viewModel::cancelDownload) { Text("Cancel") }
        } else {
          Text("${ModelStore.FILE_NAME} (${ModelStore.SIZE_LABEL}) is not on this device.")
          state.error?.let { Text(it, color = Red, style = MaterialTheme.typography.bodySmall) }
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::download) { Text("Download over Wi-Fi") }
            OutlinedButton(onClick = viewModel::refreshModel) { Text("Re-check") }
          }
          Text(state.pushCommand, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(top = 8.dp))
        }
      }

      Section("2 · Emergency contact") {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Base SMS text") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = { viewModel.saveContact(name, phone, message) }) { Text("Save contact") }
        if (state.contact.isConfigured) Text("Saved: ${state.contact.name.ifBlank { "contact" }} · ${state.contact.phone}", color = Success, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
      }

      Section("3 · Protocols on board") {
        Text("Seven skills, each under 400 words so a 2B model can read them fast. The agent loads one at a time.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onProtocols) { Text("Browse protocols as text") }
      }

      Column(Modifier.fillMaxWidth().background(OrangeSoft, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text("Educational demo", style = MaterialTheme.typography.titleSmall)
        Text("Not a substitute for first-aid training or professional care. Protocol text is illustrative and follows public guidance; when in doubt, call emergency services.", style = MaterialTheme.typography.bodySmall)
        if (!state.disclaimerAccepted) {
          Spacer(Modifier.height(6.dp))
          OutlinedButton(onClick = viewModel::acceptDisclaimer) { Text("I understand") }
        }
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(16.dp)) {
      Text(title, style = MaterialTheme.typography.labelLarge, color = Forest)
      Spacer(Modifier.height(8.dp))
      content()
    }
  }
}
