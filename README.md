# Xeno Warriors Recarnet: Minecraft Mod

A Minecraft **Forge 1.20.1** mod based on **Xeno Warriors Recarnet**, the YouTube Gacha series by **Recarnetwork Gacha**. Play as characters from the show, with their own 3D models, faces, outfits, skills, ultimates, KAs and fighting styles.

> Season 2 and Season 3 content is locked behind an unlock command so the mod doesn't spoil the show.

## Features
- **Playable characters from the show.** Each has a detailed 3D model and an expressive face (blinks, moods, battle damage). Characters also get their own outfits, idle animations and team on the character select screen.
- **Skills.** Every character carries 4 skills and an ultimate, and some also have a KA (a super ultimate). Skills run on Karma, and you unlock more as you level up.
- **Combo combat.** Every weapon has its own string of hits, timed like Epic Fight.
  - Click to chain hits. Early clicks are queued, so spam-clicking doesn't help.
  - Strings end in a finisher that knocks enemies away.
  - Sneak + click does a heavy launcher.
  - Party bots fight by the same rules.
- **DBZ-style auras** in each character's colour when you power up, focus or fight with an element.
- **Party system.** Team up with friends or **bot teammates** (`/xeno bot`). Characters with synergies get bonuses when they're together.
- **World content.** New ores, armour, weapons, vehicles, bounties, the Recarnet dimension and more.

## Requirements
- Minecraft **1.20.1** with **Forge 47.x**
- **[GeckoLib](https://modrinth.com/mod/geckolib) 4.x** (required)

### Optional companion mods
These are recommended, not required.
- **Epic Fight**: when its battle mode is on, it takes over combat. Our models and details still show.
- **Lodestone**: fancier particle effects.
- **Simple Voice Chat**: characters' mouths move while they talk.
- **DragonMineZ**: charging and blocking poses, plus a few shared animations.

## Install
1. Install Forge for Minecraft 1.20.1.
2. Put GeckoLib and `xenowarriors-<version>.jar` in your `mods` folder. Get the jar from this repo's **[Releases](../../releases)** page, under "Latest build".
3. Start the game. The first time you join a world, you'll pick your character.

## Controls (defaults)
Change any of these in Options → Controls → Key Binds → Xeno Warriors.

| Action | Key |
|---|---|
| Skills 1–4 | Ctrl + 1, 2, 3, 4 |
| Ultimate | Ctrl + 5 |
| KA / super ultimate | Ctrl + 6 |
| Combo attack / heavy | Left click / Sneak + left click |
| Lock on | Z |
| Karma Sense | K |
| Stats menu | M |

## Useful commands
- `/xeno choose`: switch character
- `/xeno bot`: add bot teammates
- `/xeno party menu`: open the party menu
- `/xeno help`: list everything else

## In-game guide
Everyone gets the **Xeno Warriors Guide** book the first time they join (craft another from a book and paper). The same pages are under **Guide** in the main menu (M).

**When you add or change a feature, add or update its entry in the guide data file:** `src/main/resources/assets/xenowarriors/guide/guide.json` (chapters → pages; `{key:NAME}` shows the player's key, `"requires"` hides a page without that mod, `"season"` hides it until that season is unlocked). Ways to raise or lower faction standing go in `faction/RepRules.java`; the Factions screen lists them from there.

## Building from source
- On Windows, double-click **BUILD-MOD.bat**.
- Anywhere else, install Java 17 and run `./gradlew build`. The jar ends up in `build/libs/`.

The other mods this one builds against are downloaded from their official sources on the first build. They aren't stored in this repo.

## Testing
All tests run in GitHub Actions (Actions tab → pick the workflow → **Run workflow**, or `gh api -X POST repos/<owner>/<repo>/actions/workflows/<file>/dispatches -f ref=<branch> -f "inputs[...]=..."`). Results are published as a pre-release named after the test.

- **UI test** (`uitest.yml`, input `focus`): starts the game on a virtual screen, runs the test set named by `focus` (see `client/UiTest.java` and `client/test/`) and publishes screenshots and logs to the release `uitest-<focus>`. A focus starting with `dmz`, `ef` or containing `tacz` also loads DragonMineZ, Epic Fight or TaCZ.
- **Server self-test** (`selftest.yml`, input `extra`): starts a dedicated server, generates Recarnet and checks it, then stops. Look for `XW-SELFTEST OK` in `server.log` of the release `selftest` (or `selftest-<extra>`).

### Real mod pack test (`pack`)
Checks the mod inside the full mod pack it's played with, listed in `tools/pack/pack.json` (exact versions, Forge 47.4.20). Unlike the other tests it uses the **real reobfuscated jar on a real Forge install**, not the dev environment, so it catches mixin conflicts and refmap problems the way players would hit them. The other mods are downloaded at test time from Modrinth (or CurseForge's CDN / the author's maven) and never stored in the repo; Essential is skipped because it can't run headless.

- **Client:** run `uitest.yml` with `focus` = `pack`. A dedicated server with the pack's server-side mods makes a world, then the real client joins it with the whole pack and runs `client/test/PackTests.java`: every character, every Xeno Warriors menu, Mikiko's and Rhalix's skills and ultimates, bots, Karma Sense, every gun form aimed and fired, placing and breaking blocks, a Journey, the DragonMineZ menu and a team ultimate. Each check logs `XW-TEST pack ... PASS/FAIL`, ending with `XW-TEST pack summary pass=N fail=N`.
- **Dedicated server:** run `selftest.yml` with `extra` = `pack` (server-side mods only); look for `XW-SELFTEST OK` and the `XW-SELFTEST mods loaded` line.
- **What to read in the release:** `mods-client.tsv` / `mods-server.tsv` (each mod: exact file, near version, client-only or skipped, and where it came from), `pack-report-client.txt` / `pack-report-server.txt` (every ERROR/FATAL line, mixin problem, Xeno Warriors warning and stack trace through our code, with a `PACK-SCAN` summary line per log), the full Forge logs, crash reports and screenshots.
- To change the pack, edit `tools/pack/pack.json`. Locally you can run `bash tools/pack/run_pack.sh server` (needs Java 17 and Python 3) or `client` (also needs `xvfb-run`).

## Credits and license
*Xeno Warriors Recarnet*, its characters, story and art belong to **Recarnetwork Gacha**.

This mod is © Recarnetwork Gacha. All rights reserved (see [LICENSE](LICENSE)). You're welcome to play it, record it and use it in videos. Please don't re-upload or redistribute it, or reuse its code or assets, without permission.

See [CHANGELOG.md](CHANGELOG.md) for what's new.
