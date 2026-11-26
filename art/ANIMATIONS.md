# Animated globes and projectiles (Midjourney "Animate")

All six globes and all six projectiles are animated. The game picks up an animation automatically
when its sheet exists in `app/src/main/res/drawable-nodpi` (otherwise it uses the still image).

## Water, earth, air, lightning, shadow (2026-11-25) — "Animate manually" with a motion prompt

**Animate image → Animate manually**, replace the prompt with a motion description, tick **Loop**
(End Frame), keep **Low** motion. Motion prompts used:

- Globes: `<what moves inside>, the glass orb, its rim and its white highlight stay perfectly still,
  static camera, no camera movement, pure black background`
- Projectiles: `<what moves>, the projectile stays fixed in the same place and size in the frame and
  does not move across the frame, static camera, no camera movement, pure black background`

This fixed the wandering highlight on the globes (18 of 20 variations good, versus 2 of 8 without
it). Projectiles mostly stayed in place; shadow still transformed mid-loop in all four variations.

| What | Motion (start of prompt) | Video job | Chosen |
|---|---|---|---|
| Globe water | the water whirlpool swirls slowly inside the orb | 1007a815-f191-4b0a-9b6b-66744229d847 | 0 |
| Globe earth | the moss sways gently and tiny green light motes drift inside the orb | e7ea89b6-2e7d-4596-9d82-b762d6b5899d | 0 |
| Globe air | the white wind and clouds swirl slowly inside the orb | e59ece9b-aae0-4c7a-8c87-781ca0306419 | 1 |
| Globe lightning | the yellow lightning bolts crackle and flicker inside the orb | e9331dbc-ae4e-4084-890b-1a36511a34e8 | 1 |
| Globe shadow | the violet shadow smoke swirls slowly inside the orb around the crescent moon | c5c61f3c-ffbf-4d96-b320-e0caae733964 | 1 |
| Projectile water | the water ball churns and its spray trail ripples | f0b7fd84-ce69-41a4-bd8e-47bc57259fbf | 0 |
| Projectile earth | the green energy flickers around the rock shard and the dust trail drifts | dc0ae10f-2f8f-41be-8b74-39f33c45fd7e | 3 |
| Projectile air | the wind vortex spins and its wispy trail ripples | eb53c18b-c929-4780-a38e-ece79dcbcdcf | 3 |
| Projectile lightning | the lightning ball crackles and the electric sparks flicker | cbcdddc0-2653-497f-9758-3941b72e5009 | 2 |
| Projectile shadow | the shadow smoke churns and the violet trail ripples | 5213f140-d0e7-44f3-99ec-bc6ee18c29dc | 0, ping-pong |

**Ping-pong** (shadow projectile): only the steady first 1.8 s were used — 13 frames forward
(0, 0.15 … 1.8 s) then 11 back — so the loop is still seamless and never shows the transformation.

## Projectiles: head locked, tail animated (2026-11-25)

Midjourney moves the projectile's head around inside the video (fire up to 47 px on screen, the others
4–17 px), which made shots look like they surged forward and back. `anim-sheets.ps1` now finds the head
in every frame (its front edge and vertical centre) and shifts each frame so the head stays in one place
(now under 1 px). In the game the head therefore moves exactly at the flight speed, in a straight line,
while the tail keeps its own animation. The game adds no bobbing, pulsing or arcs; backfires and
pushed-out globes fly in straight lines at constant speed too.

Fire's loop (video 4e1733fd…, variation 0) drifts until its tail is cut off by the video edge, so fire
now also uses the ping-pong of its steady first 1.8 s, like shadow.

## How they were made

1. Open the chosen image on midjourney.com → **Animate image** → **Loop → Low motion**.
   Loop makes the last frame match the first; Low motion keeps the shape, only the inside moves.
   Each animation gives 4 variations of 5 s (globe 624×624, projectile 864×432).
2. Pick the variation that stays still as a whole (see notes below).
3. Take 24 evenly spaced frames from the 5 s video (`art/<kind>/anim/<element>/frame_00..23.png`).
4. `tools/art/anim-sheets.ps1 -Element <element>` builds the sheets (6×4 grid of 24 frames):
   - `orb_<element>_anim.png` — round cut-out, 128 px frames.
   - `proj_<element>_anim.png` — brightness-keyed, one shared crop for all frames, 128 px tall.
5. The game plays them at 10 fps (`GameView.ANIM_FPS`), so a loop takes ~2.4 s; every globe starts
   at its own point in the loop so neighbours don't flicker in sync.

## Fire (2026-11-25)

| What | Midjourney video job | Chosen | Notes |
|---|---|---|---|
| Globe | 2a46cd65-afbb-4907-b935-c38454cc3cc0 | 3 | liveliest flames, glass highlight stays put |
| Globe (duplicate run) | 262ead70-6e62-4533-9a83-4f890fc798cc | — | started twice by mistake; all 4 had a wandering highlight |
| Projectile | 4e1733fd-8739-4f8d-8de5-536f32a06736 | 0 | fireball stays in place; variations 1–3 jump around the frame |

## What to watch for

- Globes: in 6 of 8 variations the white glass highlight wanders around like a moving light. Pick
  one where only the inside moves.
- Projectiles: Midjourney also moves the fireball itself (it drifts or jumps inside the frame). The
  game already moves projectiles, so pick a variation where it stays put — or use
  **Animate manually** with a motion prompt such as "flames flicker in place, the fireball stays fixed
  in the center, static camera".

## Tools (`tools/art/`)

- `cut-orbs.ps1` — globe originals → round sprites + preview.
- `resize-orbs.ps1` — sprites → 256 px game copies.
- `cut-fx.ps1` — projectile and button originals → sprites, game copies and preview.
- `anim-sheets.ps1` — animation frames → sprite sheets for the game.

## Element effects (drawn in code)

Explosions and hits are not Midjourney art: `app/src/main/java/com/an0obis/magicduel/ui/Effects.kt`
draws them with particles, sized in field cells.

| Element | Globe bursts | Magic flies to the target | On hit |
|---|---|---|---|
| Fire | flames and rising embers | comet-tailed fireball with a hot core, shedding embers | burst of flame |
| Water | splash of droplets falling with gravity, ripple rings | comet-tailed water orb (a flying droplet), dripping | splash |
| Earth | spinning rock chunks, dust, green motes | two hurled rocks per globe, shedding pebbles and dust | rocks shatter |
| Air | white-blue lightning and sparks | a jagged bolt zaps straight to the target | electric burst |
| Lightning (later) | as air, in yellow | yellow bolt | yellow electric burst |
| Shadow (later) | violet flash, dark and light violet smoke, sparks | comet-tailed violet orb, trailing smoke | smoke puff |

The comet tails follow the curved flight path and taper from a round head; they are short at
launch and stretch as the magic speeds up (`Effects.drawTravel`, positions from `GameView.sparkAt`).

The first piece of magic to arrive deals the damage (full-size impact); the rest burst smaller.
Backfires and globes pushed out of a full row also burst in their element when they hit.
