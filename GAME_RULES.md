# Magic Duel — current rules (v0.1)

A match-3 duel for Android (Kotlin, portrait). All numbers live in
`app/src/main/java/com/an0obis/magicduel/game/Rules.kt` and the opponent ladder in `Opponents.kt`.

## Screen
- Enemy (top right) with HP bar and, for mages, its visible spell order.
- Shared 5×5 field in the middle, with 5 flight lanes on each side. Field cells are 1.25 layout units
  (`Rules.FIELD_CELL_UNITS`), so the field is 6.25 units wide and each side lane 1.75; the whole width
  is 9.75 units, and the mages, panels and spell grid are sized in layout units.
- You (bottom left) with HP bar, and the 5×3 spell grid (bottom right). Grid row N fires into lane N.

## Casting
- Tap a spell: your mage casts it (animated). You can't cast again until it finishes. Casting takes
  longer for the top lanes: your base 2.7 s plus the lane's time (lane 1: 6.84 s, lane 2: 5.58 s,
  lane 3: 4.32 s, lane 4: 3.045 s, lane 5: 1.785 s, `Rules.LANE_TIME`) minus the short flight
  (0.09 s), so 9.45 s into lane 1, 8.19 s, 6.93 s, 5.65 s and 4.39 s into lane 5 (`Rules.castTime`).
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
- Five **wall buttons** sit above the field, one over each column. Tapping one casts walls: a short cast
  (75% of the caster's base cast time, `Rules.WALL_CAST_SHARE`: 2.0 s for you, shorter than any spell)
  that takes your one casting slot like a spell and can be cancelled the same way. The spell grid isn't
  touched. While it's cast, the button sits in a glyph ring and the column glows.
- When the cast completes (as soon as the field is still), a stone wall rises in **every empty cell of
  that column**. Walls never fall; globes rest on them.
- A shot that reaches a wall itself **breaks it and takes its cell**, going no further:
  `· · ▓ G G` + X → `· · X G G`. If there are globes in front of the wall, the wall holds like the field
  edge and the globes are packed against it: `· G ▓ · ·` + X → `X G ▓ · ·`.
- If the stretch in front of the wall is already full, the shot can't go anywhere and **bounces back at
  its caster** (10 damage), for bots too: a wall stops a bot from pushing a full row out at you.
- An explosion **breaks the walls right beside it in the same row** (left and right, not above or
  below). Whatever stood on a broken wall then falls.
- Walls don't count for lines of 3: a wall between two globes breaks the line.
- Bots conjure walls too: about one in five of a mage's planned moves is a wall in a random column
  (`Rules.BOT_WALL_CHANCE`), shown in its queue as a wall button marked with the column (↓3).

## Menu and tutorial
- The game opens on a start menu: **Continue** (shows your level and next opponent), **Restart game**
  (asks first, then back to level 1 with the tutorial), and Leaderboard / PvP duel / Options, which
  show "coming soon" for now. In a duel, the **Menu** button or Android Back returns to the menu (the duel
  pauses); Back on the menu closes the app.
- Level 1 (target dummy) is the tutorial: short hints that follow what you do — tap a spell, watch it fly
  and push, line up 3+, how damage and chains work, and a note if a shot backfires. Beating the dummy
  ends the tutorial for good (until Restart game).

## Opponents (endless ladder, `Opponents.kt`)
- Level 1 Target dummy (40 HP) and level 2 Oak dummy (80 HP) never fire back.
- From level 3 on, mages that level up. Each shows its plan: the next 5 shots (element + lane), generated
  randomly in advance. The first one is cast next; when it has been cast it leaves the queue and a newly
  planned random shot joins at the end. Bots follow the same impact rules, except that their shots push
  full rows instead of backfiring (see Impact).
- The first mage casts for 15 s and waits 28 s between shots (10× slower than the original first bot).
  Like yours, a bot's cast also takes its lane's time on top (minus the short flight), so its shots
  reach the field when they always did.
  Each next mage casts and waits 15% shorter, down to a floor of 0.8 s cast / 0.6 s wait, and has 8 more HP
  (90 HP at first, at most 250). Around level 17 they are as fast as the original first bot.
- Titles rise every two levels: Novice, Apprentice, Initiate, Adept, Conjurer, Magus, Sorcerer, Warlock,
  Archmage, Grand Archmage.
- Win to go up a level; lose to retry the same level. Your level is saved on the phone.

Start: 4 elements (fire, water, earth, air). Lightning and shadow are already drawn and ready to unlock later.

## Not built yet
Player levelling up / unlocking elements, leaderboard, PvP.
