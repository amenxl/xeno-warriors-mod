# Xeno Warriors Recarnet – Minecraft Mod (Fabric 1.20.1)

Version 0.3.0: character selection, detailed 3D models for Mikiko and Rhalix, and Rhalix's skill 1 (Kishi).

## What's in this version
- **Character selection screen.** The first time a player joins a world, they pick Mikiko or Rhalix. Each card shows a 3D preview of the player as that character.
- **Detailed 3D models in-game.** Hair, goggles, jackets, coats, straps, boots and more, built from the official art. They move with the head, arms and legs.
- **Rhalix's wings.** Black feathered wings come out and flap while he's flying, gliding, falling, or casting Kishi, and fold away when he lands.
- **Saving.** The choice is saved, survives death, and other players see your character in multiplayer.

## Skills
Default keys (change them in Options > Controls > Key Binds > Xeno Warriors):
Skill 1 = Z, Skill 2 = X, Skill 3 = C, Skill 4 = V, Ultimate = R.
The skill bar in the bottom-right corner shows each skill and its cooldown.

| Character | Slot | Skill | What it does |
|---|---|---|---|
| Rhalix | Skill 1 | **Kishi** | Aim at a mob/player (up to 20 blocks) or a spot and press Z: a gravity dome slams down around them. Inside, they're heavily slowed, can't jump or fly, and take damage every second that grows the longer they stay in. Press Z again while it's up to make the gravity heavier (up to level 3). Lasts 8s, 15s cooldown. |

Kishi's numbers (size, length, slowness, damage) are at the top of `KishiDomeEntity.java`.

## Commands
- `/xeno choose`: reopen the selection screen to switch characters.
- `/xeno set <player> mikiko|rhalix`: set someone's character (ops only).
- `/xeno reset <player>`: clear someone's character so the screen shows again (ops only).

## Easiest way: one click (Windows)
Double-click **BUILD-MOD.bat**. It finds Java on your computer (or tells you where to get it), builds the mod, and puts `xenowarriors-0.3.0.jar` in the folder above this one, then opens that folder for you. The first time takes a few minutes because it downloads Minecraft and Fabric.

Then drop the jar into the `mods` folder of a **Fabric 1.20.1** profile together with **Fabric API for 1.20.1** (https://modrinth.com/mod/fabric-api).

## How to build it (manually)
1. Install **Java 17** (JDK), for example from https://adoptium.net (Temurin 17).
2. Open a terminal (Command Prompt or PowerShell on Windows) in this folder.
3. Run one of these:
   - `gradlew runClient` launches Minecraft with the mod so you can test it.
   - `gradlew build` makes the mod file at `build/libs/xenowarriors-0.3.0.jar`.
   (On Mac/Linux use `./gradlew` instead of `gradlew`.)
   The first run downloads Minecraft, Fabric and Gradle, so it takes a few minutes.
4. To play with it normally: install **Fabric Loader** for 1.20.1 (https://fabricmc.net/use/installer), then put the jar **and Fabric API for 1.20.1** (https://modrinth.com/mod/fabric-api) into your `.minecraft/mods` folder. The Modrinth App can do this for you: make a 1.20.1 Fabric profile, add Fabric API, then add the jar.

You can also open this folder in IntelliJ IDEA (with the Minecraft Development plugin), which sets everything up for you.

## Where things are
- `src/main/java/...`: game logic that runs on the server (characters, skills, Kishi).
- `src/client/java/...`: things only the player's game needs (screen, HUD, keys, models).
- `src/main/resources/assets/xenowarriors/textures/entity/character/`: skins (`mikiko.png`, `rhalix.png`, normal 64x64 skins) and detail textures.
- `src/main/resources/assets/xenowarriors/models/detail/`: the 3D detail models, generated from the Blockbench files.
