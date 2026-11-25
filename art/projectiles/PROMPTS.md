# Flying projectile prompts (Midjourney)

The element itself in flight, no glass globe. Head on the right, trail to the left; the game mirrors
the image for enemy shots. Black background is turned into transparency by brightness, so glows and
trails fade smoothly onto the field.

Drafts showed many diagonal flights; "flying straight horizontally", "perfectly horizontal flight path"
and `--no diagonal` fixed that.

```
a magical {HEAD} flying straight horizontally to the right, with a short {TRAIL} streaming behind it to the left, {GLOW}, side view, perfectly horizontal flight path, floating on pure black void, magical fantasy game effect, bold readable silhouette, polished 3D render --ar 2:1 --s 150 --style raw --seed 2026 --no glass, glass sphere, crystal, bubble, hands, wizard, text, border, frame, floor, ground, diagonal
```

| Element | {HEAD} | {TRAIL} | {GLOW} | Job | Pick |
|---|---|---|---|---|---|
| Fire | blazing fireball | trail of flames and sparks | glowing orange and red fire light | 7a74e334-f75a-4943-8292-5f932226894b | 1 |
| Water | swirling ball of rushing water | trail of water spray and droplets | deep sapphire and aqua blue glow | 743c2620-ed01-4c8e-88a4-0ff0efa98ef2 | 2 |
| Earth | jagged rock shard wrapped in glowing green energy | trail of pebbles and green dust | emerald green glow | e09dd6d7-3917-45dd-9a56-6a0f205a571a | 1 |
| Air | spinning vortex of white wind | trail of wispy air currents | pale silver and icy cyan glow | f93d8f2b-cf97-4ac9-9fae-2dc180660061 | 1 |
| Lightning | crackling ball of bright yellow lightning | trail of electric sparks | electric lemon yellow and white glow (+ `--no orange, fire, flames, amber`) | 5808372b-4a5e-48f2-bc2b-a0396f2c0c93 | 0 |
| Shadow | ball of swirling dark shadow smoke | trail of violet smoke | deep violet glow | 160f5000-9e05-4c62-ae46-65dcc6c3f7f5 | 1 |

- `originals/` — 1536×768 Midjourney images.
- `sprites/` — transparent cut-outs, 384 px tall, cropped so the head is at the right edge.
- In the game: `res/drawable-nodpi/proj_*.png` (160 px tall).
