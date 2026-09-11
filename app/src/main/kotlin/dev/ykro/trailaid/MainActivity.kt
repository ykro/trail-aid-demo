package dev.ykro.trailaid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import dev.ykro.trailaid.ui.EmergencyRoute
import dev.ykro.trailaid.ui.TrailAidNavHost
import dev.ykro.trailaid.ui.theme.TrailAidTheme
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val app = application as TrailAidApp
    setContent {
      TrailAidTheme {
        val nav = rememberNavController()
        // Resumability: an incident the system killed mid-guidance reopens straight into its session.
        LaunchedEffect(Unit) {
          val active = app.store.activeIncidentNow()
          if (active != null && app.agentRuntime.isModelInstalled()) {
            Timber.i("Resuming incident %s", active)
            nav.navigate(EmergencyRoute(active))
          }
        }
        TrailAidNavHost(app, nav)
      }
    }
  }

  override fun onDestroy() {
    if (isFinishing) lifecycleScope.launch { (application as TrailAidApp).agentRuntime.release() }
    super.onDestroy()
  }
}
