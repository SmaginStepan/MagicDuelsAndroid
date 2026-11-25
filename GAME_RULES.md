# Magic Duel — current rules (v0.1)

A match-3 duel for Android (Kotlin, portrait). All numbers live in
`app/src/main/java/com/an0obis/magicduel/game/Rules.kt` and the opponent ladder in `Opponents.kt`.

## Screen
- Enemy (top right) with HP bar and, for mages, its visible spell order.
- Shared 5×5 field in the middle, with 5 flight lanes on each side.
- You (bottom left) with HP bar, and the 5×3 spell grid (bottom right). Grid row N fires into lane N.

## Casting
- Tap a spell: your mage casts it for a fixed 2.7 s (animated). You can't cast again until it finishes.
- The tapped spell's whole row and column (7 spells) dissolve and are refilled when the cast completes.
- The shot appears at the left edge of its lane and flies to the field.
  Lane 1 (top) is slowest (6.0 s), lane 5 (bottom) is fastest (2.63 s).

## Impact
- The shot enters its row (from the left for you, from the right for the enemy) and keeps sliding,
  pushing every globe ahead of it until they are packed against the far edge.
  It freezes into a crystal globe only where it stops. In an empty row it reaches the far edge.
  Example, you fire X into `· · W · E` → `· · X W E`.
- Your shot into a full row → **backfire**: the shot flies back and hits you (10 damage).
- A bot's shot into a full row still pushes: every globe moves one cell toward you, the bot's shot freezes
  at the right edge, and the globe pushed off your edge flies at you (10 damage, `PUSH_OUT_DAMAGE`).
  Bots never backfire.
- Gravity: globes fall into empty cells below.
- 3+ equal elements in a row or column explode. Every globe of the same element connected to an
  exploding one through shared sides explodes with it (and so on, through the whole connected group).
  A 2×2 square alone does not explode, but it does if it touches an exploding line of its element.
- Damage goes to the opponent of whoever fired the shot that started it:
  5 per exploded globe, times the step of the chain reaction — the first explosion ×1,
  the second (triggered by falling globes) ×2, the third ×3, and so on.
  Example: 4 globes then 3 globes = 4×5×1 + 3×5×2 = 20 + 30 = 50.
- Impacts are resolved one at a time; a shot that arrives while the field is moving waits at the edge.

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
  Each next mage casts and waits 15% shorter, down to a floor of 0.8 s cast / 0.6 s wait, and has 8 more HP
  (90 HP at first, at most 250). Around level 17 they are as fast as the original first bot.
- Titles rise every two levels: Novice, Apprentice, Initiate, Adept, Conjurer, Magus, Sorcerer, Warlock,
  Archmage, Grand Archmage.
- Win to go up a level; lose to retry the same level. Your level is saved on the phone.

Start: 4 elements (fire, water, earth, air). Lightning and shadow are already drawn and ready to unlock later.

## Not built yet
Player levelling up / unlocking elements, sound, leaderboard, PvP, options.
