---
name: cpr-adult
description: Use when an adult is unresponsive and not breathing normally, or when the user asks how to do CPR or chest compressions.
---

# Adult CPR

## When this applies
An adult collapses, does not respond to shouting and tapping, and is not breathing or is only gasping. Cardiac arrest on the trail is a minute-by-minute emergency.

## Priority order
1. Confirm the scene is safe and the person is unresponsive.
2. Get help: call emergency services and, if others are present, send someone for an AED.
3. Start chest compressions immediately. Compressions matter more than anything else.
4. Add rescue breaths only if the rescuer is trained and willing.
5. Do not stop until help arrives, the person moves, or the rescuer is exhausted.

## Assets (progressive disclosure)
- Load `assets/steps.md` first. Read the steps one at a time, in order.
- Load `assets/common-mistakes.md` only if the user reports that compressions feel wrong, is unsure about depth or speed, or asks what they are doing wrong.

## Emergency services
Tell the user to call emergency services immediately, before starting compressions if alone, with the phone on speaker. If there is no signal, tell them to keep doing CPR and retry the call every few minutes.

## Tools to consider
- `get_device_status` to check signal and battery before relying on the phone.
- `call_emergency_contact` to place the call hands-free.
- `get_location` then `send_location_sms` so rescuers know where to go.
- `start_cpr_metronome` at 110 bpm to pace compressions; `stop_cpr_metronome` when done.
- `start_named_timer` named "cpr" to track time since starting.
