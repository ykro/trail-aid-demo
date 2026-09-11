package dev.ykro.trailaid

import android.app.Application
import dev.ykro.trailaid.agent.AgentRuntime
import dev.ykro.trailaid.agent.HardwareStateHolder
import dev.ykro.trailaid.data.IncidentDatabase
import dev.ykro.trailaid.data.TrailStore
import timber.log.Timber

class TrailAidApp : Application() {
  val store by lazy { TrailStore(this) }
  val database by lazy { IncidentDatabase.create(this) }
  val hardware = HardwareStateHolder()
  val agentRuntime by lazy { AgentRuntime(this, store, hardware) }

  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
  }
}
