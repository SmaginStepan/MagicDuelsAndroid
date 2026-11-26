# Player avatar prompts (Midjourney)

The three free avatars the player will choose from in Options. More (paid) avatars will follow in
the same style. All are 928×1232 (3:4) on a plain white background, so they can be cut out like the
training dummy (`tools/art/cut-sprite.ps1`).

## Style

The first round used the training dummy's richer flat style (jobs 2baeb432-…, 918a3b64-…,
2c576ac4-…). You wanted the avatars flatter, less detailed, darker and more serious, so every
avatar uses this tail:

```
stern serious expression, dark and moody, full body, three-quarter view facing right, standing, centered, the whole figure fits in the frame with space around it, minimalist flat 2D fantasy game art, bold simple shapes, very little detail, limited dark muted palette with one glowing accent color, flat colors with hard-edged two-tone shading, no outlines, no texture, on a plain pure white background --ar 3:4 --s 150 --no 3d, render, realistic, photo, painterly, sketch, texture, intricate detail, gradient background, backdrop, floor, ground shadow, text, border, frame, multiple characters, cute, smile
```

## The three free avatars

| Avatar | Start of the prompt | Job | Pick | File |
|---|---|---|---|---|
| Bearded mage | `flat 2D fantasy game art of a grim old archmage with a long grey beard and a hooded deep navy robe, tall dark pointed hat, holding a gnarled black staff topped with a cold white glowing crystal,` | be35030a-deca-4420-ba44-b24ef2ad4456 | 3 | `originals/mage.png` |
| Sorceress | `flat 2D fantasy game art of a severe dark sorceress with long black hair, high-collared deep crimson and black robe, holding a slender dark staff topped with a glowing violet crystal,` | 5b588c47-df09-4d52-885d-b31d2efbbdd1 | 3 | `originals/sorceress.png` |
| Conjurer | `flat 2D fantasy game art of a dark-skinned battle conjurer in his forties with short black hair, a long high-collared dark green and black coat-robe with simple gold trim, holding a carved dark staff with a glowing amber crystal,` (+ `--no turban, headwrap, headdress, tribal patterns, ethnic patterns, jewelry`) | 1081eea9-0463-469a-a94b-c8d285cb8700 | 4 | `originals/conjurer.png` |

The conjurer was redone twice. The first versions wore a wrapped headdress and West African patterned
robes (c0375825-…); you wanted him less "oriental", dressed like the other two. A hooded-robe variant
(31c6a777-…) wasn't chosen.

## Cast animations (three per avatar)

Each avatar's image went through **Animate image → Animate manually**, **Low** motion, **without Loop**:
every clip ends on the throw, with the magic flying off to the right toward the field. In the game the
avatar then eases back into its idle pose. There were three prompts per avatar, with `{who}` = the old
wizard / the sorceress / the conjurer and `{col}` = cold white / violet / amber. Each prompt asks for
silence, because the first round's characters moved their lips as if talking:

1. `{who}, mouth closed and silent the whole time, no talking, no lip movement, slowly raises the staff while bright {col} magic gathers and swirls around the crystal, then at the very end swings the staff forward and launches a glowing {col} magic bolt that flies off to the right out of the frame, static camera, no camera movement, same plain white background`
2. `{who}, mouth closed and silent the whole time, no talking, no lip movement, draws the free hand back while a glowing {col} orb of magic grows in the palm, then at the very end hurls the orb forward and it flies off to the right out of the frame like a comet, static camera, no camera movement, same plain white background`
3. `{who}, mouth closed and silent the whole time, no talking, no lip movement, plants the staff and channels {col} magic, glowing runes spinning around the crystal and growing brighter, then at the very end points the staff to the right and fires a blazing {col} magic projectile that streaks out of the frame, static camera, no camera movement, same plain white background`

Each job gave four 5 s videos (544×720). I picked the variation that ends with the staff or hand thrust
toward the field, and skipped ones where the camera zooms, the character turns away or leaves the
frame:

| Avatar | cast1 (raise, bolt) | cast2 (orb throw) | cast3 (channel, projectile) |
|---|---|---|---|
| Mage | fb38e59e-4d2d-4431-9863-c6da7bfcfbfc, variation 3 | a3085e15-ecb4-4fd4-9c72-838feca010ab, 3 | 1d9cbac2-94d3-4a19-9c69-b16c113ace45, 3 |
| Sorceress | 254734d0-dc6f-42e5-8bb6-96ad38ffaf66, 4 | ebedaa5a-80bd-4401-90ad-f00dff5fbda3, 1 | 8848498a-0280-461a-ad98-d738204ccb8f, 3 |
| Conjurer | c8a959af-82d8-41a6-950f-e5557dd63b22, 4 | 4bc7f7fe-667a-4647-a112-c8f78192949e, 3 | 15328e03-4c1c-460a-b119-2627dd90e43a, 4 |

The first set of animations (looped, with the magic fading instead of being thrown, and lip movement)
was ddeff857-…, 7b7a6b07-…, 0963cf2a-… (mage), 513884a8-…, f268fb56-…, 1a00a95d-… (sorceress) and
939c5aaa-…, e5cbeeb8-…, c40ba1d2-… (conjurer).

- `anim/<avatar>/cast1..3/frame_00..47.png` (not in git: 100 MB; pull them again from the videos above) —
  48 frames evenly over each clip, first to last frame, taken in the browser
  (video seeked, element screenshotted).
- `tools/art/cut-avatar.ps1 -Avatar <name>` removes the white background (fill from the border, plus
  any enclosed near-white pocket of a dozen pixels or more; coloured crystal light stays; the outline is
  trimmed by two pixels and its edge darkened, so the
  video's white blur leaves no light rim) with one crop for all frames. Run with `-Height 300 -Cols 8`; it
  writes `res/drawable-nodpi/avatar_<name>.webp` (idle: cast1's first frame) and
  `avatar_<name>_cast1..3.webp` (8×6 sheets of 232×300 frames).
- In the game (`Avatars.kt`, `GameView.drawPlayerAvatar`): each cast plays one of the three at random,
  stretched over the whole cast (its speed matched to the cast's length, so the avatar moves from the tap
  until it throws as the shot leaves); neighbouring frames are blended, and afterwards the last pose
  fades back into the idle one over 0.4 s. Only the playing sheet is kept in memory.
  Crystal positions (`tipX`, `tipY`) were measured from the idle frames.

## Defeat animation (one per avatar)

Animate (no Loop, Low motion): `{who}, mouth closed, no talking, no lip movement, is struck by a powerful
magic blast, staggers backwards, {what happens to the staff / robe}, drops the staff and collapses to the
knees, ending slumped on the knees on the ground, defeated and exhausted, static camera, no camera
movement, same plain white background`.

| Avatar | Video | Pick |
|---|---|---|
| Mage | 5b4c95d7-f991-4ebb-82b1-79e69329ba13 | variation 1 |
| Sorceress | 970f3de9-6e2c-4bf9-bf0e-978bf984517d | variation 4 |
| Conjurer | 91f36c0f-dd68-46f1-aa2a-5910a3505022 | variation 1 |

- `anim/<avatar>/defeat/frame_00..47.png`, taken like the casts; `cut-avatar.ps1 -Avatar <name> -DefeatSheet`
  cuts all clips with one crop and also writes `avatar_<name>_defeat.webp` (8×6 sheet) and
  `avatar_<name>_defeated.webp` (the last frame).
- In the game: when you lose, the collapse plays over 2 s and the avatar stays in the last pose; the
  end card then shows `_defeated` (trimmed to the figure) above "Defeated".
