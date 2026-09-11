# Trail Aid — ADK for Kotlin demo

An offline first-aid guide for hikers. The **only** model is Gemma 4 E2B running on the phone
through LiteRT-LM, so the agent works with no signal at all. It follows seven protocol **skills**,
acts on the phone's hardware through tools (GPS, battery, CPR metronome, named timers) and, only
after the hiker approves, calls or texts the emergency contact with the coordinates.
Built with [ADK for Kotlin](https://github.com/google/adk-kotlin) 1.0.1. No Firebase, no API key.

> Educational demo. Not a substitute for first-aid training or professional care. Protocol text is
> illustrative and follows public lay-rescuer guidance.

## What you'll learn

| ADK feature | Where |
|---|---|
| `LiteRtLmModel` + `EngineConfig` (CPU, vision backend for the photo): one engine per process, loaded once | `agent/AgentRuntime.kt` |
| Tool calling on device: seven `@Tool` functions over real hardware, tiny results, `FunctionTool.ERROR_KEY` when GPS or permissions are missing | `agent/Tools.kt` |
| `SkillToolset` + `AssetSkillSource` with progressive disclosure: `steps.md` first, `tourniquet.md` / `snake.md` / `improvised-splint.md` only when needed | `assets/skills/*`, `agent/FirstAidAgent.kt` |
| Human-in-the-loop (`requireConfirmation = true`) for `call_emergency_contact` and `send_location_sms` | `agent/Tools.kt`, `ui/components/ConfirmationSheet.kt` |
| Answers read aloud sentence by sentence with `TextToSpeech` (off the main thread) | `ui/emergency/EmergencyViewModel.kt` |
| `RoomSessionService` resumability: kill the process mid-guidance, reopen, the incident continues; the journal entry is rebuilt from the session events | `AgentRuntime.replay`, `EmergencyViewModel.closeIncident` |
| Image input to the same on-device agent (`Part(inlineData = Blob("image/jpeg"))`) | `AgentRuntime.sendPhoto` |
| Prompt budget for a 2B model: short instruction, ≤ 400-word assets, one action per turn | `FirstAidAgent.INSTRUCTION`, `assets/skills` |

## Architecture

```mermaid
flowchart TB
  subgraph UI["Compose UI (large type, high contrast)"]
    Prep["Prepare\nmodel download · self-test · contact · disclaimer"]
    Em["Emergency\nchat + chips · status panel · TTS · photo"]
    Sheet["Confirmation sheet\nCall / Send SMS"]
    Jr["Incident journal · static protocols (fallback)"]
  end
  subgraph Agent["agent/ (all on device)"]
    RT["AgentRuntime\nengine holder · runAsync · replay\nparseMisformattedCall recovery"]
    FA["LlmAgent trail_aid"]
    DT["DeviceTools\nget_location · get_device_status"]
    TT["TimerTools\nstart_cpr_metronome · stop_cpr_metronome · start_named_timer"]
    ET["EmergencyTools (requireConfirmation)\ncall_emergency_contact · send_location_sms"]
    SK["SkillToolset\ncpr-adult · bleeding · fracture-sprain · hypothermia\nheat-stroke · bite-sting · choking"]
    HW["HardwareStateHolder\n→ status panel"]
    LR["LiteRtLmModel\nGemma 4 E2B, CPU + vision backend"]
  end
  subgraph Services
    Room["RoomSessionService\nincident-<timestamp>"]
    DS["DataStore: contact · active incident"]
    DB["Room: incidents journal"]
  end
  Sys["Android: FusedLocation · BatteryManager · ToneGenerator\nACTION_CALL · SmsManager · CameraX · TextToSpeech"]

  Prep --> DS
  Prep -- EMERGENCY --> Em --> RT --> FA --> DT & TT & ET & SK
  FA --> LR
  RT --> Room
  DT & TT & ET --> HW --> Em
  DT & TT & ET --> Sys
  ET -. adk_request_confirmation .-> Sheet --> RT
  Em -- End emergency --> DB --> Jr
```

### The spec scenario as the agent runs it

```mermaid
sequenceDiagram
  participant H as Hiker
  participant App as Trail Aid
  participant A as trail_aid (on device)
  App->>A: "[app] Emergency started"
  A-->>H: "What happened?"
  H->>App: "My friend fell, his leg is bleeding, he can't stand"
  A->>A: load_skill(bleeding) · load_skill_resource(assets/steps.md)
  A-->>H: "Apply firm direct pressure…" (spoken)
  H->>App: "Start the pressure timer, check the battery, call my contact"
  A->>A: start_named_timer(pressure) → status panel "pressure @ 11:05"
  A->>A: get_device_status → "100 %"
  A->>App: call_emergency_contact → confirmation sheet
  H->>App: Call
  App->>App: ACTION_CALL → dialer
  H->>App: "Send my location by SMS"
  A->>App: send_location_sms → confirmation sheet
  H->>App: Send SMS
  App->>App: get GPS fix + SmsManager.sendTextMessage
  H->>App: "He stopped breathing, start CPR"
  A->>A: start_cpr_metronome → 110 bpm clicks
  H->>App: End emergency → journal entry from session events
```

## Setup

1. `./gradlew :app:installDebug` (JDK 17+; Gradle 9.7.1 / AGP 9.4.0 pinned by the wrapper).
2. Prepare screen → *Download over Wi-Fi* (2.6 GB), or push the file:
   `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/dev.ykro.trailaid/files/`
3. Save an emergency contact. On the emulator any number works; the dialer opens and the SMS is accepted.
4. *Test model* shows load time and time to first answer.

## Demo script

Airplane mode on. Emergency → "my friend fell, his leg is bleeding and he can't stand on his foot".
Expected chips: `load_skill(bleeding)` → `load_skill_resource(assets/steps.md)`; then
`start_named_timer(pressure)`, `get_device_status`, `call_emergency_contact` → approval sheet → the
dialer opens; `send_location_sms` → approval → SMS with a maps link; CPR request →
`start_cpr_metronome` (audible clicks at 110 bpm). Close the emergency: the summary lands in the journal.
`adb shell am force-stop dev.ykro.trailaid` and reopen: the incident resumes without asking "what happened" again.

Emulator notes: GPS comes from *Extended controls → Location* (or `adb emu geo fix`), the camera is
the emulated scene, and each model turn takes 1–5 minutes on the arm64 emulator (a Pixel is several
times faster). Keep the emulator otherwise idle.

## Verified on the emulator (Pixel_9_API_36)

Engine load (3.6 s warm, longer on the first load), `load_skill(bleeding)` + `steps.md`,
`start_named_timer`, `get_device_status`, `call_emergency_contact` → in-call screen, `send_location_sms`
→ SMS sent, `start_cpr_metronome` → 110 bpm, resume after reinstall, journal entry.

## Things the emulator taught us about on-device tool calling (LiteRT-LM 0.13.1 + ADK 1.0.1)

- The function-call grammar accepts strings only between `<|"|>` markers. If the model writes
  `skill_name="bleeding"` the runtime throws `Failed to parse tool calls from code block`. The
  instruction shows the marker syntax, and `AgentRuntime` recovers such calls from the error text
  (`parseMisformattedCall`) so the guidance continues.
- Numeric tool arguments break event persistence (`AnySerializer cannot serialize LazilyParsedNumber`),
  so every on-device tool takes strings or nothing (`start_cpr_metronome` is fixed at 110 bpm).
- `RunConfig(streamingMode = NONE)`: streaming brings nothing on a 2B CPU model and the same parser
  errors surface either way; the app speaks the final answer sentence by sentence instead.
- A small model may skip a prerequisite tool; `send_location_sms` fetches the GPS fix itself.
- Everything the tools change is also reflected in the ADK session, so the incident summary is
  rebuilt from `replay(sessionEvents)` and survives process restarts.

## Play policy note

`CALL_PHONE` and `SEND_SMS` are restricted permissions on Google Play. The demo acts directly so the
audience sees the agent execute after approval; a published app would use `ACTION_DIAL` and
`ACTION_SENDTO`, which open the phone and SMS apps with the content pre-filled.

## Layout

```
app/src/main/kotlin/dev/ykro/trailaid/
  agent/   FirstAidAgent, tools (device, timers, emergency), hardware state, metronome, runtime, model store
  data/    Room incident journal, DataStore contact + active incident
  ui/      prepare, emergency (chat + status panel + photo), journal + static protocols
app/src/main/assets/skills/<protocol>/SKILL.md + assets/steps.md (+ conditional assets)
app/src/test/   hardware state summary, misformatted tool-call recovery
```
