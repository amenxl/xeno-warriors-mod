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

## Building from source
- On Windows, double-click **BUILD-MOD.bat**.
- Anywhere else, install Java 17 and run `./gradlew build`. The jar ends up in `build/libs/`.

The other mods this one builds against are downloaded from their official sources on the first build. They aren't stored in this repo.

## Credits and license
*Xeno Warriors Recarnet*, its characters, story and art belong to **Recarnetwork Gacha**.

This mod is © Recarnetwork Gacha. All rights reserved (see [LICENSE](LICENSE)). You're welcome to play it, record it and use it in videos. Please don't re-upload or redistribute it, or reuse its code or assets, without permission.

See [CHANGELOG.md](CHANGELOG.md) for what's new.
