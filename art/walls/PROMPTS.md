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

## Block

```
a single square block of a magical conjured stone wall, rough grey granite bricks bound by faint glowing golden rune lines, straight-on front view, flat, the square block fills the whole frame edge to edge, solid and heavy, fantasy match-3 game tile, polished 3D render, on pure black background --ar 1:1 --s 150 --style raw --no text, letters, numbers, perspective, tilted, multiple blocks, background scene, sky, grass
```

Job e3f7fdb5-1034-4412-becb-cab18bfbdeee, pick 2 (granite bricks with glowing seams).

## Files

- `originals/` — 1024×1024 Midjourney images.
- `sprites/` — square crops with rounded corners (`tools/art/cut-symbols.ps1 -Kind walls`): the button
  256 px, the block 192 px.
- In the game: `res/drawable-nodpi/wall_button.webp` and `wall_block.webp` (`Art.wallButton`,
  `Art.wallBlock`). A wall fills 94% of its cell; a new one grows in from the bottom of the column up,
  and a broken one bursts apart with the earth explosion.
