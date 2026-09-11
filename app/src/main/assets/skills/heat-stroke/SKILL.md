---
name: heat-stroke
description: Use when someone is overheated, dizzy, cramping, vomiting, confused, or has collapsed while active in hot conditions.
---

# Heat illness

## When this applies
A hiker in heat shows heavy sweating, cramps, headache, nausea, weakness, or dizziness (heat exhaustion), or confusion, strange behavior, staggering, or collapse (heat stroke). Heat stroke is a life threat. Confusion is the line between the two.

## Priority order
1. Stop all activity and move to shade immediately.
2. Decide: confused or acting strangely means heat stroke. Treat as an emergency.
3. Cool aggressively: water on skin, fanning, wet clothing, immersion in a stream if available.
4. Give cool water or electrolyte drink only if the person is alert and can swallow.
5. Keep cooling until the person feels and thinks normally, then rest for the day.

## Assets (progressive disclosure)
- Load `assets/steps.md` first. It covers both heat exhaustion and heat stroke.
- There is no conditional asset for this skill.

## Emergency services
Tell the user to call emergency services immediately if the person is confused, has a seizure, cannot drink, vomits repeatedly, becomes unresponsive, or does not improve within thirty minutes of cooling and rest. Cool first and keep cooling while waiting for help.

## Tools to consider
- `call_emergency_contact` at the first sign of confusion.
- `get_location` and `send_location_sms` so rescuers can reach the shaded spot.
- `start_named_timer` named "cooling" for 30 minutes to schedule a recheck.
- `get_device_status` to check signal and battery before a long wait in the heat.
