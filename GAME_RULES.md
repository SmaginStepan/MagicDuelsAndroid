# Arcane Duel: Clash of Elements — current rules (v0.1)

A match-3 duel for Android (Kotlin, portrait). All numbers live in
`app/src/main/java/com/an0obis/magicduel/game/Rules.kt` and the opponent ladder in `Opponents.kt`.

## Screen
- Enemy (top right) with HP bar and, for mages, its visible spell order.
- Shared 5×5 field in the middle, with 5 flight lanes on each side. Field cells are 1.25 layout units
  (`Rules.FIELD_CELL_UNITS`), so the field is 6.25 units wide and each side lane 1.75; the whole width
  is 9.75 units, and the mages, panels and spell grid are sized in layout units.
- You (bottom left) with HP bar, and the 5×3 spell grid (bottom right). Grid row N fires into lane N.
- Every duel (a new level, a retry, a revive) opens with a title card over the field for 1.6 s, while
  nobody can cast yet: the opponent's figure, **Level X** and the opponent's name. It pops in and fades
  as the duel begins.

## Casting
- Tap a spell: your mage casts it (animated). You can't cast again until it finishes. Casting takes
  longer for the top lanes: your base 2.7 s plus the lane's time (lane 1: 6.84 s, lane 2: 5.58 s,
  lane 3: 4.32 s, lane 4: 3.045 s, lane 5: 1.785 s, `Rules.LANE_TIME`) minus the short flight
  (0.09 s): 9.45 s, 8.19 s, 6.93 s, 5.65 s and 4.39 s (`Rules.castTime`). **Your casts are 20% quicker**
  (`Rules.PLAYER_CAST_SCALE`): 7.56 s into lane 1, 6.55 s, 5.54 s, 4.52 s and 3.51 s into lane 5.
  Lane 1 takes 5.06 s longer than lane 5: 50% more than the original 3.37 s, around the same middle lane.
- While a mage casts, its target lane lights up with a **countdown**: the seconds until the shot reaches
  the field, a small quiet number at the bottom of the lane strip (no border or animation). The bot's
  lanes show one too.
- The tapped spell's whole row and column (7 spells) dissolve. The tapped button stays as it is, inside a
  ring of magic glyphs that lights up as the cast progresses (three styles, taking turns; the bot's
  next spell gets one too), and is replaced the moment the cast completes. The other new spells are chosen at once and appear
  as small element symbols that grow during the cast; they become usable buttons when the cast completes.
- While casting, a **Cancel cast** button appears above your mage. Cancelling fires nothing and brings the
  dissolved spells back unchanged, so you can pick again straight away. The button disappears when the
  cast is fired or cancelled.
- The shot appears at the left edge of its lane and flies to the field at 6.75 layout units a second
  (`Rules.SHOT_SCREEN_SPEED`, 5.4 field cells a second), the same speed it then slides through its
  row. The flight is 0.625 units (half a field cell), about 0.09 s in every lane; sliding all the way
  across an empty row takes about 0.93 s. From the tap to the shot reaching the field takes exactly as
  long as the cast time plus the lane's time above.

## Impact
- The shot enters its row (from the left for you, from the right for the enemy) and keeps sliding,
  pushing every globe ahead of it until they are packed against the far edge.
  It freezes into a crystal globe only where it stops. In an empty row it reaches the far edge.
  Example, you fire X into `· · W · E` → `· · X W E`.
- Your shot into a full row → **backfire**: the shot flies back and hits you (10 damage).
- A bot's shot into a full row still pushes: every globe moves one cell toward you, the bot's shot freezes
  at the right edge, and the globe pushed off your edge flies at you (10 damage, `PUSH_OUT_DAMAGE`).
  Bots never backfire.
- Gravity: globes fall into empty cells below. A shot that stops above an empty cell first freezes into its
  globe in place (0.8 s, `Rules.FREEZE_TIME`) and only then falls, so it never drops in comet form; a shot
  that stops on top of something lets the field settle straight away.
- 3+ equal elements in a row or column explode. Every globe of the same element connected to an
  exploding one through shared sides explodes with it (and so on, through the whole connected group).
  A 2×2 square alone does not explode, but it does if it touches an exploding line of its element.
