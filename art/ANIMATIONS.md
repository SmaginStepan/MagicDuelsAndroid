# Animated globes and projectiles (Midjourney "Animate")

All six globes and all six projectiles are animated, and the game needs their sheets in
`app/src/main/res/drawable-nodpi` (the old still images were removed: they were never drawn).

## Comets, variants and the freeze (2026-11-26)

Fire, water, air and earth shots are new "comets" with livelier, more elemental tails, every globe
and comet of those elements has **3 animation variants**, and a landed shot plays a Midjourney clip of
it **freezing into its crystal globe**.

### Variants

Sheets `orb_<el>_anim.png`, `orb_<el>_anim2.png`, `orb_<el>_anim3.png` (same for `proj_`); the game
finds up to 4 per element (`Art.MAX_VARIANTS`). Each globe and shot has a drawing-only `look` number
that picks its variant and where in the loop it starts; a shot keeps its look when it lands, so its
variant doesn't change.

### Comet images (`art/comets/originals`, `_a` = first pick, `_b` = second)

```
a magical {HEAD} flying straight horizontally to the right, with a long swirling {TRAIL} streaming behind it to the left, {GLOW}, side view, perfectly horizontal flight path, dynamic motion, floating on pure black void, magical fantasy game effect, bold readable silhouette, polished 3D render --ar 2:1 --s 150 --style raw --no {EXTRA}glass, glass sphere, crystal, bubble, hands, wizard, text, border, frame, floor, ground, diagonal
```

| Element | {HEAD} | {TRAIL} | {GLOW} | Job | a / b |
|---|---|---|---|---|---|
| Fire | blazing fireball with a white-hot core | tail of roaring flames, curling fire tongues, flying embers and sparks | glowing orange and red fire light | 648c3aad-bc41-44a8-b300-0245808a9d9b | 3 / 0 |
| Water | swirling ball of rushing water | tail of splashing water, curling waves, spray and flying droplets | deep sapphire and aqua blue glow | 5697f58b-6833-4ce5-835e-7ede1f5111c3 | 3 / 1 |
| Air | spinning vortex of white wind crackling with blue-white lightning | tail of swirling wind currents and forked electric arcs | pale silver, icy cyan and electric blue glow (+ `--no yellow, orange, fire`) | 71455feb-c8ac-4383-ad03-0a6129fa4966 | 1 / 3 |
| Earth | jagged rock boulder wrapped in glowing green energy | tail of tumbling stones, broken rock fragments, pebbles and green dust | emerald green glow | fd551411-2ebf-4ff0-8d56-d00671f2d2b7 | 3 / 0 |

### Comet animations — Animate manually, Loop

Motion prompt: `<motion>, the <head> stays fixed in the same place and size in the frame and does not
move across the frame, static camera, no camera movement, pure black background`.

| Element | Motion | Video jobs (image, motion) | Variants v1 / v2 / v3 |
|---|---|---|---|
| Fire | flames flicker and stream steadily backwards along the tail, glowing embers and sparks drift off behind it, the fireball keeps its round shape … no explosions | 6ac83553… (a, Low), b3b6a9f3… (b, Low) | 6ac8/0, b3b6/0, b3b6/3 |
| Water | the foaming water in the comet head churns and boils, the water streams in the tail twist, ripple and stream backwards, spray and droplets burst off and fly away behind it (… "the comet head keeps its shape and stays fixed …") | f25613bc… (`water_f`, Low), 0c780d32… (`water_g`, Low), e984d0f9… (`water_h`, Low) | f256/0, f256/2, 0c78/2 |
| Air | whirlwind images (`air_c` 742256cd…/2, `air_d` 6157f855…/3, `air_e` 6157…/2): "the whirlwind spins rapidly around its horizontal axis, the spiral bands of wind rotate and twist along the whole tail, wind streams whip and flutter backwards, blue-white lightning flickers inside, the comet head stays fixed …" | 4465… (air_d, Low), 6a6d… (air_e, Low), 4c48… (air_c, Low, all turned into a ball); d2dd8880… (old image, lightning bolts) | 4465/3, 6a6d/3 (-NoLock), d2dd/3 |
| Earth | stones and rock fragments in the tail tumble and spin backwards, pebbles and green dust stream off and fade away, green energy flickers around the boulder | 97b88f3b… (a, High) | 97b8/1, 97b8/3, 97b8/2 |

