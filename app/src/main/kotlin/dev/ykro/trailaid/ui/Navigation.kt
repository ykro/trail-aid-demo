package dev.ykro.trailaid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.ykro.trailaid.TrailAidApp
import dev.ykro.trailaid.ui.emergency.EmergencyScreen
import dev.ykro.trailaid.ui.emergency.EmergencyViewModel
import dev.ykro.trailaid.ui.journal.JournalScreen
import dev.ykro.trailaid.ui.journal.ProtocolsScreen
import dev.ykro.trailaid.ui.prepare.PrepareScreen
import dev.ykro.trailaid.ui.prepare.PrepareViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable object PrepareRoute
@Serializable data class EmergencyRoute(val sessionId: String)
@Serializable object JournalRoute
@Serializable object ProtocolsRoute

@Composable
fun TrailAidNavHost(app: TrailAidApp, nav: NavHostController) {
  val scope = rememberCoroutineScope()
  val contact by app.store.contact.collectAsStateWithLifecycle(initialValue = null)
  NavHost(navController = nav, startDestination = PrepareRoute) {
    composable<PrepareRoute> {
      val vm: PrepareViewModel = viewModel(factory = viewModelFactory { initializer { PrepareViewModel(app, app.store, app.agentRuntime) } })
      PrepareScreen(
        vm,
        onEmergency = {
          scope.launch {
            if (!app.agentRuntime.isModelInstalled()) {
              nav.navigate(ProtocolsRoute)
              return@launch
            }
            val id = app.store.activeIncidentNow() ?: ("incident-" + System.currentTimeMillis()).also { app.store.setActiveIncident(it) }
            nav.navigate(EmergencyRoute(id))
          }
        },
        onJournal = { nav.navigate(JournalRoute) },
        onProtocols = { nav.navigate(ProtocolsRoute) },
      )
    }
    composable<EmergencyRoute> { entry ->
      val route = entry.toRoute<EmergencyRoute>()
      val vm: EmergencyViewModel =
        viewModel(key = route.sessionId, factory = viewModelFactory { initializer { EmergencyViewModel(app, route.sessionId, app.agentRuntime, app.store, app.database) } })
      EmergencyScreen(vm, contactLabel = contact?.let { it.name.ifBlank { it.phone } }?.ifBlank { "your contact" } ?: "your contact", onClosed = { nav.popBackStack(PrepareRoute, inclusive = false) })
    }
    composable<JournalRoute> { JournalScreen(app, onBack = { nav.popBackStack() }) }
    composable<ProtocolsRoute> { ProtocolsScreen(onBack = { nav.popBackStack() }) }
  }
}
