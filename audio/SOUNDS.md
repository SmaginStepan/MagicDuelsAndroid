# Sound effects

All sounds are **CC0** (public domain, no credit required). They are in `app/src/main/res/raw` and
played by `ui/Sounds.kt` (Android `SoundPool`); Options → Sound effects turns them off.

## Sources (downloaded 2026-11-26)

| Pack | Where | Licence | Used for |
|---|---|---|---|
| Basic Spell Impacts (20 WAV, 48 kHz/24-bit) by lentikula | https://lentikula.itch.io/freecc0-basic-spell-impacts-sfx | CC0 | fire, water and lightning hits; ice for the freeze |
| Impact Sounds (130 OGG) by Kenney | https://kenney.nl/assets/impact-sounds | CC0 | earth hits (impactMining), globes bursting (impactGlass_medium) |
| Interface Sounds (100 OGG) by Kenney | https://kenney.nl/assets/interface-sounds | CC0 | menu taps, toggles, cancel, win, lose |
| Magic Spell SFX (7 OGG) by JaggedStone | https://opengameart.org/content/magic-spell-sfx | CC0 | casting, launch, shadow hits |

The packs themselves are not in the repository.

## In the game

| Event | File(s) | Source file | Volume |
|---|---|---|---|
| You tap a spell (the bot is silent) | `sfx_cast.ogg` | Kenney click_004 (your pick) | 0.3 |
| A shot is launched | `sfx_launch.ogg` | magical_5.ogg | 0.55 |
| A shot freezes into its globe | `sfx_freeze_1/2.wav` | Ice Spell Impact 4, 5 | 0.5 |
| Matched globes burst | `sfx_pop_1/2.ogg` | impactGlass_medium_000, 002 | 0.8 |
| Fire hits a mage | `sfx_hit_fire_1/2.wav` | Fire Spell Impact 3, 4 | 1 |
| Water hits a mage | `sfx_hit_water_1/2.wav` | Water Spell Impact 4, 1 | 1 |
| Earth hits a mage | `sfx_hit_earth_1/2.ogg` | impactMining_000, 003 | 1 |
| Air (and lightning) hits a mage | `sfx_hit_air_1/2.wav` | Lightning Spell Impact 3, 1 | 1 |
| Shadow hits a mage | `sfx_hit_shadow.ogg` | magical_7.ogg | 1 |
| Options tap | `sfx_ui_click.ogg` | click_001 | 0.8 |
| Main menu buttons (quiet) | `sfx_ui_menu.ogg` | click_002 | 0.3 |
| Language / sound switch | `sfx_ui_toggle.ogg` | toggle_001 | 0.8 |
| Cast cancelled | `sfx_ui_back.ogg` | back_001 | 0.8 |
| Victory / defeat | `sfx_win.ogg`, `sfx_lose.ogg` | confirmation_002, error_006 | 1 |

Variants are picked at random and every sound plays at a slightly random pitch (±6%).

The WAVs were made with `tools/audio/wav-sfx.ps1`: mono, 22050 Hz, 16-bit, leading silence trimmed,
cut to 1.2 s (freeze 0.9 s) with a 0.12 s fade-out, peak-normalised. All sounds together are 619 KB.