- **High motion** turned the fireball into blobs in all four variations (01c65a0c…); it worked for
  earth (the boulder tumbles) and for some water and air variations.
- Fire with "roar and swirl" puffed flame blobs out of the head on Low too (c51b0a72…, d97df821…);
  "flicker and stream steadily … keeps its round shape … no explosions" fixed it.
- **No globe look in comets.** The first water comets had a round swirling ball for a head, which
  looks like the water globe. Water was remade as a torrent with a pointed wave crest:
  `a magical surging torrent of water flying straight horizontally to the right, its front a pointed
  curling wave crest breaking into white foam, a long tail of splashing water, spray and flying
  droplets streaming behind it to the left, …` with `--no ball, sphere, orb, globe, bubble, round
  head, spiral, whirlpool, vortex, glass, crystal, …` (jobs d88f62e7…, 972d480a…; picks
  972d/0 = `water_c`, d88f/0 = `water_d`, 972d/3 = `water_e`). Dropped: 866d…, 0b46… (ball heads),
  ee9a/0 (diagonal boulder), 511e… (bursts mid-loop).
- **Water, third try (in the game now).** The torrents were flat strips of water, about 1.2 cells tall
  and 3.8 long with no clear head, and looked static. "A spear of rushing water" gave thin needles
  (4749bbbc…, 6b3ee941…). What worked is asking for the fire-comet shape in water:
  `a magical comet made of water flying straight horizontally to the right, its head a bullet-shaped
  mass of churning foaming water with white caps, a tapering tail of twisting water streams, spray
  and flying droplets streaming behind it to the left, compact shape like a fireball comet but made of
  water, …` with `--no ball, sphere, orb, globe, bubble, spiral, swirl, whirlpool, vortex, glass,
  crystal, wave, river, sea, needle, …` (jobs ac719562…, 17b94f13…; picks 17b9/0 = `water_f`,
  ac71/1 = `water_g`, 17b9/3 = `water_h`). In the loops, e984/1 turned the head into a ball and 0c78/1
  splashed into a wide fan; both dropped.

### Same size for every comet

`anim-sheets.ps1 -Kind projectile` measures each comet's head (the most solidly covered rows in the
columns just behind its front edge, median over the frames) and cuts a window `HeadFrac` (0.45) times
the head tall, with the head's centre half a window height in from the right edge (the shot's position
in the game) and up to 3 window heights of tail; edges are feathered. The game draws comets
`GameView.SHOT_HEIGHT` = 1.35 cells tall, so every head is about 0.6 cells across. Water v2 and v3 are
built with `-NoLock`: their "front" is spray that comes and goes, and locking on it jerked the body.

In the game the comet loops play at 16 fps (`GameView.COMET_FPS`, globes 10), and comets in the lanes
and sliding into the field shed element particles behind the head (`GameView.shedFromShots`); air
(and lightning) comets also get short lightning arcs drawn in code around the tail, about 9 a second
(`Effects.crackle`, `GameView.CRACKLE_RATE`).

**Air, third try (in the game now).** The wind comet with a soft vortex head still looked still in the
game: at 0.6 cells its swirling tail barely changes. It was remade as a whirlwind that spins around
its own axis: `a magical whirlwind comet flying straight horizontally to the right, its head a tight
spinning funnel of white wind, the whole comet a horizontal tornado twisting around its own axis with
bold spiral bands of wind and blue-white lightning crackling through it, a tapering tail of twisting
wind streams behind it to the left, compact shape like a fireball comet but made of wind, …` with
`--no ball, sphere, orb, globe, bubble, glass, crystal, clouds, yellow, orange, fire, …, vertical
tornado` (jobs 742256cd…, 6157f855…). The game also draws curved wind streaks that peel off the head
and stream back, about 18 a second (`Effects.windStreaks`, `GameView.WIND_RATE`).

**Room ahead of the head.** Sheets used to end half a window height in front of the head's centre,
which cut off heads that bulge forward (the spinning air head showed a straight edge). Now the head's
centre sits 0.75 window heights in from the right edge (`HeadInset` in the script,
`GameView.SHOT_HEAD_INSET` in the game, which must match), and the right edge is feathered.

