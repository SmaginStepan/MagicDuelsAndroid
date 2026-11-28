# Element button prompts (Midjourney)

Square stone spell buttons for the 5×3 spell grid (also used for the enemy's visible spell order).
Drafts showed some tilted tiles; "straight-on top-down view, flat and not tilted" and
`--no perspective, tilted` fixed that.

```
a square fantasy game spell button with rounded corners, dark polished obsidian stone tile with a thin glowing {RIM} rim, a glowing {EMBLEM} emblem engraved in the center, straight-on top-down view, flat and not tilted, the tile fills most of the frame, on pure black background, mobile game UI icon, bold readable silhouette, polished 3D render --ar 1:1 --s 150 --style raw --seed 2026 --no text, letters, numbers, hands, multiple tiles, grid, perspective, tilted
```

| Element | {RIM} | {EMBLEM} | Job | Pick |
|---|---|---|---|---|
| Fire | orange | flame | d6d0f8c5-a5d8-4b71-b63c-a63a4662dd16 | 0 |
| Water | blue | water droplet | e1156ba2-1ec6-4c2a-8391-c5be4b6e8e80 | 3 |
| Earth | green | mountain | d43ea5f3-aaf6-4cba-88c2-80a49ba0f563 | 0 |
| Air | pale cyan | swirling wind | 86133839-f355-49f0-9efa-81ad7ddd1d7b | 3 |
| Lightning | lemon yellow (+ `--no orange, fire, flames, amber`) | lightning bolt | cc68918e-6357-4760-bb7b-cc5f4012b03d | 3 |
| Shadow | violet | crescent moon | 84a1e44b-64ea-4f0c-9eb2-e6ef17ece795 | 0 |

- `originals/` — 1024×1024 Midjourney images.
- `sprites/` — 512×512, square crop around the tile with rounded transparent corners.
- In the game: `res/drawable-nodpi/btn_*.png` (256 px).
- `../projectiles-and-buttons-preview.png` — everything on the game's field colour.

## Refresh all

The same prompt with a warm sandstone gold rim (like the wall button's before its recolour), three
emblems tried: RA `golden refresh symbol of two curved arrows chasing each other in a circle`, RB
`golden circular arrow rune of swirling magic light, a single arrow curving around in a near full
circle`, RC `golden renewal sigil of three small arrows circling around a bright sparkle`. You chose
RA4 (job 6d51f094-70af-499a-9304-29fa8b88cc61, index 3): two big arrows chasing each other in a ring.

- `originals/refresh.png`; cut by `tools/art/cut-symbols.ps1 -Kind walls` like the wall button (gold
  kept) → `sprites/refresh.png` and `res/drawable-nodpi/refresh_button.webp` (`Art.refreshButton`).

### More mysterious (second round)

The same prompt with `dark mysterious arcane mood` and three new emblems: MA violet rune arrows in purple
mist (`faint violet` rim), MB `mysterious refresh sigil of two pale silver arrows circling around a dark
swirling void like an eye, faint ghostly glow and drifting mist` (`dim silver moonlight` rim), MC occult
teal-and-violet serpent arrows around a small star. You chose **MB3** (job
12aa09b5-a4bc-4ee0-99ca-8ec52fd52dce, index 2), which replaced RA4: a round dark disc with two pale
silver arrows around a swirling void.

It is round, so `cut-symbols.ps1 -Kind walls` cuts it as a disc rather than a square tile: centre
(513, 519) and rim radius 372 measured by eye on the original, fading out to radius 392 just past the
rim's glow.
