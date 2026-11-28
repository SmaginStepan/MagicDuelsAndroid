# Element packs (Midjourney)

An element pack is another look for the player's own globes, flying shots and walls. Its art has the
classic resources' names with a prefix: `spiked_orb_<el>_anim`, `spiked_proj_<el>_anim`,
`spiked_wall_block_1` …
- In the game: `ui/Packs.kt` lists the packs, and `Art.pack` picks the one in use; an element a pack has
  no art for keeps the classic look.
- Only what the player makes wears it: `Globe.owner`, `Board.wallOwner` and `Projectile.maker` say who
  made what, and the drawing passes `mine` to `Art`. The bot's shots, globes and walls and a duel's
  starting globes stay classic.
- With a pack's globes, the player's landing shot doesn't play the classic freeze clip (comet into
  crystal globe); the frost ring shows instead.
- Packs are bought in the shop for essence (GAME_RULES.md).

## Dark Spikes (`spiked_`, normal price, 2026-11-28)

Fire first, three directions each for globes (GA obsidian with thorns, GB spiked iron shell, GC dark
crystal with black spikes), walls (WA spiked obsidian bricks, WB spiked iron plates) and shots (PA
spiked obsidian shard, PB spiked iron ball), all with `--seed 2026`; you chose GA4, WA2 and PB3. Water,
earth and air were then made with the same prompts.

Globe (`--ar 1:1 --s 150 --style raw --seed 2026 --no fire, flames, orange, hands, table, pedestal,
stand, floor, ground, surface, reflection, text, border, frame, multiple orbs`; fire without the
`fire, flames, orange`):

```
a single round dark obsidian orb bristling with sharp black spikes all around it, {INSIDE} glowing brightly inside it, the {LIGHT} shining out between the spikes and through cracks in the obsidian, {GLOW}, centered, the orb fills most of the frame, floating on pure black void, dark fantasy game item, bold readable silhouette, polished 3D render
```

Shot (`--ar 2:1 --s 150 --style raw --seed 2026 --no fire, flames, orange, glass, glass sphere, bubble,
hands, wizard, text, border, frame, floor, ground, diagonal`; air also `--no yellow`):

```
a magical spiked black iron ball like a morning star engulfed in {AURA} flying straight horizontally to the right, with a long swirling {TRAIL} streaming behind it to the left, {GLOW}, side view, perfectly horizontal flight path, dynamic motion, floating on pure black void, dark fantasy game effect, bold readable silhouette, polished 3D render
```

| | Image (job / index) | Animation (job / variation) |
|---|---|---|
| Fire globe | 5f758abe-b495-4305-9a29-6c720e92f8b7 / 3 | f7d8b5fa-88e9-4f90-ad67-9d7810474238 / 2 |
| Water globe | 6e76dd96-aeb9-4ae7-a162-23fd3ac545df / 0 | 0d7da90d-26a3-4ae5-b9a2-fffc4ab1978b / 1 |
| Earth globe | 1156fdaa-1a6e-4e46-8851-2369e5464e60 / 0 | 25b9bb8c-3377-471b-934f-f52d21732691 / 1 |
| Air globe | 45bd4876-3364-40c4-a462-8110cf338975 / 2 | eeff9630-e549-40bb-aa0a-46b288e37737 / 3 |
| Fire shot | 133c9385-396e-4fca-ab93-4cf0785ab465 / 2 | c5d96384-afc5-43b0-89f9-e778b62f5805 / 3 |
| Water shot | 8faf9c32-7887-46e0-9f90-0356bc878e1e / 0 | 9b9c5cde-ee09-4a58-b2a6-3af750be1851 / 2 |
| Earth shot | ca1aa555-e6ac-4d8b-af5d-cac5ed7ee3a0 / 2 | 11b18bdd-49bd-4699-8c2d-c6cb41b4a642 / 0 |
| Air shot | d91eb51b-c806-4207-a3f5-7acf30beafc7 / 2 | 8cb5c4c0-9c31-455f-8060-f908fd195de6 / 3 |
| Wall | 0e605f00-3cff-41f4-b36a-4183b985bcf3 / 1 (`../walls/originals/spiked_wall_1.png`) | — |

