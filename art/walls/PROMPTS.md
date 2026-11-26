# Wall prompts (Midjourney)

The wall button above each field column, and the wall block that fills a field cell (see GAME_RULES.md,
Walls).

## Button

The same prompt as the spell buttons (`../buttons/PROMPTS.md`), with a sandstone gold rim and a brick
wall emblem. Pick 0 drew a battlement wall, which reads as a wall even at button size.

```
a square fantasy game spell button with rounded corners, dark polished obsidian stone tile with a thin glowing warm sandstone gold rim, a glowing brick wall emblem engraved in the center, straight-on top-down view, flat and not tilted, the tile fills most of the frame, on pure black background, mobile game UI icon, bold readable silhouette, polished 3D render --ar 1:1 --s 150 --style raw --seed 2026 --no text, letters, numbers, hands, multiple tiles, grid, perspective, tilted
```

Job b032b233-6990-44c6-87e5-88d53457240d, pick 0. Its background is dark grey (about 30), not black,
so the cut uses a brightness threshold of 60.

## Wall on the field: a pane of magic glass

```
a single square pane of a magical barrier made of thin translucent golden glass, glowing arcane runes etched into it, fine hairline cracks running across the surface, delicate and fragile, soft inner light, straight-on front view, flat, the square fills the whole frame edge to edge, fantasy match-3 game tile, on pure black background --ar 1:1 --s 150 --style raw --no text, letters, numbers, perspective, tilted, multiple, frame, border, background scene, stone bricks, rock
```

Job f058bf34-ab5f-4077-8ad8-3504029bbaf5, pick 2: a thin cracked glass pane with a glowing rune circle.
It is cut by brightness (Lo 14, Hi 120), so the dark glass is see-through and the field shows through it.

The first wall was a solid granite brick block (job e3f7fdb5-1034-4412-becb-cab18bfbdeee, pick 2). It
felt too heavy, so it was replaced with something more magical and fragile. Two other tries didn't
make it: a golden energy ward with a honeycomb pattern (f6c0675b-6b97-48b5-ad20-d0d62db068c4), and a
cracked amber crystal slab (8624b290-bfb2-421c-a452-f40b4f25a4b3).

## Files

- `originals/` — 1024×1024 Midjourney images.
- `sprites/` — `tools/art/cut-symbols.ps1 -Kind walls -Lo 14 -Hi 120`: the button is a 256 px square
  crop with rounded corners; the glass is 192 px, keyed by brightness and cropped tight.
- In the game: `res/drawable-nodpi/wall_button.webp` and `wall_block.webp` (`Art.wallButton`,
  `Art.wallBlock`). A wall fills 94% of its cell. A new one grows in with a flash from the bottom of the
  column up, a standing one shimmers gently, and a broken one shatters into four spinning shards with a
  gold ring and the glass sound.
