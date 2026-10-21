# Xeno Warriors Recarnet – Minecraft Mod (Forge 1.20.1)

Version 0.3.1: character selection, detailed 3D models with HD faces for Mikiko and Rhalix, Rhalix's wings and Kishi, and Mikiko's flying wind.

## What's in this version
- **Character selection screen.** The first time a player joins a world, they pick Mikiko or Rhalix. Each card shows a 3D preview of the player as that character.
- **Detailed 3D models in-game.** Hair, goggles, jackets, coats, straps, boots and more, built from the official art, plus high-res faces. They move with the head, arms and legs.
- **Rhalix's wings.** Black feathered wings come out and flap while he's flying, gliding, falling, or casting Kishi, and fold away when he lands.
- **Mikiko's wind.** Gusts of wind swirl around Mikiko while he flies.
- **Saving.** The choice is saved, survives death, and other players see your character in multiplayer.

## Skills
Default keys (change them in Options > Controls > Key Binds > Xeno Warriors):
Skill 1 = Z, Skill 2 = X, Skill 3 = C, Skill 4 = V, Ultimate = R.

| Character | Slot | Skill | What it does |
|---|---|---|---|
| Rhalix | Skill 1 | **Kishi** | Aim at a mob/player (up to 20 blocks) or a spot and press Z: a gravity dome slams down around them. Inside, they're heavily slowed, can't jump or fly, and take damage every second that grows the longer they stay in. Press Z again while it's up to make the gravity heavier (up to level 3). Lasts 8s, 15s cooldown. |

Kishi's numbers (size, length, slowness, damage) are at the top of `KishiDomeEntity.java`.

## Commands
- `/xeno choose`: reopen the selection screen to switch characters.
- `/xeno set <player> mikiko|rhalix`: set someone's character (ops only).
- `/xeno reset <player>`: clear someone's character so the screen shows again (ops only).

## Getting the mod file
- **From GitHub:** every push builds the mod automatically. Download `xenowarriors-0.3.1.jar` from the repo's **Releases** page ("Latest build").
- **One click on Windows:** double-click **BUILD-MOD.bat**. It finds Java, builds the mod, and puts the jar in the folder above this one.
- **Manually:** install Java 17, then run `gradlew build` in this folder. The jar is in `build/libs/`.

Then drop the jar into the `mods` folder of a **Forge 1.20.1** profile (Forge 47.x). In the Modrinth App: make a 1.20.1 profile with the Forge loader and drag the jar in.

## Where things are
- `src/main/java/com/xenowarriors/`: all the code. `client/` is what only the player's game needs (screen, HUD, keys, models, wind).
- `src/main/resources/assets/xenowarriors/textures/entity/character/`: skins, detail textures, and HD faces.
- `src/main/resources/assets/xenowarriors/models/detail/`: the 3D detail models, generated from the Blockbench files.
