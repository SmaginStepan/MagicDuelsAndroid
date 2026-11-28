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
