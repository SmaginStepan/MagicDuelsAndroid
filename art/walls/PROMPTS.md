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

## Wall on the field: arcane crystal bricks (11 variants)

You chose "crystal 1" from a round of arcane brick walls:

```
a single square section of a wall made of arcane crystal bricks, translucent glowing bricks of blue-violet magic crystal with tiny runes inside, bright light shining through the seams, straight-on front view, flat, the square fills the whole frame edge to edge, fantasy match-3 game tile, polished 3D render, on pure black background --ar 1:1 --s 150 --style raw --no text, letters, numbers, perspective, tilted, multiple tiles, background scene, hands, door, window
```

Job 9d733a8d-0b6d-4943-a69d-a75b93cf3fff, image 1. It was then varied with Midjourney's Vary: two
Subtle rounds and one Strong, 12 images. All of them except A1 are used: `originals/crystal_A2` …
`crystal_C4`, rows A = 21e816c2-a2d5-4423-8c68-aee393a2d5d5, B = 9f8e394d-ec20-4690-8485-2ea4c4d5b3a1,
C = b7470320-b2af-4d1d-a7e5-90eba5262b0a. Each new wall picks one at random (`Board.wallLook`) and keeps it
when it falls. Row A has a glowing floor under the bricks; the cut keeps the tile square from its top
edge, so only a faint glow line remains.

### Earlier walls

- A solid granite brick block (e3f7fdb5-1034-4412-becb-cab18bfbdeee, pick 2): too heavy.
- A thin cracked gold glass pane with a rune circle (f058bf34-ab5f-4077-8ad8-3504029bbaf5, pick 2,
  `originals/glass.png`), cut by brightness so it was see-through. Replaced: you didn't like how it looked.
- Rounds that weren't used: a gold energy ward (f6c0675b-…), a cracked amber slab (8624b290-…), and
  ice (ee71581b-…), stained glass (e50b894f-…), a light lattice (81e1dc52-…), amethyst (2ebb2014-…),
  and the other arcane bricks: runes (eb7cf9db-…), floating (7449f0f0-…), cracked (3a3d8325-…).

## Files

- `originals/` — 1024×1024 Midjourney images.
- `sprites/` — `tools/art/cut-symbols.ps1 -Kind walls`: the button is a 256 px square crop with rounded
  corners; each crystal wall is a 192 px square tile with slightly rounded corners.
- In the game: `res/drawable-nodpi/wall_button.webp` and `wall_block_1` … `wall_block_11.webp`
  (`Art.wallButton`, `Art.wallBlock`). A wall fills 94% of its cell. A new one grows in with a blue flash
  from the bottom of the column up, a standing one shimmers gently, and a broken one shatters into four
  spinning shards with a blue ring and the glass sound.
