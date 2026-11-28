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

## Dark Spikes (`spiked_`, 25 essence, 2026-11-28)

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

## Living Grove (`grove_`, 30 essence, 2026-11-28)

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
