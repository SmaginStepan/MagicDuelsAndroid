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
| Mage | (replaced by Google Flow, see the end of this file) fb38e59e-…, 3 | a3085e15-…, 3 | 1d9cbac2-…, 3 |
| Sorceress | (replaced by Google Flow, see the end of this file) 254734d0-…, 4 | ebedaa5a-…, 1 | 8848498a-…, 3 |
| Conjurer | (replaced by Google Flow, see the end of this file) c8a959af-…, 4 | 4bc7f7fe-…, 3 | 15328e03-…, 4 |

The first set of animations (looped, with the magic fading instead of being thrown, and lip movement)
was ddeff857-…, 7b7a6b07-…, 0963cf2a-… (mage), 513884a8-…, f268fb56-…, 1a00a95d-… (sorceress) and
939c5aaa-…, e5cbeeb8-…, c40ba1d2-… (conjurer).

- `anim/<avatar>/cast1..3/frame_00..47.png` (not in git: 100 MB; pull them again from the videos above) —
  48 frames evenly over each clip, first to last frame, taken in the browser
  (video seeked, element screenshotted).
- `tools/art/cut-avatar.ps1 -Avatar <name>` removes the white background (fill from the border, plus
  any enclosed near-white pocket of a dozen pixels or more, `-PocketMin`, joined through corners too;
  the sorceress is cut with `-PocketMin 4` for a one-pixel crack of background in her robe's folds; coloured crystal light stays; the outline is
  trimmed by two pixels and its edge darkened, so the
  video's white blur leaves no light rim) with one crop for all frames. Run with `-Height 300 -Cols 8`; it
  writes `res/drawable-nodpi/avatar_<name>.webp` (idle: cast1's first frame) and
  `avatar_<name>_cast1..3.webp` (8×6 sheets of 232×300 frames).
- In the game (`Avatars.kt`, `GameView.drawPlayerAvatar`): each cast plays one of the three at random,
  always lasting as long as your quickest cast (lane 5, about 3.5 s) and ending with the throw as the shot
  leaves; in a longer cast the avatar stands still until the clip starts (bots: their quickest cast, at
  most 4 s). Neighbouring frames are blended, and afterwards the last pose
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

## Sorceress, remade with Google Flow (2026-11-27)

Her three casts and her defeat were remade with Google Flow (the google-flow skill), Veo 3.1 Lite,
9:16, x2 each, silent videos, project `fcb5346a-9fc6-44dc-99bd-cb51edbe15bf`. The still: her idle
frame with the floor line removed, padded to 9:16 (720 wide, top at y = 164). Casts use it as **Start
and End** (she returns to the pose; the frames are cut where the magic leaves the frame); the defeat
uses it as Start only. She faces right, which is where Veo throws anyway, so no mirroring. Each prompt
starts `The severe dark sorceress in the crimson and black robe faces the right edge of the frame.
Silent, mouth closed the whole time, no talking, no lip movement.` and ends `Static camera, locked-off
shot, no camera movement, no zoom. The same plain pure white background, no floor, no shadow. Flat 2D
animation in the style of the image, the same flat colours and hard-edged shading.`

| Clip | Action | Pick | Frames used |
|---|---|---|---|
| cast1 | slowly raises the staff while violet magic swirls round the crystal, swings it forward to the right and launches a small violet bolt out through the right edge, then returns to the pose | A (`ANqvLOZXJUzR…`) | 0–4.9 s |
| cast2 | draws the free hand back while a violet orb grows in the palm, hurls it right like a small comet | A (`ANqvLOYWrlYu…`) | 0–5.0 s |
| cast3 | plants the staff upright, two thin rings of violet runes circle the crystal, points it right and a small dark-violet projectile with a short trail streaks out (prompt also: "deep saturated violet magic, no white glare, no bright flash"); remade at your request, x4, pick E: media `ad3f05cd-5673-4ae2-a17d-a2236bf2ac1d` (`ANqvLOYSroGH…`). The first version (`ANqvLOY1-kW4…`, a big pale rune sphere) was dropped | E | 0–4.8 s |
| defeat | struck by a blast from the right, staggers, drops the staff, collapses to her knees, head bowed | A (`ANqvLObGtwVU…`) | 2.1–6.1 s (after the blast, which is too white to cut out on a white background) |

Cut with `cut-avatar.ps1 -Avatar sorceress -DefeatSheet -PocketMin 4 -FigureMargin 0.12 -NoWhite` (crop to the
figure, magic may run past the edges). `-NoWhite` erases the pale white and light-grey parts of the
magic (a swirl's middle, an orb's rim, the inside of the rune rings, spell cores): on the dark game
background they read as flat white patches, so the magic keeps only its coloured parts and is
see-through elsewhere. Her frame has room above the head, so she is drawn with `scale = 1.16`
(`Avatars.kt`). The Midjourney frames are kept in `anim/sorceress_mj/`.

## Mage, remade with Google Flow (2026-11-27)

The same way as the sorceress (above): Veo 3.1 Lite, 9:16, x2 each, silent, project `fcb5346a-…`;
his idle still (floor line removed, padded to 9:16) as Start and End for the casts, Start only for the
defeat; he faces right. Prompts start `The grim old archmage with a long grey beard, in the hooded deep
navy robe and tall pointed hat, faces the right edge of the frame. Silent, mouth closed …`, use icy
blue magic and add `Deep saturated icy blue magic, no white glare, no bright flash.` 80 credits.

| Clip | Action | Pick | Frames used |
|---|---|---|---|
| cast1 | raises the staff while icy blue magic swirls into a sphere round the crystal, swings it forward and launches a bolt to the right | A (`ANqvLObtDcxI…`) | 0–4.6 s |
| cast2 | a frost orb grows in his outstretched hand and streaks off to the right (remade: the first pick, `ANqvLOYnkHDl…`, sent the orb back across him to the left; prompt adds "the orb flies straight away from him to the right … never comes back toward him or passes across his body") | A of the remake (`ANqvLOahWUXg…`) | 0–4.2 s |
| cast3 | a ring of runes round the crystal, then a projectile streaks right | A (`ANqvLOYqcqD9…`) | 0–4.9 s |
| defeat | struck by a blast, staggers, drops the staff, sinks to his knees, head bowed | A (`ANqvLOawbogE…`) | 2.25–6.4 s (after the blast and its motion blur) |

Cut with `cut-avatar.ps1 -Avatar mage -DefeatSheet -PocketMin 1 -FigureMargin 0.12 -NoWhite -ColorToAlpha`.
His frost magic is white-blue, so erasing white (`-NoWhite`) left holes and pale halos. `-ColorToAlpha`
un-blends every pale or bright-blue pixel outside the figure (his pose in each clip's first frame) from
the white background with the least opacity that explains it: white goes clear, pale blue becomes a
faint pure-blue glow, strong blue stays strong. (`-FillCores` was tried first and dropped.) Drawn with
`scale = 1.10`. The Midjourney frames are kept in `anim/mage_mj/`.

## Conjurer, remade with Google Flow (2026-11-27)

Like the mage: Veo 3.1 Lite, 9:16, x2 each, silent; his idle still as Start and End for the casts,
Start only for the defeat; he faces right. Prompts start `The dark-skinned battle conjurer with short
black hair, in the long high-collared dark green and black coat-robe, faces the right edge of the
frame. Silent, mouth closed …`, use amber fire magic and add `Deep saturated amber-orange magic, no
white glare, no bright flash.` 80 credits. Two clips threw to the **left** (cast 1 B, cast 2 A), so
they were left out.

| Clip | Action | Pick | Frames used |
|---|---|---|---|
| cast1 | an amber vortex at the raised crystal, swings the staff, fire shoots right | A (`ANqvLOaV8Ff_…`) | 0–4.9 s |
| cast2 | a spinning fire orb in his palm, then a burst of flame streaks right | B (`ANqvLOYkgggr…`) | 0–5.5 s |
| cast3 | a ring of amber runes round the crystal, he tilts the staff to the right and fire blasts out of the tip through the right edge (remade: the first pick, `ANqvLOZKbVIM…`, swept the fireball back across him; of 8 retries only this one never crossed his body; prompt: "a small amber fireball forms at the crystal tip, in front of him … No fire ever appears to his left, behind him or over his body") | `ANqvLOZAjngO…` | 0–3.6 s |
| defeat | thrown back by a blast, drops the staff, sinks to his knees, head bowed | A (`ANqvLOZQw_6Y…`) | 1.5–6.0 s (after the violet blast) |

Cut with `cut-avatar.ps1 -Avatar conjurer -DefeatSheet -PocketMin 1 -FigureMargin 0.12 -NoWhite
-ColorToAlpha -HotCores`. `-HotCores` adapts the colour-to-alpha step to fire: amber and orange glow
is un-blended from white too (also over his dark body), a clearly yellow pale pixel (fire's hot core)
stays solid instead of turning olive, and a half see-through pixel surrounded by fire is filled as a
glowing core. Drawn with `scale = 1.09`. The Midjourney frames are kept in `anim/conjurer_mj/`.

**Checking direction.** Before offering a clip, track where its magic is: the centre of the bright,
saturated pixels outside the pose, frame by frame. During the throw it must only move right (in
20ths of the width, e.g. `6 6 7 9 11 14 16`); a dip toward the figure's left side (`15 13 9 7 4`)
means the magic swings behind or crosses the body, and the clip is out, however good it looks in
a few stills.

## Revive (one per avatar, Google Flow, 2026-11-27)

Played in the revive popup when you spend essence to get back up (`avatar_<name>_revive.webp`).
Veo 3.1 Lite, 9:16, x2 each, silent, project `fcb5346a-…`, 60 credits. **Start** = the last frame of
the avatar's defeat clip (`anim/<name>/defeat/frame_47.png`, kneeling), **End** = its idle still, so
the clip begins exactly where the collapse ended and finishes in the idle pose. The prompt: `{Who}
kneels slumped on the ground, defeated, head bowed, and faces the right edge of the frame. Silent,
mouth closed … A soft {colour} shimmer gathers around {him/her} like a healing aura and faint motes of
{colour} light drift upward. Slowly {he/she} lifts {his/her} head, {his/her} staff rises from the
ground into {his/her} hand, glowing softly, and {he/she} rises to {his/her} feet, steady and strong
again, ending standing tall in exactly the pose of the last image, holding the staff. {He/She} stays in
place, {he/she} does not walk. The whole clip is the rising motion, {he/she} reaches the standing pose
only at the very end.` plus the usual static-camera, white-background, flat-style ending and `Deep
saturated {colour} glow, no white glare, no bright flash.`

| Avatar | Pick | Frames used | Notes |
|---|---|---|---|
| mage | M2 (`ANqvLOaK6nRG…`) | 0.4–7.3 s | blue aura; the staff appears on his right and swings over in front of his face to his left, as in the idle pose. M1 (`ANqvLOZIkdhP…`) blurred the staff to a glow mid-swing |
| sorceress | S1 (`ANqvLOYdCVjR…`) | 0.3–7.9 s | violet aura; S2 (`ANqvLOYC4e0H…`) washed the whole background pale cream near the end, so it was out |
| conjurer | C1 (`ANqvLOaN-pk4…`) | 0.3–7.3 s | fire at his feet and embers; the staff stays on his idle side. C2 (`ANqvLOa8fw3o…`) had a heavier orange halo |

The 48 frames are in `anim/<name>/revive/`. `cut-avatar.ps1` cuts `revive/` with the other clips and
the same crop (the revive takes no part in choosing it, so the other sheets come out unchanged).
For the revive only:
- its figure mask (for `-ColorToAlpha`) covers both the kneeling and the standing figure;
- its glow fades out over 24 px at the clip's sides and top instead of being cut off in a straight line
  (a crystal held near the edge);
- with `-HotCores`, the white-hot-core rules are off. They took the orange glow hugging his outline and
  the pale staff as it appears for fire cores, and painted a cream rim.

`-ReviveGlow` (mage and sorceress) also un-blends the revive's aura from the white background
whatever its colour, and inside the figure mask too when it is bright, clearly blue or violet
light. Without it the sorceress's violet aura was a flat pale-violet blob and the mage had pale cyan
rims. Their skin, robes, hair, the mage's grey-blue beard and his white crystal never match that rule.

Cut with:
- `cut-avatar.ps1 -Avatar mage … -ColorToAlpha -ReviveGlow`
- `cut-avatar.ps1 -Avatar sorceress … -NoWhite -ReviveGlow`
- the conjurer's usual command.

The sorceress's other sheets were restored from git after the re-cut, since the `-NoWhite` rules have
been tuned for the mage since she was cut. Checked on the dark game background: no white or pale
patches, and each last frame matches the idle pose.
