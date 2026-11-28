# Enemy avatars: 50 levels of cybernetic mages

Every mage opponent gets a Midjourney avatar in the same flat, dark style as the player avatars
(`../avatars/PROMPTS.md`), facing left toward the field, with **mechanical cybernetic parts** that grow
level by level. Each avatar has one idle image, **one cast animation** (ends throwing magic to the left)
and a **defeated image** (the last frame of a collapse animation, so it is exactly the same character),
shown on the victory card. The two training dummies get a defeated image only.

Mage *k* (the k-th mage, game level k + 3) uses avatar `bot_kk`. The first ones must look weak; the
cybernetics, size, armour and magic grow steadily, so level 50 is a near-machine titan.

## Stages

| Bots | Stage | Cybernetics | Magic, look |
|---|---|---|---|
| 1–10 | Apprentices | one small brass part: a gauntlet, an eye lens, a temple implant, one clockwork forearm | plain worn robes, short staffs, small crystals |
| 11–20 | Adepts | mechanical limbs, steam vents, brass shoulder plates, glowing rune circuitry on the skin | longer robes with metal trim, bigger crystals, floating sparks |
| 21–30 | Sorcerers, warlocks | half-machine bodies, armoured chests, back-mounted arcane engines, small hovering drone orbs | dark armoured robes, staffs with rotating rings |
| 31–40 | Archmages | exosuits, a glowing reactor core in the chest, extra mechanical arms, levitation | crowns of floating blades or gears, large auras |
| 41–50 | Grand archmages | colossal techno-sorcerers, almost fully machine, halos of gears, wings of metal and light | cosmic energy, the most dramatic silhouettes |

Within a stage, vary age, gender, skin tone and the glowing accent colour (teal, amber, crimson,
violet, ice blue, acid green), so neighbouring levels don't look alike. Describe looks, not
ethnicities' clothing.

## Prompt pattern

```
flat 2D fantasy game art of {CHARACTER with its CYBERNETIC PARTS}, holding {STAFF}, stern serious expression, dark and moody, full body, three-quarter view facing left, standing, centered, the whole figure fits in the frame with space around it, minimalist flat 2D fantasy game art, bold simple shapes, very little detail, limited dark muted palette with one glowing accent color, flat colors with hard-edged two-tone shading, no outlines, no texture, on a plain pure white background --ar 3:4 --s 150 --no 3d, render, realistic, photo, painterly, sketch, texture, intricate detail, gradient background, backdrop, floor, ground shadow, text, border, frame, multiple characters, cute, smile
```

**From bot_03 on, the background is pure black, not white** (the user's choice: the videos are made
on black). A Midjourney video starts from its image, so the image is on black too: the pattern ends
`…, a soft cool rim light outlining the figure against the background, on a plain pure black
background` and the `--no` list adds `white background`. The rim light keeps the dark robes from
melting into the black, and `cut-avatar.ps1 -BlackBg` keys black instead of white.

Animate (no Loop, Low motion), always with `mouth closed and silent the whole time, no talking, no lip
movement` and `static camera, no camera movement, same plain black background` (white for bot_01 and
bot_02):

- **Cast:** `{who}, …, gathers {colour} magic …, then at the very end thrusts the staff forward to the left and launches a glowing {colour} magic bolt that flies off to the left out of the frame`
- **Defeat:** `{who}, mouth closed, no talking, is struck by magic, staggers, {cybernetics} spark and smoke, drops the staff and collapses to the knees, ending slumped on the knees on the ground, defeated and exhausted`

## Made so far

