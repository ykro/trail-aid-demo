---
name: choking
description: Use when someone is choking on food or an object and cannot breathe, cough, or speak normally.
---

# Choking (adult and child over one year)

## When this applies
A person eating or drinking suddenly grabs their throat, cannot speak, has a weak or silent cough, makes high-pitched noises, or turns blue. A person who can cough forcefully and speak has a mild obstruction and needs encouragement, not thrusts.

## Priority order
1. Ask "Are you choking?" Decide mild or severe.
2. Mild: encourage coughing and stay with the person. Do not hit their back.
3. Severe: alternate five back blows and five abdominal thrusts until the object comes out.
4. If the person becomes unresponsive, lower them to the ground and start CPR.
5. Get help early if anyone else is present.

## Assets (progressive disclosure)
- Load `assets/steps.md` first.
- If the person becomes unresponsive, switch to the `cpr-adult` skill and load its `assets/steps.md`. Look in the mouth before each set of breaths.

## Emergency services
Tell the user to call emergency services as soon as the obstruction is severe, ideally by having someone else call while they give back blows and thrusts. If alone, start back blows and thrusts first, then call on speaker. Anyone who received abdominal thrusts should be checked by a clinician afterward.

## Tools to consider
- `call_emergency_contact` on speaker so hands stay free.
- `start_cpr_metronome` at 110 bpm and `stop_cpr_metronome` if CPR becomes necessary.
- `get_location` and `send_location_sms` when rescuers need directions.
- `get_device_status` to check signal quickly.
