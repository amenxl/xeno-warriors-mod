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