| Bot | Level | Look | Image job (pick) | Cast video | Defeat video |
|---|---|---|---|---|---|
| bot_01 | 3 | **Novice Cogwin**: young apprentice, worn grey-brown robe, one clockwork brass forearm, teal temple implant, short staff with a teal crystal | b1e4c670-70b7-48b7-a57d-0d0aea01b962 (2) | 08df9a3c-ce8f-4fc0-b8e2-4030f8cca462 (3) | 0049724e-0ff6-49c2-9079-596cdf26d08c (2), last frame |
| bot_02 | 4 | **Novice Rustjaw**: lean shaved-head apprentice warlock, rust-red hooded robe, brass jaw plate, amber eye lens, brass clockwork knee brace, crooked staff with an amber crystal | 02481782-93be-4002-b8ad-024d558f1ad0 (1) | **Google Flow**, Veo 3.1 Lite, project fcb5346a-9fc6-44dc-99bd-cb51edbe15bf, clip M2 (`ANqvLOZhsTpp…`): mirrored idle as Start and End, frames 0–4.3 s flipped back (was Midjourney fdcafb37-…, 2) | f4d2433e-ec4e-42e9-b631-1d398b0bfe0c (2), last frame |
| bot_03 | 5 | **Ozrin**: gaunt grey-bearded hedge mage, patched violet robe, brass ear piece with glowing violet coils, small clockwork shoulder pauldron, gnarled copper-wired staff with a violet crystal. **First on black** | a6078ca9-1301-461a-9a90-9d3af7a98c15 (0) | f16ff197-138f-463d-8b12-115e87f09504 (0) | 2320a8af-37c7-452c-9645-5a394a8dd6c9 (1), last frame |

Dummies: one defeated image for both, `dummy_defeated.webp`: the last frame of
b9db9d36-02df-4b59-a7fc-67ab0431de2c (4) (`… is struck by a powerful magic blast, its wooden post snaps
and it topples over …`).

## Making a bot

The standard sequence for the next opponent (mage k, `bot_kk`), with the user choosing at each step:

1. **Image.** Imagine two prompts from the prompt pattern (8 images), a step more impressive than the
   last bot, with a different accent colour. Show them in one labelled sheet (A1–B4) and let the user
   pick; leave out any that is cropped or doesn't face left.
2. **Name.** Offer three or four names that fit the picked look (its cybernetics, colour, character),
   each with its Russian form, and let the user pick. The title before it (Novice, Apprentice …) comes
   from the level.
3. **Animations.** Animate the cast and the defeat (no Loop, Low motion). Show the cast variations as
   frame strips and the defeats with their last frame, and let the user pick one of each: the cast
   should end on the throw, the defeat's last frame becomes the defeated image.
   The cast can also come from **Google Flow** (the google-flow skill; bot_02): the idle still
   mirrored as Start and End, a small fireball thrown to the right, frames pulled up to the throw and
   flipped back, then fitted to the Midjourney video's geometry (and its frames padded the same way)
   so every clip lines up; give the avatar a `scale` if its frame got taller.
4. **Cut.** Pull the frames (`anim/bot_kk/cast1/frame_00..47.png`; for the defeat only the last frame,
   `anim/bot_kk/defeat/frame_00.png`; not in git), then
   `tools/art/cut-avatar.ps1 -Art art\enemies -Prefix '' -Avatar bot_kk` writes `bot_kk.webp` (idle),
   `bot_kk_cast1.webp` (8×6 sheet) and `bot_kk_defeated.webp`, all with one crop. On black (from bot_03)
   add `-BlackBg`: it keys black with tight edges and protects the figure's dark robe and shadows (a mask
   of its visible parts, closed over small gaps, with what they enclose). If Midjourney drew a
   ground shadow anyway (bot_02), add `-Shadow`: it clears the pale, nearly colourless band under the
   feet. Check the result on a dark background for leftover white specks (`-PocketMin`).
5. **Add it.** Add it to `EnemyAvatars.all` in `ui/Avatars.kt` with its crystal position (`tipX`,
   `tipY`, measured on the idle frame) and glow colour, and its name as item k of `mage_names` in
   `res/values`, `values-ru` and `values-es` and in `Opponents.names` (there are 12 names so far and
   they repeat from mage 13, so every new bot gets a new one). Mages past the last one made still use
   the drawn mage and their old names.
6. **Record and check.** Add its row to the table above (name, look, image job, cast and defeat
   videos), build, install and check it on the phone.