**Earth is drawn smaller** (`-HeadFrac 0.32` for its comet sheets and its freeze, instead of 0.45):
its green dust cloud is much bigger than the boulder, so with the same head size the comet looked
far bigger than the others. Now it is about 0.75 cells tall like fire and water; its freeze starts at
the smaller head size.

### Globe variants

| Element | v1 (unchanged) | v2 | v3 |
|---|---|---|---|
| Fire | 2a46cd65…/3 | efec9723…/0 | efec9723…/2 (new job: "the flames swirl and flicker inside the orb and small embers rise inside it" + still-orb ending, Low) |
| Water | 1007a815…/0 | 1007a815…/1 | 1007a815…/3 (2 has a light background flash) |
| Earth | e7ea89b6…/0 | e7ea89b6…/2 | e7ea89b6…/3 (1 has a tinted background) |
| Air | e59ece9b…/1 | e59ece9b…/0 | e59ece9b…/3 |

### Freeze: comet → crystal globe

Animate manually from the comet image (`_a`), **Loop off**, **End Frame** = an uploaded image the size
of the comet image with the globe sprite (`art/elements/sprites`) drawn where the comet's head is
(`art/comets/freeze/<el>_end.png`, globe 1.15 × the head), Low motion:

```
the {COMET} instantly freezes: white-blue frost and ice crystals spread over it from the tail to the head, the {TAIL} crystallizes into ice and shatters into glittering ice dust that fades away, and the head hardens into a clear crystal glass orb with {INSIDE} inside, static camera, no camera movement, pure black background
```

| Element | {COMET} / {TAIL} / {INSIDE} | Video job | Pick | End frame globe (video px) |
|---|---|---|---|---|
| Fire | fireball / flaming tail / swirling fire | 04e3c498-9dd8-41ec-bfd8-4e4f838c393e | 1 | (709.9, 253.1) d 139 |
| Water | from `water_f`: "the water comet instantly freezes … the watery tail crystallizes … and the foaming head hardens into a clear crystal glass orb with swirling water inside" | a49e6fda-4850-4b6e-97e0-f5606fadd8f5 | 3 | (693.6, 233.4) d 135 |
| Air | from `air_c`: "the whirlwind comet instantly freezes … the spinning windy tail crystallizes … and the head hardens into a clear crystal glass orb with swirling white clouds inside" | d8748df9-5b7f-409c-a4db-fc16541e6658 | 2 | (686.8, 174.4) d 104 |
| Earth | flying boulder / tail of stones and green dust / a mossy rock | f1cf7c9d-19e3-4c7d-82df-b93fe2b6be1e | 0 | (672.2, 191.25) d 187.6 |

Midjourney's orb often forms away from the end frame's globe (up to a cell to the left) and swells to
1.5–2 globes before it settles. Picks are the variations where it forms closest to its place
(fire 3, water cac7…/1 and 9917…/1 and /3 drifted a cell). 16 frames from 0.5 s to the end
(`art/comets/freeze/<el>/`) → `freeze_<el>.png` with `anim-sheets.ps1 -Kind freeze -Track …`:
brightness key, one window around the end frame's globe (globe = 0.42 of the window height, window
1.8 × as wide, edges feathered), and every frame shifted and scaled so the orb stays on the globe's
spot and grows smoothly from the flying comet's head size (0.675 globes) to the globe. `-Track` lists,
per frame, how far the orb is left of the final globe and how big it is (in globes), measured by eye
from the frames with a globe-size circle drawn on them:

| Element | -Track (dx:size per frame) |
|---|---|
| Fire | 0:1.0,0:1.0,0:1.1,0:1.05,0:1.1,0:1.1,0:1.15,0:1.15,0:1.1,0:1.05,0:1.05,0:1.0,0:1.0,0:1.0,0:1.0,0:1.0 |
| Water | 0:1.0,0:1.0,0:1.05,0:1.3,0:1.5,0:1.6,0:1.6,0:1.5,0:1.35,0:1.3,0:1.15,0:1.1,0:1.0,0:1.0,0:1.0,0:1.0 |
| Air | 0:1.0,0:1.6,0:1.8,0:1.8,0:1.7,0:1.7,0:1.6,0:1.5,0:1.25,0:1.2,0:1.1,0:1.0,0:1.0,0:1.0,0:1.0,0:1.0 |
| Earth | 0:1.0,0:1.0,0.2:1.2,0.3:1.3,0.3:1.3,0.3:1.35,0.3:1.4,0.3:1.5,0.3:1.6,0.25:1.5,0.15:1.2,0.1:1.1,0.05:1.05,0:1.0,0:1.0,0:1.0 |

