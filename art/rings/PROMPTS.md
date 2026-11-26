# Cast ring prompts (Midjourney)

Rings of magic glyphs around a spell being cast: around the tapped button in your spell grid, and
around the first spell in the bot's queue. They're white, and the game tints them with the element's
colour (`Art.drawCastRing`). The whole ring shows dimly, a bright part sweeps clockwise from the top
as the cast progresses, and a spark marks its leading edge. Every cast takes the next of the three
styles:

| Style | File | Animation | Job | Pick |
|---|---|---|---|---|
| Glyphs: eight bold runes on one circle | `ring_glyphs` | stands still, the lit part breathes | 58329f8a-d4d7-49eb-b9fb-882db4158015 | 2 |
| Chain: a ring made only of runes | `ring_chain` | turns anticlockwise, the runes stream through the lit part | b195e81a-7f19-45c5-ba1d-ded4d6bb71fd | 0 |
| Seal: a band of runes between two circles, triangle marks outside | `ring_seal` | turns clockwise and closes in from 1.2× as it fills | 3d52c90f-75af-4cae-8c7e-0f51e95b42a0 | 1 |

```
{RING}, perfectly round, centered, flat straight-on top-down view, the ring fills most of the frame, large empty pure black center, made only of glowing white neon light, thick bold luminous strokes readable at small size, monochrome white glow, on pure black background, fantasy game UI element --ar 1:1 --s 100 --style raw --no metal, jewelry, bracelet, chrome, silver band, text, letters, numbers, words, hands, person, stars, nebula, solid disc, perspective, tilted, color, center symbol
```

- Glyphs: `a magic circle ring made of eight large bold glowing rune glyphs evenly spaced around a single glowing circle line`
- Chain: `a ring formed entirely by a chain of bold glowing arcane glyph symbols written end to end around the circle like a magic spell inscription`
- Seal: `a glowing magic seal ring, a wide band between two bright circle lines filled with big bold runes, with small glowing triangle marks on the outside`

A first round asked for "tiny runes" and "glowing white and pale silver" light. It gave metal rings
with glyphs far too small to see at button size. "made only of glowing white neon light, thick bold
luminous strokes readable at small size" plus `--no metal, jewelry, bracelet, chrome` fixed it.

- `originals/` — 1024×1024 Midjourney images.
- `sprites/` — 256×256 transparent sprites, cropped tight to the ring
  (`tools/art/cut-symbols.ps1 -Kind rings`).
- In the game: `res/drawable-nodpi/ring_*.webp`, drawn 1.45× the button's size (`CAST_RING_SIZE`).
