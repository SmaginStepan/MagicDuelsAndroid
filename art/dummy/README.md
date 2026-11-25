# Training dummy sprite (Midjourney)

Used for both training dummies (levels 1 and 2). Picked by you from three rounds of options:
3D (A–C), flat 2D (D–F) and flat fantasy art (G–I) — **G4**.

## Still image (G4)

Job `e24297a8-cf2f-4d26-ad42-8b55a4c102dc`, image 4 (index 3) → `originals/target_dummy_G4.png` (928×1232).

```
flat 2D fantasy game art of an enchanted training dummy for apprentice wizards, round tan head with two dark brown X eyes and a small floppy purple wizard hat, tan sack-shaped body with a red and white bullseye target on the chest, faint glowing blue runes stitched around the target, brown stick arms with small golden tufts at the ends, a tattered purple scarf, thin carved wooden post on a small round base with a glowing rune, a few tiny golden sparkles floating around, full body, front view, centered, the dummy fills most of the frame, elegant stylized shapes, rich warm colors, flat colors with soft two-tone shading, no outlines, on a plain pure white background --ar 3:4 --s 250 --no 3d, render, realistic, photo, gradient background, backdrop, floor, ground shadow, text, border, frame, multiple dummies
```

## Hit animation

G4 → **Animate image → Animate manually**, Loop, **Low** motion:

```
the training dummy is hit by a magic blast: it jolts backwards and wobbles on its post, its pointed purple hat bounces and a few bits of straw and golden sparkles fly off, then it settles back into its original pose, static camera, no camera movement, same plain light background
```

Video job `41cb5afc-32ef-4b8d-be20-3c94ac9edf89`, variation **1** (544×720, 5 s). Only 0.3–1.6 s is used:
a burst of straw hits from the left, the dummy jolts, straw scatters and it settles. After that Midjourney
starts turning the dummy around. (The High-motion run, `b9f0acc5-…`, spun or flipped the dummy in all
four variations.)

- `anim/idle.png` — video frame at 0 s (the idle pose, so idle and hit match exactly).
- `anim/frame_00..15.png` — 16 frames evenly from 0.3 s to 1.6 s.
- `tools/art/cut-sprite.ps1` removes the light background (flood fill from the border with a soft
  edge; pale haze and straw-coloured pixels let the fill through, saturated colours stop it, so the
  target's white rings stay), uses one crop for all frames and fades the last 4 frames back into the
  idle pose. Output: `res/drawable-nodpi/dummy_idle.png` and `dummy_hit.png` (4×4 grid, 254×300 frames).

In the game the hit animation plays at 20 fps (0.8 s) each time the dummy takes damage
(`GameView.DUMMY_HIT_FPS`), restarting if it is hit again; size is `DUMMY_SPRITE_SCALE`.
