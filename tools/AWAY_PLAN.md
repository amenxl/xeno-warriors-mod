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
7. Dragon Ball outfit compatibility: full 3D, no clipping, both characters (start with the most common gis).
8. Epic Fight weapon movesets for DMZ weapons (power pole etc.), per character.
9. Enemy animation polish (humans, possessed, corrupted, sky haters).
10. Humans/Skylanders gather, mine, build, craft gear (staged: gather → build small huts → craft armor).
11. 3D armor polish (sky/corrupted), then vanilla armor in 3D.
12. Character blueprints from the S3 script: model sheets + skill lists (send as pictures/doc).
13. Rhalix outfits accuracy pass with alternates for the user to pick.

## Log
- Fri 05:30 — plan set up, folder access granted, tools backed up.
- Fri 06:10 — 0.88.0: Rhalix hair black only at the tips (options A/B/C sent, A in game; rhalix.py HAIR_STYLE env), Skylander wing variety (size/tint/spread per UUID, guardians big golden).
- Fri 11:20 — 0.89.0: Sky/Corrupted tool sets (effects: sky +35% vs airborne & faster digging above y150; corrupted lifesteal/wither & faster below y0). Islands bigger (r 7-16, centered in chunk), island Skylanders circle/land on their own island, attack anyone entering their house or breaking/opening near home.
- Fri 15:50 — user picked hair C → 0.89.1; sent wing variant sheet. (Device file tools disconnected: jar not copied to Documents.)
- Fri 16:30 — 0.90.0: 8 Skylander wing styles as separate models (angel, hawk, falcon, bat, butterfly, crystal, seraph, tattered), picked per UUID (1/9 classic), guardians seraph; sheet sent.
- Fri 21:25 — 0.91.0: Sky Hater sieges (random near island Skylanders, 2-day cooldown per island): builders build cobble+ladder tower w/ plank platforms, foreman rides top, bridge across, climb and attack Skylanders; /xeno siege (op).
- Sat 02:35 — 0.92.0: HudLayout: top-right stack under status effects (request card, then toasts), lock-on bar under boss bars, level box left of offhand slot, party HUD kept above chat, skill bar raised 34px with Epic Fight. Layout diagram sent.
