---
name: bleeding
description: Use when the user reports a cut, wound, or bleeding of any severity, including bleeding that will not stop.
---

# Bleeding control

## When this applies
Any external bleeding from a cut, laceration, puncture, or scrape on the trail. Most bleeding stops with firm direct pressure. Life-threatening bleeding is spurting, pooling on the ground, or soaking through dressings.

## Priority order
1. Protect yourself: gloves or a plastic bag over your hands if available.
2. Expose the wound and apply firm, direct pressure.
3. Hold pressure without peeking for at least ten minutes.
4. Add a pressure dressing so hands are free.
5. Escalate to a tourniquet only for life-threatening limb bleeding that pressure does not control.

## Assets (progressive disclosure)
- Load `assets/steps.md` first and guide the user through direct pressure.
- Load `assets/tourniquet.md` only when the user reports that bleeding from an arm or leg is still heavy after firm pressure, is spurting, or the wound is too large or the limb too damaged to hold pressure.

## Emergency services
Tell the user to call emergency services immediately for spurting or pooling blood, blood soaking through dressings, a deep wound on the neck, chest, or abdomen, any amputation, or signs of shock: pale skin, confusion, weak fast pulse.

## Tools to consider
- `start_named_timer` named "pressure" for 10 minutes so the user does not lift the dressing early.
- `start_named_timer` named "tourniquet" when a tourniquet is applied.
- `call_emergency_contact` for severe bleeding.
- `get_location` and `send_location_sms` so rescuers can find the user.
- `get_device_status` to check signal before relying on a call.
