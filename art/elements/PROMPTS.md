# Element globe prompts (Midjourney)

One shared skeleton so all six globes match: a single glass orb, the element sealed inside,
centered on a pure black background (easy to cut out as a round in-game sprite).
Colours follow the game's element colours (fire orange-red, water blue, earth green,
air silver-cyan, lightning gold, shadow violet).

Step 1 — find the look with fire (`--draft` is fast and cheap), then drop `--draft` for the final.
Step 2 — run the other elements with the same `--seed` as the chosen fire image, so lighting and
framing stay the same.

## Final picks (2026-11-25)

| Element | Midjourney job | Image | Notes |
|---|---|---|---|
| Fire | 248ced43-ebf0-4497-9e95-29946f579346 | 0 | flame clearly sealed inside |
| Water | 708f65dc-746a-45cf-8b24-c0b296fcf517 | 3 | most saturated blue |
| Earth | 30477dbc-832d-4852-8993-25428d1ce1cc | 3 | big, readable island |
| Air | c26d9631-feac-408a-ab05-db779c70bd62 | 1 | pale cyan swirl |
| Lightning | 5d1ba221-492e-42cb-8d45-2dc662608cab | 3 | re-rolled in lemon yellow (gold came out orange, too close to fire) |
| Shadow | 93942281-a44e-4c28-a1f8-ac0c7ac433d7 | 1 | crescent moon visible |

Open a job at `https://www.midjourney.com/jobs/<job>?index=<image>` to see the other variations.

- `originals/` — the full 1024×1024 Midjourney images.
- `sprites/` — 512×512 round cut-outs with a transparent, soft edge, ready for the game.
- `preview.png` — all six on the game's field colour, at 200 px and 96 px.

Lightning used: `... with small crackling bright yellow lightning bolts in its center, ... electric lemon yellow and white glow, ... --no orange, fire, flames, amber, ...`

## Skeleton v2 (used for the final set)

Iteration 2 of the fire drafts showed that naming the glass shell and its highlight makes every
result read as "element sealed in crystal" (consistent top-left highlight, no floors):

```
a single crystal glass orb with {ELEMENT, small, in its center}, thick clear glass shell around it, bright white highlight on the glass at the top left, {GLOW}, centered, the orb fills most of the frame, floating on pure black void, magical fantasy game item, bold readable silhouette, polished 3D render --ar 1:1 --s 150 --style raw --seed 2026 --no hands, table, pedestal, stand, floor, ground, surface, reflection, text, border, frame, multiple orbs
```

## Skeleton v1 (first fire drafts)

```
a single glossy crystal glass orb with {ELEMENT} sealed inside, {GLOW}, bright rim light and glass reflections, centered, the orb fills most of the frame, floating on pure black void, magical fantasy game item, bold readable silhouette, polished 3D render --ar 1:1 --s 150 --style raw --no hands, table, pedestal, stand, text, border, frame, multiple orbs
```

## Elements

| Element | {ELEMENT} | {GLOW} |
|---|---|---|
| Fire | a living swirling flame | glowing orange and red fire light |
| Water | a swirling whirlpool of clear water | deep sapphire and aqua blue glow |
| Earth | a tiny floating island of green moss, stone and a small mountain | emerald green glow |
| Air | swirling white wind currents and wisps of cloud | pale silver and icy cyan glow |
| Lightning (later) | crackling lightning bolts | bright electric gold glow |
| Shadow (later) | swirling shadow smoke and a pale crescent moon | deep violet and black glow |

## Full prompts

Fire:
```
a single glossy crystal glass orb with a living swirling flame sealed inside, glowing orange and red fire light, bright rim light and glass reflections, centered, the orb fills most of the frame, floating on pure black void, magical fantasy game item, bold readable silhouette, polished 3D render --ar 1:1 --s 150 --style raw --no hands, table, pedestal, stand, text, border, frame, multiple orbs
```

Water:
```
a single glossy crystal glass orb with a swirling whirlpool of clear water sealed inside, deep sapphire and aqua blue glow, bright rim light and glass reflections, centered, the orb fills most of the frame, floating on pure black void, magical fantasy game item, bold readable silhouette, polished 3D render --ar 1:1 --s 150 --style raw --no hands, table, pedestal, stand, text, border, frame, multiple orbs
```

Earth:
```
a single glossy crystal glass orb with a tiny floating island of green moss, stone and a small mountain sealed inside, emerald green glow, bright rim light and glass reflections, centered, the orb fills most of the frame, floating on pure black void, magical fantasy game item, bold readable silhouette, polished 3D render --ar 1:1 --s 150 --style raw --no hands, table, pedestal, stand, text, border, frame, multiple orbs
```

Air:
```
a single glossy crystal glass orb with swirling white wind currents and wisps of cloud sealed inside, pale silver and icy cyan glow, bright rim light and glass reflections, centered, the orb fills most of the frame, floating on pure black void, magical fantasy game item, bold readable silhouette, polished 3D render --ar 1:1 --s 150 --style raw --no hands, table, pedestal, stand, text, border, frame, multiple orbs
```

Lightning:
```
a single glossy crystal glass orb with crackling lightning bolts sealed inside, bright electric gold glow, bright rim light and glass reflections, centered, the orb fills most of the frame, floating on pure black void, magical fantasy game item, bold readable silhouette, polished 3D render --ar 1:1 --s 150 --style raw --no hands, table, pedestal, stand, text, border, frame, multiple orbs
```

Shadow:
```
a single glossy crystal glass orb with swirling shadow smoke and a pale crescent moon sealed inside, deep violet and black glow, bright rim light and glass reflections, centered, the orb fills most of the frame, floating on pure black void, magical fantasy game item, bold readable silhouette, polished 3D render --ar 1:1 --s 150 --style raw --no hands, table, pedestal, stand, text, border, frame, multiple orbs
```

Why these choices (from the Midjourney skill's knowledge base):
- "floating on pure black void" isolates a single object far better than "on a black background".
- `--style raw` keeps the render clean; `--s 150` balances prompt adherence and polish.
- `--no … border, frame, multiple orbs` stops icon-sheet / framed layouts.
- Subject first: V7 weights the start of the prompt most.
