package dev.ykro.trailaid.ui.journal

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ykro.trailaid.R
import dev.ykro.trailaid.TrailAidApp
import dev.ykro.trailaid.data.IncidentEntity
import dev.ykro.trailaid.ui.components.MarkdownText
import dev.ykro.trailaid.ui.prepare.PROTOCOLS
import dev.ykro.trailaid.ui.theme.Forest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault())

/** Incident summaries saved when an emergency is closed: what to show rescuers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(app: TrailAidApp, onBack: () -> Unit) {
  val incidents by app.database.incidents().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Incident journal") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    if (incidents.isEmpty()) {
      Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.incident_summary), null, Modifier.size(180.dp).clip(RoundedCornerShape(20.dp)))
        Spacer(Modifier.height(12.dp))
        Text("No incidents yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Closing an emergency saves a summary here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      return@Scaffold
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      items(incidents, key = { it.sessionId }) { IncidentCard(it) }
    }
  }
}

@Composable
private fun IncidentCard(i: IncidentEntity) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("${fmt.format(Instant.ofEpochMilli(i.startedAtEpochMs))} → ${fmt.format(Instant.ofEpochMilli(i.closedAtEpochMs))}", style = MaterialTheme.typography.labelLarge, color = Forest)
      if (i.protocols.isNotBlank()) Text("Protocols: ${i.protocols.replace(",", ", ")}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      i.timers.lines().filter { it.isNotBlank() }.forEach { Text("Timer $it", style = MaterialTheme.typography.bodyMedium) }
      i.location?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
      Text(listOfNotNull(if (i.called) "Called emergency contact" else null, if (i.smsSent) "Location SMS sent" else null).ifEmpty { listOf("No calls or SMS") }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

private suspend fun readAsset(context: Context, path: String): String =
  withContext(Dispatchers.IO) { runCatching { context.assets.open(path).bufferedReader().readText() }.getOrElse { "" } }

/** Fallback for devices that cannot run the model, and a browsable reference otherwise. Static text, no agent. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var selected by remember { mutableStateOf(PROTOCOLS.first()) }
  val text by produceState("", selected) { value = readAsset(context, "skills/${selected.skill}/assets/steps.md") }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Protocols") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PROTOCOLS.forEach { p ->
          Column(
            Modifier.clip(RoundedCornerShape(14.dp)).clickable { selected = p }.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Image(painterResource(p.image), null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
            Text(p.title, style = MaterialTheme.typography.labelMedium, color = if (p == selected) Forest else MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(16.dp)) {
          Text(selected.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(8.dp))
          MarkdownText(text)
        }
      }
    }
  }
}

