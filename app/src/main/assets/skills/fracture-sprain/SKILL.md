---
name: fracture-sprain
description: Use when the user reports a twisted ankle, a fall, a possible broken bone, or a joint or limb that is painful, swollen, or deformed.
---

# Fracture or sprain

## When this applies
A limb or joint injury after a fall, twist, or impact: pain, swelling, bruising, inability to bear weight, or an obvious deformity. Lay rescuers cannot reliably tell a sprain from a fracture, so treat both the same way.

## Priority order
1. Check for bleeding and treat it first.
2. Do not move the person if the head, neck, or back may be injured.
3. Rest, ice, compress, and elevate the injured area.
4. Check circulation below the injury before and after any bandage or splint.
5. Immobilize the limb if the person must move or be carried.
6. Decide whether to walk out or call for evacuation.

## Assets (progressive disclosure)
- Load `assets/steps.md` first.
- Load `assets/improvised-splint.md` only when the limb is deformed, the person cannot bear weight, or the user must move the person over rough ground.

## Emergency services
Tell the user to call emergency services when bone is visible, the limb is deformed or shortened, the injury involves the hip, thigh, or pelvis, fingers or toes below the injury are cold, pale, or numb, or the person cannot walk and cannot be safely carried.

## Tools to consider
- `get_location` and `send_location_sms` when evacuation is needed.
- `call_emergency_contact` for severe injuries.
- `start_named_timer` named "ice" for 20 minutes to limit cold exposure.
- `get_device_status` to check battery and signal before a long wait.
