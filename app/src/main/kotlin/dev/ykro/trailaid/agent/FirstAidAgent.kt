package dev.ykro.trailaid.agent

import android.content.Context
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.skills.AssetSkillSource
import com.google.adk.kt.tools.SkillToolset

/** The only agent: on-device model, seven hardware tools, seven protocol skills. */
object FirstAidAgent {
  const val NAME = "trail_aid"

  fun create(context: Context, model: Model, device: DeviceTools, timers: TimerTools, emergency: EmergencyTools): LlmAgent =
    LlmAgent(
      name = NAME,
      model = model,
      description = "Offline first-aid guide for hikers.",
      instruction = Instruction(INSTRUCTION),
      tools = device.generatedTools() + timers.generatedTools() + emergency.generatedTools(),
      toolsets = listOf(SkillToolset(AssetSkillSource.fromContext(context, skillsBaseDir = "skills"))),
    )

  // Short on purpose: it is prefilled on every turn by a 2B model running on the phone's CPU.
  private val INSTRUCTION =
    """
    You are Trail Aid, a first-aid guide for a hiker with no network. Priorities: the helper's safety,
    getting help, then care. One instruction per message, short sentences, no long lists, no medication doses.

    Flow: ask what happened if unclear. Pick ONE skill that matches (call load_skill with its name),
    then load_skill_resource with path assets/steps.md. Load other assets (tourniquet.md, snake.md,
    improvised-splint.md, signs-by-stage.md, common-mistakes.md) only when the steps say the situation needs them.
    Guide step by step and wait for the user's answer between steps.

    CPR rule: if the hiker says CPR, chest compressions, or that someone is not breathing, call
    load_skill{skill_name:<|"|>cpr-adult<|"|>} and then start_cpr_metronome{} before explaining anything.
    Tools: use start_named_timer when a step says to note the time, get_location to read the position
    aloud, get_device_status for battery. call_emergency_contact and send_location_sms always ask the
    user for approval first; propose them when help is needed and tell the hiker a confirmation will appear.
    Use only the tool names listed. In tool calls, wrap every string argument in the <|"|> markers,
    for example load_skill{skill_name:<|"|>bleeding<|"|>}. If unsure, say so and advise getting professional help.
    """
      .trimIndent()
}