- Damage goes to the opponent of whoever fired the shot that started it:
  5 per exploded globe, times the step of the chain reaction — the first explosion ×1,
  the second (triggered by falling globes) ×2, the third ×3, and so on.
  Example: 4 globes then 3 globes = 4×5×1 + 3×5×2 = 20 + 30 = 50.
- Impacts are resolved one at a time; a shot that arrives while the field is moving waits at the edge.

## Walls
- Three **wall buttons** sit above the field, over the middle columns 2–4 (`Rules.WALL_COLUMNS`); the edge
  columns 1 and 5 can't be walled, by you or the bot. Tapping one casts walls: a long cast
  (1.25× the caster's slowest spell, `Rules.WALL_CAST_FACTOR`: about 9.45 s for you, longer than any spell)
  that takes your one casting slot like a spell and can be cancelled the same way. The spell grid isn't
  touched. While it's cast, the button sits in a glyph ring and the column glows, with a **countdown**
  at its top like the lanes': the seconds until the walls rise (yours and the bot's).
- A wall button is **disabled (grey and faded) while the cell right below it** (the column's top cell) holds a wall or a
  globe, or while the bot is casting walls into that same column (its glyph ring shows around the
  button), and lights up again when it can be used. Walls are locked on level 1 (the tutorial dummy):
  they unlock on level 2, which starts with a short walls lesson.
- When the cast completes (as soon as the field is still), a wall of arcane crystal bricks rises in **every empty cell of
  that column**. Walls fall like globes when the cells below them empty, and globes rest on them.
- A shot that reaches a wall itself **breaks it and takes its cell**, going no further:
  `· · ▓ G G` + X → `· · X G G`. If there are globes in front of the wall, the wall holds like the field
  edge and the globes are packed against it: `· G ▓ · ·` + X → `X G ▓ · ·`.
- If the stretch in front of the wall is already full, the shot **pushes anyway and breaks the first wall**,
  yours and the bot's alike: the globes in front of it move one cell, the front one into the wall's cell,
  and the shot takes the entry cell. Example from the left: `F W ▓ · ·` + X → `X F W · ·`; from the right:
  `E A ▓ W F` + X → `E A W F X`. Only a full row **without** a wall still bounces your shot back at you (10
  damage), or, for a bot's shot, pushes a globe out at you.
- An explosion **breaks the walls right next to it**: left, right, above and below (not diagonally).
  Whatever stood on a broken wall then falls.
- Walls don't count for lines of 3: a wall between two globes breaks the line.
- Bots conjure walls too: about one in five of a mage's planned moves is a wall in a random middle column
  (`Rules.BOT_WALL_CHANCE`), shown in its queue as a wall button marked with the column (↓3). The same
  rule holds for bots: if that column's top cell is taken when its turn comes, it picks a free column
  instead (or casts a shot if every column is blocked). If the column fills up while the bot is still
  casting its wall (with your walls, say), it drops that cast at once and goes straight on to its next
  move.

## Menu and tutorial
- The game opens on a start menu: your **essence** in the top right corner (a tap opens the shop),
  **Continue** (shows your level and next opponent), and two by two **Leaderboard**, **Shop**, PvP duel
  ("coming soon") and **Options** (language, sound, your name, your avatar, and **Restart game**, which
  asks first and then goes back to level 1 with the tutorial; it keeps your essence, streak and unlocked
  avatars). In a duel, the **Menu** button or Android Back returns to the menu (the duel pauses); Back
  on the menu closes the app.
- The first time the app opens, it asks your name (once, if none is set; Options can change it).

## Essence, revives and the shop (`Economy.kt`)
- **Essence** (the glowing crystal) is collected from the daily gift and the shop, and unlocks paid
  avatars; revives are free. A new player starts with **3**.
- **Daily gift**: the first time the app opens on a new day (the phone's date), a popup shows the
  7-day streak and today's gift: **1 essence** plus a **streak bonus** of 0, 1, 1, 2, 2, 3, 5 on days
  1 to 7 in a row (1, 2, 2, 3, 3, 4, 6 in all, 21 a week). **Collect** adds it and the menu's counter
  counts up. After day 7 the streak starts again at day 1; a missed day resets it to day 1. Turning the
  phone's date back gives nothing.
- **Going down**: at 0 HP you don't lose straight away. The duel pauses (your cast is called off and
  shots on their way vanish), your avatar plays its collapse, then the **revive popup** shows it fallen
  with one button, **Revive** (free). It plays the avatar rising back up in a glow of its crystal's
  colour (a clip of its own per avatar), then the **duel starts over** at the same level: a fresh
  board, both at full HP, the bot's plan and the intro again. There is no giving up: Back steps out to
  the menu, and the popup is waiting when you return to the duel. Revives work against the dummies too
  and don't change the leaderboard.
- **Shop**: essence packs of 10, 30 and 100 (free test purchases in debug builds; "soon" in release
  builds until Google Play billing is in) and the **avatars**.
- **Avatars**: only the Archmage is free. The Sorceress costs **3** essence and the Conjurer **5**;
  unlocked once, they are yours for good. In the shop and in "Choose your avatar" a locked one shows
  its price (dimmed in the picker); a tap opens the unlock popup (the figure, its price, your essence,
  Cancel and Unlock, or Shop instead when you are short). Unlocking puts the avatar on. An avatar you
  own is put on with a tap (the shop marks it Owned, or Chosen when it is yours now). A paid avatar
  chosen before avatars had prices goes back to the Archmage.

## Leaderboard
- An online board of every player's **highest level** and name, on the ScoreServer service
  (`net/ScoreClient.kt`; server settings in `local.properties`, not in git). If two players reached the
  same level, **whoever reached it first ranks higher**. A rename doesn't change your place.
- You are a random player id kept on the phone. Your best level (kept even after Restart game) is sent
  at start-up, when you reach a new level and after a rename; if the phone was offline it's sent later.
- The Leaderboard screen shows the top 10 (rank, name, level) with **your row highlighted**. If you're
  further down, your row follows after a "⋯" gap, between your neighbours. While loading it says so;
  if the server can't be reached there's a Retry button.
- Level 1 (target dummy) is the tutorial: short hints that follow what you do — tap a spell, watch it fly
  and push, line up 3+, how damage and chains work, and a note if a shot backfires. Beating the dummy
  ends the tutorial for good (until Restart game).
- Every hint has a **×** in its corner: tapping it closes that hint for the rest of the duel (the lesson
  goes on, and the next hint shows as usual).
- Level 2 (oak dummy) unlocks walls and teaches them: a hint points at the wall buttons until you cast
  walls, then one explains how walls break (shown for 9 s). It is shown once (until Restart game).

## Opponents (endless ladder, `Opponents.kt`)
- Level 1 Target dummy (40 HP) and level 2 Oak dummy (80 HP) never fire back.
- From level 3 on, mages that level up. Each shows its plan: the next 5 shots (element + lane), generated
  randomly in advance. The first one is cast next; when it has been cast it leaves the queue and a newly
  planned random shot joins at the end. Bots follow the same impact rules, except that their shots push
  full rows instead of backfiring (see Impact).
- The first mage casts for 12 s (its base, 20% under the 15 s it was) and waits 28 s between shots.
  Like yours, a bot's cast also takes its lane's time on top (minus the short flight), so its shots
  reach the field when they always did (bots don't get your 20% quicker casting).
  Each next mage casts and waits shorter, by the most at the bottom of the ladder (`Opponents.speed`):
  20% after the first mage, 18.5% after the second, then smaller and smaller steps, about 10% by mage 11
  and towards 5% far up. Base cast times: 12, 9.6, 7.8, 6.5, 5.5, 4.6, 4.0 s … 2.4 s at level 13, 1.6 s
  at level 17, 1.0 s at level 23, and the floor of 0.8 s cast / 0.6 s wait around levels 28 / 43. Each
  also has 8 more HP (45 HP at first, half the 90 it was; at most 250, from level 29).
- Titles rise every two levels: Novice, Apprentice, Initiate, Adept, Conjurer, Magus, Sorcerer, Warlock,
  Archmage, Grand Archmage.
- Mages have Midjourney avatars with cybernetic parts that grow level by level (`art/enemies/PLAN.md`;
  the first two are made so far, later mages use the drawn mage). A bot's avatar plays its cast animation
  over each cast and throws the magic itself.
- Win to go up a level; going down means a revive and the same level again (see Essence). Your level
  is saved on the phone.
- The victory card shows the beaten opponent's **defeated pose**: a beaten bot sinks into it on the
  field, and the dummies share one.

Start: 4 elements (fire, water, earth, air). Lightning and shadow are already drawn and ready to unlock later.

## Not built yet
Player levelling up / unlocking elements, PvP, buying essence (Google Play billing).
