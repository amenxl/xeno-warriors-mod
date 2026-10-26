# Away plan (Fri Oct 23 → Wed Oct 28)

The user is away until Wednesday. Token budget for the whole stretch is about 10% (~1.5M tokens): aim for **≤ 60k tokens per check-in**.
Every ~5 h: do one focused chunk, build, deliver the jar (SendUserFile + copy to Documents\Xeno Warriors Mod), send pictures + a short update, log it below, then schedule the next check-in.
Questions go in the update; never stop to wait for an answer.

Generator tools live in tools/xw (if the container was reset: `mkdir -p /home/claude/xw && cp -r tools/xw/. /home/claude/xw/`).
User refs: C:\Users\amenk\OneDrive\Pictures\Xeno Warriors Recarnet (chars/, Poses for Chars/, manga/, Heku/, S3 script .docx, episodes).

## Queue (rough priority)
1. ~~Rhalix hair~~ done — user picked C (in game since 0.89.1)
2. ~~Skylander wing variants~~ done
3. ~~Sky Crystal + Corrupted tools~~ done (tools/sprites/tool_sprites.py)
4. ~~Island Skylanders, bigger islands, house/chest defense~~ done
4b. ~~Wing styles~~ done 0.90.0 (tools/xw/wings/wing_styles.py) — was: Skylander wing STYLES, not just size/color** — separate wing models per style (e.g. big feathered angel, hawk/eagle pointed, swept falcon, bat/dragon membrane, butterfly/fairy, crystal sky wings, four-wing seraph, tattered/ragged). Pick per UUID (guardians: grand style). Send a picture sheet of all styles.
5. ~~Sky Hater siege towers~~ done 0.91.0 (/xeno siege to test)
6. ~~UI/HUD pass~~ done 0.92.0 (HudLayout.java). Still to check: menus/screens (stats screen, altar, fox screens) at small GUI scales; DMZ HUD actual position.
7. Dragon Ball outfit compatibility — STEP 1 done 0.93.0: DMZ skin-layout outfits drawn on a split-limb PlayerModel (bends with knees/elbows), character's own torso/arm/leg clothes hidden under them (wings/hair kept). STEP 2 done 0.96.x: GiVoxels — outer layer (jacket/sleeves/pants/hat regions) voxelized per pixel (0.9 deep) on the bending gi model, all ~90 outfits. STEP 3 done 0.97.0 (user: 'full outfit shape'): GiShapes — loose 3D cut built from skin-layout UVs (collar, gi flap, thick belt, wrist/pant cuffs; armor style: chest plate + shoulder pads). NEXT (optional): hand-made 3D accents per popular gi (belts/wristbands/shoulder pads/collars) as extra detail models keyed by DMZ texture name; DMZ textures in /tmp/dmzx (re-extract from uploads/dragonminez-2_1_3.jar).
8. ~~EF movesets for DMZ weapons~~ done 0.98.0 (EfDmzWeapons: combined presets, Rhalix's moves in the OCHS style slot via styleProvider). NEXT (optional): truly custom EF animations per character (e.g. power pole spin) — needs EF animation JSON authoring.
9. PART done 0.99.0: human reactions (HumanReactions + HumanModel poses; vanilla model only — with Epic Fight patched NPCs the poses don't show: TODO map to EF anims). Enemy polish + EF reaction fallbacks DONE 0.107.0.
10. Humans PART done 0.100.0 (HumanWorkGoal: chop natural trees, mine raw stone/ore, craft planks/swords/iron armor, build 5x5 hut once; mobGriefing respected). Skylanders done 0.101.0 (SkylanderWorkGoal: mine island Sky Ore refilled w/ cobble, plant saplings/flowers, forge sky sword/boots/helmet, build shrine/lookout/garden/quartz home). NEXT (optional): humans farms/more structures.
11. PART done 0.102.0: vanilla armor own 3D cut per material (tools/xw/armor/vanilla_armor.py; leather tinted by dye, others untinted, turtle uses shared cut). Sky/corrupted polish done 0.102.1 (upper-arm sleeves, faulds, tabard/back guard, corrupted spines + jagged faulds).
12. DONE Mon 00:45: blueprints (docs/S3_BLUEPRINTS.md, docs/s3_chars.json, sheets via tools/xw/blueprints). Script skill ideas not in mod yet: Mikiko True Empathy (copy moves), Dance Kick, Manifest True Greatness (red glow buff), Netsu Bust, Hekubeats, Kaeru return; Rhalix KA Release, wire knife, Lunar form.
13. Rhalix outfits accuracy pass with alternates for the user to pick.
14. NEW (user approved, do in this order): (a) Rhalix Lunar form = SEASON 2 locked (Seasons.unlocked(server,2)); night-only OR while DMZ full-moon skill active; silver Karma, crescent-moon eyes, Moon Reflect, Moon Fall; Lunar trials unlock. (b) Gov facilities (structure) + healing pod block (heals; diamond pickaxe drops it) + Gov workers (scanner/speed mods, alert guards) & guards (1-2 mods: speed, phase/invisible until hit a few times, attack mode, armor plating, scanner eye, jump boosters); NO elements/KAs for Gov. (c) Bounty board in villages. (d) Named mini-bosses in high danger zones. (e) SR camps.
15. Later/reminders: Gov shop/loot (saved idea); Ultima 3D model (waiting on user pics) then Ultima boss (remind user); Komatsu idea reminder scheduled ~Dec 25.

## Log
- Fri 05:30 — plan set up, folder access granted, tools backed up.
- Fri 06:10 — 0.88.0: Rhalix hair black only at the tips (options A/B/C sent, A in game; rhalix.py HAIR_STYLE env), Skylander wing variety (size/tint/spread per UUID, guardians big golden).
- Fri 11:20 — 0.89.0: Sky/Corrupted tool sets (effects: sky +35% vs airborne & faster digging above y150; corrupted lifesteal/wither & faster below y0). Islands bigger (r 7-16, centered in chunk), island Skylanders circle/land on their own island, attack anyone entering their house or breaking/opening near home.
- Fri 15:50 — user picked hair C → 0.89.1; sent wing variant sheet. (Device file tools disconnected: jar not copied to Documents.)
- Fri 16:30 — 0.90.0: 8 Skylander wing styles as separate models (angel, hawk, falcon, bat, butterfly, crystal, seraph, tattered), picked per UUID (1/9 classic), guardians seraph; sheet sent.
- Fri 21:25 — 0.91.0: Sky Hater sieges (random near island Skylanders, 2-day cooldown per island): builders build cobble+ladder tower w/ plank platforms, foreman rides top, bridge across, climb and attack Skylanders; /xeno siege (op).
- Sat 02:35 — 0.92.0: HudLayout: top-right stack under status effects (request card, then toasts), lock-on bar under boss bars, level box left of offhand slot, party HUD kept above chat, skill bar raised 34px with Epic Fight. Layout diagram sent.
- Sat 07:40 — 0.93.0: DMZ outfits bend with the body + hide own clothes underneath (no clipping); preview sheet (flat) sent.
- Sat 09:05 — 0.94.0 (user request): usable phones: 3 3D models (smart/flip/brick) x 12 colors, right-click call toggles held-to-ear pose (PhoneClient), no attacks/skills while on call.
- Sat 09:15 — 0.95.x (user requests): phone calls between players (contacts screen, ring card, SVC hidden group, /xeno phone answer|decline|hangup), ringtone + notes + caller ringback.
- Sat 09:25 — 0.95.2: call audio routed manually (MicrophonePacketEvent → decode → per-phone filter → static packet to partner); smartphone clean, flip = phone line, brick = crunchy/hiss/dropouts.
- Sat 12:55 — 0.96.1: DMZ outfits in 3D (GiVoxels per-pixel raised outer layer, cached per texture, bends with knees/elbows). Preview sheet sent (tools/xw/outfits/dmz_voxel_preview.py).
- Sat 16:35 — 0.97.0 (user clarified 'full outfit shape'): GiShapes 3D cut for DMZ outfits; preview sheet sent.
- Sat 18:00 — 0.98.0: EF movesets for DMZ weapons per character (pole: M tachi / R spear; Z sword: M longsword / R greatsword; brave+dimensional: M sword / R longsword; katana: M uchigatana / R tachi).
- Sat 23:05 — 0.99.0: townsfolk reactions: cower (explosions/lightning, flee), awe (skills), dance (jukebox), rain cover, wave (looked at), hands up (armed player close), cheer (raid won).
- Sun 04:15 — 0.100.0: townsfolk work loop (gather/mine/craft/equip/build hut).
- Sun 13:40 — 0.101.0: island Skylanders work loop (mine/plant/forge/build 4 build types); NPC armor layer added so townsfolk iron + Skylander sky armor show.
- Sun 18:45 — 0.102.0: vanilla armor in 3D per material (leather jerkin, chain coif/hauberk, iron knight plate w/ nasal helm, gold crown/sun, diamond crystal horns/shards, netherite horned spiked w/ ember core). Faces kept open.
- Sun 23:40 — 0.102.1: sky + corrupted armor polish (no more bare upper arms; faulds, sky tabard, corrupted spines).
- Mon 00:45 — S3 blueprints: Mikiko + Rhalix blueprint sheets (in-mod vs idea skills), 25-character cast roster, docs/S3_BLUEPRINTS.md (also saved to the claude.ai project). No jar this time.
- Mon 01:20 (user here) — 0.103.0: skill loadouts (SkillPool: 4 equipped + fixed ult, unlock by Character Level, Skills tab in M menu, swap = 5s cooldown). Mikiko: Dance Kick Lv15, Netsu Burst Lv30, Manifest Greatness Lv50, True Empathy Lv75. Rhalix: KA Release Lv60. Creative = all unlocked.
- Mon 01:45 (user here) — 0.104.0: KAs (ka/Ka, ka/Kas, KaClient): 7 simple + 7 rare, NPC-only (players can't hold KAs; Mikiko/Rhalix KAs unrevealed -> super slot '?' Ctrl+6), price per KA scales with strain, burnout at 6; KA Release clears; /xeno ka list|give.
- Mon 01:55 (user here) — 0.104.1: enemy Karma (ka/EnemyKarma), KA costs, KA Release cost = enemy's KA cost, /xeno ka self <ka|clear> test KAs. Elements list sent for approval.
- Mon 02:05 (user here) — 0.105.0 elements (ka/Element, ka/Elements; humans 75%/Skylanders 40%/C-souls 90%, no Blood) + 0.106.0 clashes (ka/Clashes, ClashClient; Skill.clashColor on FireBlitz/Scatter/Bang/Compress).
- Mon 02:25 (user here) — 0.107.0: animation pass (anim/Poses + client/EntityPoses + compat/epicfight/EfPoses): clash, element casts, KA cast/burnout, KA victim states, empathy mirror, KA break, work anims, corrupted model polish, EF fallbacks.
- Mon 02:30 (user here) — 0.107.1: skill info tooltips (desc/how/stats), KaGuideScreen (KAs + Elements), M menu 'KAs' tab. User will allow PC play-testing when home: link computer, then do a full visual test pass.
- Mon 02:45 (user here) — 0.108.0: scaling/EnemyScaling (zone per 1k blocks, player power pull w/ spread, HP/dmg/armor/karma/xp), Lv on lock-on bar, zone messages.
- Mon 02:55 (user here) — 0.108.1: DMZ-aware enemy scaling, character/Seasons (/xeno season unlock|lock). Reminder set ~Dec 25 for Komatsu idea (#4, every ~2 months). NEW QUEUE (user approved): bounty boards (1), named mini-bosses (2), SR camps (3), Gov facilities w/ healing pods (diamond pick drops), Gov workers/guards w/ human modifications (5), Rhalix Lunar form as Season 2 locked content (7), HUD (KH style, waiting on 4 answers), Ultima 3D model options (needs reference pics) then Ultima boss later (remind user).
- Mon 03:05 (user here) — 0.109.0: KH HUD (client/KhHud, HudFaces, DmzHud, party/KhParty; textures/gui/hud via tools/xw/hud/tex.py). Settings: HUD style / hearts shown / skills always.
  User answers: Gov human mods list approved (speed, phase, attack mode, armor plating, scanner eye, jump boosters; workers alert guards). Gov shop/loot idea SAVED FOR LATER. Ultima pics: user will send when found. Season locks for skills: S3 = Dance Kick, True Empathy, KA Release (check: user said Manifest Greatness = Season 1, Netsu Burst = Season 2). Lunar form: night-only, ALSO counts if DMZ full moon skill used (DMZ "full moon"/moon ability).
- Mon 03:10 (user here) — 0.109.1: skill season locks (Netsu Burst S2; Dance Kick, True Empathy, KA Release S3; shown as ??? when locked).
- Mon 03:20 — Ultima 5 model options + Overload sheet sent (tools/xw/ultima/ultima.py; outputs /home/claude/xw/ultima/out). Waiting on pick. Overload = >100% output: circuit face, white-green eyes, green static (user wants it added).
- Mon 03:25 — Ultima: user picked Option A + bracelet on bare wrist; ultima.bbmodel sent (tools/xw/ultima/bb.py). Next: integrate Ultima (Season 2 locked) when user confirms model; Overload effects; boss later (remind).
- Mon 03:30 — Ultima default face = option 8 (Bored, looking away), NO nose. User may change later: REMIND them when integrating Ultima.
- Mon 04:00 (user here) — 0.110.0: Ultima playable, Season 2 locked (ultima/UltimaOutput, skill/ultima/*, assets via tools/xw/ultima/mod_assets.py, HUD output %). TODO: Ultima boss (remind user), remind about Ultima face (DONE: anime option 8), Lunar form next, then Gov facilities etc.
- Mon 04:05 — STANDING: send GIFs of every new/changed animation from now on (tools/xw/ultima/idle_pics.py has a GIF builder: lerp_pose + U.preview with pose/bends/offset).
- Mon 04:10 (user) ORDER: (1) Gov facilities + healing pod + Gov workers/guards [in progress], (2) Lunar form (S2), (3) bounty boards, (4) named mini-bosses, (5) SR camps. Ultima BOSS: only after more player-Ultima work. THEN: send pictures of all + GIFs: phone animation, Ultima fighting at 25/45/70/100/Overload.
- Mon 04:35 — 0.111.0: Gov facilities (gov/*), healing pod, Gov agents w/ mods, /xeno build gov_facility. NEXT: Lunar form (S2).
- Mon 04:50 — 0.111.1: Ultima boss intro pose (UltimaAnim.perch) + GIF; 20 battle refs saved to docs/refs/ultima_battle. NEXT: Lunar form.
- Mon 05:20 — 0.112.0: Lunar form (lunar/LunarForm, MoonFall, MoonReflectSkill, LunarClient, lunar textures from user art), /xeno lunar unlock|lock, Lunar trial (crouch still 30s under full moon), DMZ fake moon. Spin-off docs summarized in docs/lore. NEXT: bounty boards.
- Mon 06:20 — 0.113-0.114.1: Aurora virus (S2), Distortions + Order of Assassins (S3), Recarnet dimension with Aurora City (S2 barrier), dedicated-server crash fixes, CI selftest (selftest.yml -> 'selftest' release, render with tools/xw/recarnet). NEXT: Mountain Village/Pine Cone City + Kitsune farm + Disco Dojo in Recarnet, then bounty boards, mini-bosses, SR camps, GIFs.
- Mon 06:50 — 0.115-0.117: Pine Cone City + Mountain Village + Kitsune farm + Disco Dojo + canyon, Rhalix virus immunity, hunger (Mikiko x10, Rhalix x3), Ultima Data Sense / Data Ore / armor rules, locked upcoming slots, Collection Book, Demon's Remnants.
- Mon 07:05 — 0.118.0: bot teammates (/xeno bot pick screen, modes, skills via FakePlayer, downed/revive, spar XP w/ hidden 30m cooldown, bot menu, /xeno party menu, BOT tag), Rhalix carry, party XP share + boss loot split, pet whistle 3D + pet HUD, per-character levels. Dedicated-server selftest OK.
- Mon 07:30 — 0.119.0: Room Technology (creative, room dimension), Gale's Hotel + Yuri's HQ in Aurora.
  STILL TODO: #73 model/UI audit w/ before-after images, #75 char models, #76 compat (TACZ/Lodestone optional-vs-required unanswered), #78 more 3D items, max 4 pets limit, bounty boards, mini-bosses, SR camps, GIFs (Demon's Remnants, phone, Ultima styles).
- Mon 13:40 (user here) — 0.120.0: Collection (hostile only, 1000 cap, DMZ), menu Collection tab, Tower mode (tower dim, waves from unfinished, checkpoints every 10 floors, party join, 10 hearts healing/floor), party pet limit 4, TACZ/Lodestone optional + tip.
- Mon 14:05 (away, hourly) — 0.121.0: optional TACZ guns for Gov guards (60%, reflection bridge compat/TaczGuns), 35% Sky-tech rounds (+30% dmg, slow, blue burst). Untested with TACZ installed (CI has no TACZ).
- Mon 14:40-14:55 (user here) — 0.121.1/0.121.2: Lunar moons: hanging grey stars, black moons with glowing purple edge (eyes glow pass, rhalix_lunar_glow.png); flying GIF sent.
- Mon 15:15 (away) — 0.122.0: Bounty boards (bounty/*): 3 daily bounties easy/medium/hard, emeralds + stat XP (+ karma potion/data shard), boards in Mountain Village + Aurora plaza, craftable, /xeno bounty. Lodestone effects DEFERRED (needs client particle packet plumbing per effect; low value vs. queue).

- Mon 16:35 (away) — 0.123.0: named mini-bosses (boss/MiniBosses): 1.5% of monsters (not creepers/Tower), name + title, boss bar, x4 HP, +75% dmg, glow, power (Blazing/Swift/Ironhide/Karma-Eater/Summoner/Frostbound), loot + 50% of a level in each stat; /xeno miniboss.
- Mon 17:50 (away) — 0.124.0: Sky Rebellion camps (sr/*): rare overworld camp (2 black tents, campfire, barrels, loot chest w/ SR hoodie chance, sign), 3-4 SR members (black SR hoodie with white eye, red eyes, leap, demon vessel at 50% HP); SR hoodie = disguise (they won't attack). /xeno build sr_camp. No wings on SR members yet.
- Mon 18:00-18:15 (user here) — 0.124.1 SR wings on demand (tattered, only when flying); 0.124.2 party pets over 4 stay behind (party/PetRest) instead of blocking invites; answers: Tower checkpoint on reaching 10/20 (keep), Collection rewards hostile-only (keep). 0.124.3: Ultima faces redone anime-style (tools/xw/ultima/anime_assets.py), default = new option 8 'Narrow, distant'.
- Mon 18:45-19:00 (user here) — 0.125.0 cel-shading toggle (Settings; client/cel: Cel.tex _cel textures via tools/xw/cel/make_cel.py, inverted-hull outline CelRenderTypes front-cull + inflated PlayerModel/DetailModel.outlineCopy); CI artifact upload made non-fatal (quota). 0.125.1 cel for NPCs. Offered: Mikiko overalls outfit from user's art (orange overalls, black hoodie, red goggles/sneakers) - awaiting answer.
- Mon 19:15 (user asked to start check-in early) — model audit script (tools/xw/ultima/audit.py: off-atlas UVs, invisible cubes, zero-size, duplicate z-fighting cubes) over all character + NPC detail models: 0 issues. Ultima fighting-styles GIF (25/45/70/100/Overload) sent. No jar (no code change).
## Reminders
- When more playable characters exist: disable two players being the same character, but keep a way to switch if nobody else is free in the character screen (user asked to be reminded).
- Away mode resumes 2pm ET Mon Oct 26, check-ins every 1 hour.