In the game the clip plays over `Rules.FREEZE_TIME` (0.8 s) on top of the field, mirrored for enemy
shots. The live (animated) comet is drawn over its start and fades out in the first third, so the shot
keeps moving as the frost takes over; at the end the clip fades into the real globe
(`GameView.drawFreeze`).

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
- `anim-sheets.ps1` — animation frames → sprite sheets for the game (`-Out *.webp` writes WebP).
- `to-webp.ps1` — PNGs in `res/drawable-nodpi` → lossy WebP (quality 92, alpha kept), PNGs deleted.
  Needs Google's `cwebp.exe` in `tools/art/bin` (not in git; from libwebp-…-windows-x64.zip on
  storage.googleapis.com/downloads.webmproject.org/releases/webp/).

All game images are WebP (2026-11-26): 42.9 MB of PNG became 11.6 MB, the APK 46 → 12.6 MB. In the
game each animation is one decoded sheet (GPU-only hardware bitmap) and frames are drawn straight from
it (`Art.Anim`); only the elements in play are loaded. Measured on the phone in a duel: 387 MB total
(250 MB of copied frames) before, 177 MB after.

## Hit bursts (Midjourney, 2026-11-26)

When magic hits a mage (the first piece of a match, a backfire or a globe pushed out of a full row), an
element burst plays over the mage on top of the code particles below: `hit_<element>.png`, 16 frames
(4×4, 256 px), `GameView.drawHitBursts`, 0.75 s (`HIT_TIME`), 0.85 × the player's height or 1.1 × the
smaller enemy's. Each burst is randomly turned (±25°) and mirrored. Midjourney's clips start at the blast's
peak, so the game pops it in (55% → 100% in the first tenth), keeps it spreading (+30%) and fades the last
third. Fire, water, earth and air only; lightning and shadow keep the code particles.

```
a magical {WHAT} exploding outward in all directions from the center, radial burst, front view, the burst fills the middle of the frame with empty space around it, {GLOW}, floating on pure black void, magical fantasy game impact effect, bold readable silhouette, polished 3D render --ar 1:1 --s 150 --style raw --no {EXTRA}ground, floor, smoke column, mushroom cloud, landscape, city, person, hands, wizard, text, border, frame, glass, sphere, orb, globe
```

| Element | {WHAT} | Image (pick) | Video (Animate manually, High, Loop off, End Frame = black) | Pick, frames |
|---|---|---|---|---|
| Fire | fire impact explosion, a fiery blast of roaring flames, curling fire tongues and flying embers and sparks | d6e4f186…/0 | b0233847-7941-4448-a550-02cfa0b6e0e3 | 1, 0–4.0 s |
| Water | water impact splash, a crown of splashing water, curling water sheets and flying droplets and spray | adf60671…/1 | 5ceabbc2-2abd-4d7c-8a57-fb9ff32803c2 | 3, 0–3.2 s |
| Earth | earth impact, a shattering blast of jagged rock fragments, stones, pebbles and green dust | 2cc10fda…/3 | 1898613c-7107-4a85-be4d-729d91ee406e | 2, 0–1.8 s |
| Air | wind and lightning impact, forked blue-white lightning bolts and swirling wind gusts (+ `--no yellow, orange, fire`) | 8d9576e8…/3 | 48f6c961-fc93-46da-9a62-fd0301a8c174 | 2, 0–4.0 s |

Motion prompt: `the {element} explosion blasts outward: the {pieces} fly away from the center in all
directions … and fades away into darkness, static camera, no camera movement, pure black background`.
With a black End Frame Midjourney does not blow the burst apart: it churns for 3–4 s and then shrinks
into a dot. Only the churning part is used (the dot would look like an implosion); later frames of water
and earth fill a disk, so their windows are shorter. Sheets: `anim-sheets.ps1 -Kind hit -Frames
art/hits/anim/<el> -Out res/drawable-nodpi/hit_<el>.png` (brightness key 28–110, round soft edge).

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
