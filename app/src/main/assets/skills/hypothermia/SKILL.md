---
name: hypothermia
description: Use when someone on the trail is cold, shivering, wet, confused, clumsy, or has been exposed to cold, wind, or rain for a long time.
---

# Hypothermia

## When this applies
A person has been cold and wet or exposed to wind and is shivering, fumbling, stumbling, mumbling, or unusually quiet. Hypothermia happens above freezing too, especially when wet. It gets worse fast without action.

## Priority order
1. Stop heat loss: get out of wind and rain, off the ground, and out of wet clothes.
2. Insulate: dry layers, hat, and something under the person.
3. Add fuel: warm sweet drinks and food only if the person is alert and can swallow.
4. Add gentle heat: warm bottles wrapped in cloth on the chest, armpits, and groin.
5. Handle gently and keep the person horizontal if they are moderate or severe.

## Assets (progressive disclosure)
- Load `assets/steps.md` first.
- Load `assets/signs-by-stage.md` only when the user asks how bad it is, whether they can keep walking, or describes confusion, no shivering, or unresponsiveness.

## Emergency services
Tell the user to call emergency services when the person stops shivering while still cold, is confused or cannot walk a straight line, becomes drowsy or unresponsive, or does not improve within thirty minutes of shelter and warming. If unresponsive and not breathing, start CPR.

## Tools to consider
- `get_device_status` to check battery, since cold drains phones fast.
- `get_location` and `send_location_sms` so rescuers know the shelter position.
- `call_emergency_contact` for moderate or severe cases.
- `start_named_timer` named "rewarm" for 30 minutes to schedule a recheck.