The animations were made with Animate manually, **Loop** and **Low** motion. The motion prompts were:
- globes: `the {element} swirls … inside the orb and … through the cracks, the black obsidian shell
  and its spikes stay perfectly still, static camera, no camera movement, pure black background`;
- shots: `… , the spiked ball stays fixed in the same place and size in the frame and does not move
  across the frame, static camera, …`.

Picks are the variations where the shell stays still and the element stays visible:
- earth 2 turned the whole shell;
- air 0 and 2 swelled into a big white cloud;
- fire shots 0–2 threw flame bursts;
- earth shots 2 and 3 puffed grey smoke.

### Cutting (`tools/art/anim-sheets.ps1`)

The orbs and balls are dark and their backgrounds are dark tints (water, earth and air blue-grey or
teal), so neither the classic globe circle nor a black key works.
- `-Kind spikyglobe` flood-keys the background colour (from the frame's corners) away from the edges and
  protects the orb's body. The window reaches the spike tips, with a round fade.
- `-Kind spikyshot` keys the same way and protects a ball-sized circle behind the head, so the fill
  can't leak in through the dark spikes. Earth uses `-BallBack 0.5 -BallR 0.26`, because its ball sits
  far behind the pointed tip of its energy.
- The frames (24 per loop) are in `anim/spiked_<orb|proj>_<el>/`.

```
anim-sheets.ps1 -Kind spikyglobe -Frames art\packs\anim\spiked_orb_fire -Out app\src\main\res\drawable-nodpi\spiked_orb_fire_anim.webp
anim-sheets.ps1 -Kind spikyshot -Frames art\packs\anim\spiked_proj_fire -Out app\src\main\res\drawable-nodpi\spiked_proj_fire_anim.webp
```

The wall is cut by `cut-symbols.ps1 -Kind walls`, which turns `originals/<pack>_wall_*.png` into
`<pack>_wall_block_1 …`. Lightning and shadow keep the classic look for now (they aren't in play yet).

## Living Grove (`grove_`, premium price, 2026-11-28)

A "living" look: roots, vines and leaves. Fire first, three directions each, all with `--seed 2026`:
- globes: GA glass orb wrapped in roots and vines, GB cracked wooden seed pod, GC closed flower bud;
- walls: WA woven roots and moss, WB mossy stones with ivy, WC living logs with green runes;
- shots: PA glowing seed, PB thorn spear of living wood, PC ball of roots and leaves.

You chose GA3, WA3 and PB3, then W3, E3 and A3 for the globes and w4, e3 and a4 for the shots. Water,
earth and air were made with the same prompts.

Globe (`--ar 1:1 --s 150 --style raw --seed 2026 --no fire, flames, orange, hands, table, pedestal,
stand, pot, soil, tree, forest, landscape, floor, ground, surface, reflection, text, border, frame,
multiple orbs`; fire without the `fire, flames, orange`):

```
a single round clear glass orb tightly wrapped in twisting gnarled living tree roots and green vines with small fresh leaves sprouting from them, {INSIDE} glowing brightly inside the glass, the {LIGHT} shining out between the roots, {GLOW}, centered, the orb fills most of the frame, floating on pure black void, fantasy game item, lush living nature magic, bold readable silhouette, polished 3D render
```

Shot (`--ar 2:1 --s 150 --style raw --seed 2026 --no fire, flames, orange, glass, glass sphere, bubble,
hands, wizard, tree, forest, landscape, text, border, frame, floor, ground, diagonal`; air also
`--no yellow`):

```
a magical sharp thorn spear of living wood wrapped in green vines and engulfed in {AURA} flying straight horizontally to the right, its point to the right, with a long swirling tail of {TRAIL} streaming behind it to the left, {GLOW}, side view, perfectly horizontal flight path, dynamic motion, floating on pure black void, fantasy game effect, lush living nature magic, bold readable silhouette, polished 3D render
```

Wall (`--ar 1:1 --s 150 --style raw --seed 2026 --no text, letters, numbers, perspective, tilted,
multiple tiles, background scene, sky, forest, landscape, hands, door, window`):

```
a single square section of a living wall of thick tightly woven tree roots and twisted branches overgrown with green moss and small fresh leaves, faint golden green glow in the gaps between the roots, straight-on front view, flat, the square fills the whole frame edge to edge, fantasy match-3 game tile, lush living nature magic, polished 3D render, on pure black background
```

| | Image (job / index) | Animation (job / variation) |
|---|---|---|
| Fire globe | abf06081-be1c-4eb2-81eb-1148acac27b1 / 2 | a099d316-0e69-420d-9b3d-a50e03eddf15 / 1 |
| Water globe | 81829ace-b7db-4555-9774-ad424db49272 / 2 | e138d7bc-2ad8-4fc6-910d-2f7481c0f37f / 2 |
| Earth globe | 19b0d1e8-469e-4898-b2b4-501d3795f067 / 2 | d09010ad-74b1-4516-9245-e9d0ae3a78a9 / 2 |
| Air globe | 1bda42f2-eb7c-4954-b32f-6d4472c19697 / 2 | c24593cd-ca66-40b1-99aa-ff70b648de1f / 2 |
| Fire shot | c6353bb1-cc97-4e77-8eb4-b0ba083233c6 / 2 | c15837ed-8d18-4334-b6d2-56e64461825e / 3 |
| Water shot | ee8a4474-543f-498d-9da9-d8152e8331b6 / 3 | 1f2bede1-8c4e-49d9-b675-1fce7e1a314f / 3 (see below) |
| Earth shot | c960b05d-75e4-439d-be2f-2a2c216ea5c0 / 2 | b2e479fc-3e67-49af-94ad-1fc6aa091f3f / 3 |
| Air shot | 3bd520af-1c6a-4bf2-856d-70dcd5236133 / 3 | 65af7cf2-84a7-4efc-a083-43849ae30869 / 0 |
| Wall | b053f5d6-9fb1-499e-94b5-d8adc204856d / 2 (`../walls/originals/grove_wall_1.png`) | — |

The animations were made with Animate manually, **Loop** and **Low** motion. The motion prompts were:
- globes: `the {element} swirls … inside the glass orb and … between the roots, the glass orb and the
  roots, vines and leaves around it stay perfectly still, static camera, no camera movement, pure
  black background`;
- shots: `… , the wooden thorn spear stays fixed in the same place and size in the frame and does not
  move across the frame, static camera, …`.

Picks are the variations where the roots stay still and the flames stay inside the glass (fire 2 threw
flames out of the top). Every variation of the first water shot animation (w4, with its curled wave)
spun the spear around. Two more jobs had firmer prompts:
- `only the water ripples and splashes in the wave trail behind the spear, the wooden thorn spear stays
  perfectly level and still in the same place and size, it does not rotate, spin, turn or move at
  all, …`;
- `gentle shimmering water flowing along the curled wave trail, the thorn spear is completely frozen in
  place pointing right, no rotation, no spinning, no flipping, …` (18dbcd3d-6b0e-4121-b26f-ba7a34de9904).

Most of their variations still tilted the spear. Variation 3 of the first job keeps it at one angle:
the wave stretches along the spear and curls back. (Fast hours ran out before w1 or w2, with their
straight trails, could be animated.)

### Cutting

- Globes: `anim-sheets.ps1 -Kind glassglobe` keeps the round glass orb whole: a circle measured by eye
  on the frames (`-Cx -Cy -D`, a little inside the glass). Outside it the background is keyed against
  its own colour at that distance from the orb's centre (each ring's median), because it glows softly
  around the orb and a single colour left a dark disc. The roots reaching out of the orb stay; the window
  is 1.22 × the orb's radius, so the orb is as big as a classic globe and the longest sprouts are
  trimmed.

  | | Cx | Cy | D |
  |---|---|---|---|
  | fire | 296 | 310 | 384 |
  | water | 292 | 310 | 372 |
  | earth | 290 | 315 | 382 |
  | air | 296 | 312 | 376 |

- Shots: Midjourney drew the spears rising to the right, so `tools/art/rotate-frames.ps1` turns the
  frames level first (fire 15°, water 18°, earth 21°, air 18° clockwise); the frames in
  `anim/grove_proj_<el>/` are the turned ones. They are on black, so the classic `-Kind projectile`
  brightness key cuts them (the flood key of `spikyshot` left a dark smudge around the flames). Water
  needs `-HeadDensity 0.1`: next to its big wave the thin spear tip wasn't counted as the head, so the
  head lock followed the water instead. Water and air glow faintly around the spear in the video, which
  the default key kept as a pale haze: water is cut with `-KeyLo 18`, air with `-KeyLo 26` (the default
  is 8).

```
anim-sheets.ps1 -Kind glassglobe -Frames art\packs\anim\grove_orb_fire -Out app\src\main\res\drawable-nodpi\grove_orb_fire_anim.webp -Cx 296 -Cy 310 -D 384
rotate-frames.ps1 -Frames <video frames> -Out art\packs\anim\grove_proj_fire -Angle 15
anim-sheets.ps1 -Kind projectile -Frames art\packs\anim\grove_proj_fire -Out app\src\main\res\drawable-nodpi\grove_proj_fire_anim.webp
```

The wall is cut by `cut-symbols.ps1 -Kind walls` (`originals/grove_wall_1.png` → `grove_wall_block_1`).

## Plush Toys (`plush_`, premium price, 2026-11-28)

Toys and childhood. Fire first, three directions each, all with `--seed 2026`:
- globes: GA felt ball with a flame patch, GB round plush creature with button eyes, GC crocheted ball;
- walls: WA wooden toy blocks, WB quilted patchwork cushions, WC chunky knitted wool;
- shots: PA felt ball comet, PB plush toy rocket, PC plush shooting star.

You chose GB1, WB1 and PB2, then W1, E1 and A2 for the creatures and w2, e2 and a2 for the rockets. The
images were made in **Relax** mode, after the Standard plan's Fast hours ran out.

Creature (`--ar 1:1 --s 150 --style raw --seed 2026 --no fire, flames, orange, hands, child, person,
table, shelf, floor, ground, surface, reflection, text, letters, border, frame, multiple objects`; fire
without the `fire, flames, orange`, air also `--no yellow`):

```
a single round cute plush creature like a soft stuffed toy ball made of fluffy {COLOURS} fabric, black button eyes and a small stitched smile, {TUFTS} on its head, {GLOW}, centered, it fills most of the frame, floating on pure black void, cute toy game item, soft cozy childhood, bold readable silhouette, polished 3D render, soft studio lighting
```

| | {COLOURS} | {TUFTS} | {GLOW} |
|---|---|---|---|
| fire | red and orange | felt flame tufts | warm orange glow |
| water | blue and aqua | felt water drop tufts | soft aqua blue glow |
| earth | green and moss green | a little felt leaf sprout and felt pebbles | soft emerald green glow |
| air | pale white and light sky blue | fluffy felt cloud and wind swirl tufts | soft pale silver and icy cyan glow |

Rocket (`--ar 2:1 --s 150 --style raw --seed 2026 --no fire, flames, orange, hands, child, person,
wizard, text, letters, border, frame, floor, ground, diagonal`; the same colours and glows; trails:
fire `felt flames and puffy cotton smoke`, water `splashing felt water drops, bubbles and puffy cotton
spray`, earth `tumbling felt leaves, little felt pebbles and puffy green dust`, air `swirling felt wind
ribbons and puffy cotton clouds`):

```
a cute plush toy rocket sewn from soft {COLOURS} fabric with stitched seams and a round porthole button, flying straight horizontally to the right, its nose to the right, with a long trail of {TRAIL} streaming behind it to the left, {GLOW}, side view, perfectly horizontal flight path, dynamic motion, floating on pure black void, cute toy game effect, soft cozy childhood, bold readable silhouette, polished 3D render
```

Wall (`--ar 1:1 --s 150 --style raw --seed 2026 --no text, letters, numbers, perspective, tilted,
multiple tiles, background scene, room, hands, child, door, window`):

```
a single square section of a soft plush wall of quilted patchwork cushions in pastel colors, stitched seams and big sewn buttons, straight-on front view, flat, the square fills the whole frame edge to edge, cute toy match-3 game tile, soft cozy childhood, polished 3D render, on pure black background
```

| | Image (job / index) | Animation (job / variation) |
|---|---|---|
| Fire creature | be24eff9-6f30-4292-9ff3-5ccb35e08b52 / 0 | 1c6f3d65-da24-4430-aa4c-bca145aea21d / 2 |
| Water creature | 97603ed2-3c98-4e39-b0d8-e83bae064002 / 0 | b420ec26-89bf-4337-be7b-b61353e5f34b / 1 |
| Earth creature | 5e6944df-87de-4630-bd80-242bf0be4076 / 0 | 802edd0e-a686-41b6-8b40-3cf79e6425f6 / 1 |
| Air creature | 493deefe-1877-471d-a360-091449dffd48 / 1 | d7f3cb90-7739-4bad-a261-3417cd08a4d6 / 2 |
| Fire rocket | e1e23921-0aa7-41eb-96dd-0e4f4bc31452 / 1 | 0a134874-eb81-412c-8c41-f088e760bbd1 / 1 |
| Water rocket | 61d7ce0e-7176-47a9-a1a3-fd41576e2456 / 1 | 0a6d8e0e-d5c5-4a1f-9d81-90abbdf6aead / 1 |
| Earth rocket | af6879bb-3bc4-4fc2-b266-87a1d05c6a11 / 1 | 07fbb135-106e-4e1b-a9d7-aeec443681ef / 2 |
| Air rocket | 841ab82a-8fe0-4464-8b4c-f53453ce6416 / 1 | c752c7a1-d359-4291-989e-d1d907ae45d9 / 1 |
| Wall | a860ec13-2022-49ee-8955-95ef6d1eba23 / 0 (`../walls/originals/plush_wall_1.png`) | — |

The animations were made with Animate manually, **Loop** and **Low** motion, as SD videos in Relax mode
(Pro plan). Relax runs one video job at a time with at most three queued: jobs sent beyond that vanish
without a message, so send them one by one and watch the "queued jobs" count. The motion prompts were:
- creatures: `the round plush creature gently squishes and bounces in place like a soft toy, its fluffy
  fur sways softly and its {felt flame tufts flicker | water drop tufts wobble | cloud swirl drifts}, it
  stays in the same place and size in the frame, static camera, no camera movement, pure black
  background`;
- earth creature (a second job: in the first one the sprout melted and the moss came and went): `the
  round plush creature gently squishes and bounces in place like a soft toy and its fluffy fur sways
  softly, the little leaf sprout and the mossy patch with pebbles stay attached and unchanged on its
  head, it keeps facing the camera, …`;
- rockets: `the {exhaust / trail} {flickers / ripples / flutters}, the plush rocket stays level and fixed
  in the same place and size in the frame, it does not rotate, turn or move across the frame, …`.

Picks are the variations that keep facing the camera and keep their tufts. Rejected: fire 0 grew little
legs, fire 3 lost its flames for a moment, the other water and air variations turned to show their
backs, and earth rockets 1 and 3 rolled over.

### Cutting

- Creatures: `anim-sheets.ps1 -Kind plushglobe` flood-keys the dark background from the frame's edges
  (the fur is far from its colour), then cuts every frame with one square window around everything the
  loop shows, so the bounce stays inside. Loose bits (under 1% of the creature) and specks outside that
  window are cleared. Earth needs
  `-Protect 0.92`: its moss is as dark as the background, so the key would eat into it; its body (an
  ellipse found in every frame from its width and its bottom) is kept out of the key.
- Rockets: they fly level already, so the classic `-Kind projectile` brightness key cuts them as they are.

```
anim-sheets.ps1 -Kind plushglobe -Frames art\packs\anim\plush_orb_earth -Out app\src\main\res\drawable-nodpi\plush_orb_earth_anim.webp -Protect 0.92
anim-sheets.ps1 -Kind projectile -Frames art\packs\anim\plush_proj_fire -Out app\src\main\res\drawable-nodpi\plush_proj_fire_anim.webp
```

The wall is cut by `cut-symbols.ps1 -Kind walls` (`originals/plush_wall_1.png` → `plush_wall_block_1`).

## Game Stream (`stream_`, premium price, 2026-11-28)

Gaming and streaming: controllers, likes, stars. Fire first, three directions each, all with `--seed 2026`:
- globes: GA joystick ball top, GB round arcade push button, GC glossy reaction bubble with a heart;
- walls: WA stacked controllers, WB backlit RGB keycaps, WC neon hex panels with stars and hearts;
- shots: PA rating star, PB like-heart, PC pixel comet (it came out as a plain fireball).

You chose GB2, WB2 and PB1, then W1 for water, and w3, e4 and a4 for the hearts. The first earth and air
buttons were seen too much from the side; new ones, "seen straight from the front, perfectly frontal flat
view looking directly at its face, a perfect circle" with `--no side view, tilted, perspective, angle`,
gave G1 (earth) and A4 (air). (One earth set had a cannabis-like leaf; avoid those.)

Button (`--ar 1:1 --s 150 --style raw --seed 2026 --no fire, flames, orange, hands, person, table, desk,
floor, ground, surface, reflection, text, letters, logo, brand, border, frame, multiple objects`; fire
without the `fire, flames, orange`, air also `--no yellow`):

```
a single big round glossy arcade push button seen from the front, a domed {COLOURS} button with a glowing {ICON} icon in it, a chrome ring around it, {GLOW}, centered, it fills most of the frame, floating on pure black void, gaming and streaming game item, glossy, bold readable silhouette, polished 3D render
```

Icons: flame, water droplet, leaf, wind swirl. Heart (`--ar 2:1 …`):

```
a glossy {HEART} 3D like heart icon flying straight horizontally to the right, with a long trail of {TRAIL} streaming behind it to the left, {GLOW}, side view, perfectly horizontal flight path, dynamic motion, floating on pure black void, gaming and streaming game effect, bold readable silhouette, polished 3D render
```

(fire: `a glossy red 3D like heart icon wrapped in flames … tiny hearts, thumbs-up sparkles and confetti`.)
Wall: `a single square section of a wall made of glowing RGB mechanical keyboard keys in neat rows, purple
and blue backlight between the keycaps, straight-on front view, flat, the square fills the whole frame
edge to edge, gaming and streaming match-3 game tile, polished 3D render, on pure black background`.

| | Image (job / index) | Animation (job / variation) |
|---|---|---|
| Fire button | b2dd7db3-749a-4930-b80d-adddec2dd78a / 1 | 40fa6249-b5e6-4ac6-bae4-69b2bf8e5772 / 0 |
| Water button | 81fc2623-6ffc-4a3e-9983-41398b46c111 / 0 | c6a7a484-b4a5-4516-beb9-67685f86ea80 / 0 |
| Earth button | ca1f0160-8093-4e23-8651-938016cff7bd / 0 | 8a1950f0-b1bb-4f2d-8902-d50dbf785bd7 / 1 |
| Air button | e4649e97-4526-479a-aaa5-5ec5502d3814 / 3 | 481c028b-711c-452c-8a6b-dbf306a6c3fb / 1 |
| Fire heart | 88920ce5-e456-4be0-8b60-a79c84e489f8 / 0 | 5e9d0956-5326-4b89-849f-6e022db51778 / 3 |
| Water heart | a7ece9c6-86f3-4d03-ade4-6103d16807d3 / 2 | 20a39746-f915-496d-a24e-f9647216f6cf / 3 |
| Earth heart | f8055fdb-24a8-4bf0-81fc-557461876591 / 3 | 6f0b3cf1-e077-49a7-8702-747f07234471 / 1 |
| Air heart | 3496a7ea-59b2-421c-9159-74cf2a6e3626 / 3 | e4f46efe-c95f-4215-9a40-b96de7ae5d0d / 2 |
| Wall | 2698d4a2-2bbd-43ec-90c8-04884aa5431c / 1 (`../walls/originals/stream_wall_1.png`) | — |

Animations: Loop, Low motion; the buttons in Relax, the hearts in Fast. Prompts:
- buttons: `the glowing {icon} icon inside the button pulses and shimmers softly, light glints sweep across
  the glossy dome and the neon rim glows brighter and dimmer, the button stays perfectly still in the
  same place and size, static camera, …` (fire 1–3 pressed the button in and lost the flame);
- hearts: `the heart pulses gently like a heartbeat and the trail of {trail} flickers and streams, the
  heart stays level and fixed in the same place and size in the frame, it does not rotate, …`.

Cutting: buttons with `-Kind plushglobe -Protect 0.9` (dark glass inside the chrome ring would otherwise
be keyed), hearts with `-Kind projectile` (fire `-HeadFrac 0.63`, air `-HeadFrac 0.52`: their glow counts
as head, so at the default 0.45 their hearts came out smaller than the others); the wall with
`cut-symbols.ps1 -Kind walls`.

## Clockwork (`mech_`, normal price, 2026-11-28)

Gears and mech, light themed: every prompt adds `, bright light color scheme of clean white enamel,
polished silver and shiny brass` and `--no rust, dirt`. Fire first, three directions each, `--seed 2026`:
- globes: GA clockwork orb with a flame window, GB white mech reactor core, GC sphere of gears;
- walls: WA interlocking gears, WB white armor plates, WC riveted plate with a big gear;
- shots: PA flaming cog, PB white mech missile, PC drill bit.

You chose GB3, WA3 and PA4, then A2 for air and w1, e4 and a2 for the cogs. The first water and earth
cores were turned too far; new ones, `seen straight from the front, the glowing … reactor lens exactly in
the center of the sphere facing the camera, symmetrical` with `--no side view, rotated, turned, tilted,
perspective, angle`, gave C2 (water) and B2 (earth).

Core:

```
a single round mech reactor core sphere of white armor panels with fine panel lines and small bolts, a glowing {LENS} reactor lens in the center, bright light color scheme of clean white enamel, polished silver and shiny brass, {GLOW}, centered, it fills most of the frame, floating on pure black void, mechanical game item, bold readable silhouette, polished 3D render
```

Cog (`--ar 2:1`):

```
a spinning polished silver cog gear wrapped in {AURA} flying straight horizontally to the right, with a long trail of {TRAIL} and tiny flying gears streaming behind it to the left, bright light color scheme of clean white enamel, polished silver and shiny brass, {GLOW}, side view, perfectly horizontal flight path, dynamic motion, floating on pure black void, mechanical game effect, bold readable silhouette, polished 3D render
```

Wall: `a single square section of a wall made of large interlocking gears and cogs set flat side by side, …,
straight-on front view, flat, the square fills the whole frame edge to edge, mechanical match-3 game tile,
polished 3D render, on pure black background`.

| | Image (job / index) | Animation (job / variation) |
|---|---|---|
| Fire core | f506ef37-ab53-4197-82dd-8184362e79f7 / 2 | 664b6b82-3440-4533-980f-f693fed60b05 / 1 |
| Water core | 8a359db3-0fa3-456f-a3b0-87e49f949768 / 1 | 33d6f4a1-802c-468c-bee1-4af0f4d69e3f / 1 |
| Earth core | eec39b82-db4b-4b18-ab1c-ee1381425de9 / 1 | 3a982ade-a056-4923-8c52-0b2337c096ea / 2 |
| Air core | 33e396f3-45bd-45d3-a5fe-3af32417cf54 / 1 | 3b5c8eb8-17e1-4ecf-952f-3df1bae07f35 / 3 |
| Fire cog | d8c5a0b7-c4a6-4ef3-867f-aad757ce79ba / 3 | f1434808-8834-4204-afdb-899f0b2cae16 / 0 |
| Water cog | 1f45eda7-fcd0-4c3f-84f7-396091a8ece3 / 0 | 80c71f56-0b3c-4d59-a355-716d2d330103 / 1 |
| Earth cog | 60b7684e-d969-4403-8f77-c22b09d1ea0f / 3 | 2419cc0d-1192-43da-98cc-980ce9cce34a / 1 |
| Air cog | a1804136-467e-439f-857b-2ff17a8712bb / 1 | dffda622-5f1b-46d4-baed-1786167da3f9 / 0 |
| Wall | 33fa8dbc-c2e5-48c7-9ae6-4755ab056d24 / 2 (`../walls/originals/mech_wall_1.png`) | — |

Animations in Fast, Loop, Low motion: cores `the reactor lens glows and pulses softly with swirling {element}
light inside it and small lights on the panels blink, the white armored sphere stays perfectly still …`
(the rejected ones turned or opened their panels); cogs `the cog gear spins in place and the trail of
{trail} streams and flickers, the cog stays level and fixed …` (the fire cog still travels about 180 px
in its video; the head lock holds it). Cut with `-Kind plushglobe -Protect 0.9` (cores) and
`-Kind projectile` (cogs); the wall with `cut-symbols.ps1 -Kind walls`.
