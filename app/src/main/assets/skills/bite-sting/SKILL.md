---
name: bite-sting
description: Use when the user reports an insect sting, tick, spider or animal bite, or a snake bite, including any swelling, hives, or trouble breathing afterward.
---

# Bites and stings

## When this applies
Bee, wasp, or ant stings, tick bites, spider bites, and snake bites. Most stings are minor. The dangerous cases are severe allergic reaction (anaphylaxis) and venomous snake bite.

## Priority order
1. Get away from the insects or the snake. Do not try to catch or kill the snake.
2. Check for anaphylaxis: trouble breathing, swelling of the face or throat, spreading hives, dizziness. Epinephrine and emergency services come first.
3. For snake bites, keep the person calm and still and begin evacuation.
4. For minor stings, remove the stinger, clean, cool, and watch for thirty minutes.
5. For ticks, remove promptly with fine tweezers and note the date.

## Assets (progressive disclosure)
- Load `assets/steps.md` first for insect stings, allergic reactions, and ticks.
- Load `assets/snake.md` only when the user says a snake bit someone or describes puncture marks with rapidly spreading pain and swelling.

## Emergency services
Tell the user to call emergency services immediately for any breathing difficulty, throat or tongue swelling, fainting, hives spreading beyond the sting, any snake bite, or a sting inside the mouth or throat. If epinephrine is used, call anyway.

## Tools to consider
- `call_emergency_contact` for anaphylaxis or snake bite.
- `get_location` and `send_location_sms` to share the position for evacuation.
- `start_named_timer` named "swelling" for 15 minutes to track swelling after a snake bite.
- `start_named_timer` named "observe" for 30 minutes after a sting.
- `get_device_status` to confirm signal before a long evacuation.
