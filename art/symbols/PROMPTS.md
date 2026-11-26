# Element symbol prompts (Midjourney)

Glowing element emblems with no tile or button. While a spell is being cast, the new spells for the
dissolved row and column appear as these symbols in the empty spell-grid cells. They grow during the
cast and become buttons when it finishes, so each symbol must look like the emblem engraved on its
button (`../buttons/PROMPTS.md`). Compare them side by side before picking.

The first round used one general prompt. It gave realistic fire, water and wind (and a mountain
landscape for earth), which looked nothing like the clean, stylized button emblems. So fire, water,
air and earth were redone with prompts that describe the button's icon shape. Lightning and shadow
(not in play yet) still come from the general prompt.

## Fire, water, air: stylized icons

```
a single glowing stylized {ICON}, {COLOUR} glowing simple bold stylized icon shape made of glowing magical light with a soft neon glow, smooth clean edges, centered, straight-on front view, the symbol fills most of the frame, floating on pure black void, fantasy mobile game UI icon, polished 3D render --ar 1:1 --s 150 --style raw --no stone, rock texture, tile, button, frame, border, badge, circle background, ground, floor, reflection, smoke, realistic fire, splash, text, letters, multiple symbols, perspective
```

| Element | {ICON} | {COLOUR} | Job | Pick |
|---|---|---|---|---|
| Fire | flame emblem, classic flame icon with three pointed tongues and a cut-out inner flame shape in the middle | bright orange and yellow | 83ca5e67-5aca-4459-b511-e08e93b3d779 | 2 |
| Water | water droplet emblem, smooth teardrop icon with a curved crescent highlight cut out inside it | bright blue and aqua | b50a11c0-2b48-4525-a78f-b60d55f52548 | 3 |
| Air | wind spiral emblem, a thick smooth spiral curling inward with its outer stroke sweeping out to the right in a long curved tail | pale cyan and turquoise | 9c306c9f-a36b-4686-8329-946ba2907885 | 0 |

## Earth: glowing outline mountain

```
a single glowing neon green mountain emblem drawn with thick bright green glowing lines, one tall central peak with two smaller side peaks, glowing ridge lines running down the slopes, the inside of the mountain is empty black, simple bold line-art icon made of green magical light, centered, straight-on front view, the symbol fills most of the frame, floating on pure black void, mobile game UI icon --ar 1:1 --s 150 --style raw --no stone, rock texture, tile, button, frame, border, badge, ground, floor, reflection, landscape, fog, mist, crystal, text, letters, multiple symbols, perspective
```

Job 2475e15a-4c43-4105-a144-dd87bf6fc318, pick 2.

## Lightning, shadow: general prompt

```
a single glowing {EMBLEM} emblem, a magical fantasy game icon symbol made of {GLOW} light and energy, bold readable silhouette, centered, straight-on front view, the symbol fills most of the frame, floating on pure black void, mobile game UI icon, polished 3D render --ar 1:1 --s 150 --style raw --no text, letters, numbers, tile, button, stone, frame, border, circle background, badge, hands, wizard, multiple symbols, perspective
```

| Element | {EMBLEM} | {GLOW} | Job | Pick |
|---|---|---|---|---|
| Lightning | lightning bolt (+ `--no orange, fire, flames, amber`) | electric lemon yellow and white | a645b06f-c5b0-47d5-afb1-395b7cb53e94 | 3 |
| Shadow | crescent moon | deep violet shadow | 43e0aa7e-9925-4a2d-ac5a-43adeec2139c | 1 |

## Files

- `originals/` — 1024×1024 Midjourney images.
- `sprites/` — 192×192 transparent sprites made by `tools/art/cut-symbols.ps1`. Alpha comes from
  brightness, and the outer ring fades out.
- In the game: `res/drawable-nodpi/sym_*.webp` (`Art.drawSymbol`).
