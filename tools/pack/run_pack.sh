#!/bin/bash
# CI only: run Xeno Warriors inside the user's real mod pack (tools/pack/pack.json), the way players run it: the
# reobfuscated release jar in a real Forge install (not the dev environment), the other mods downloaded from their
# official sources at test time (never committed).
#
#   tools/pack/run_pack.sh server   dedicated server with the server-side mods: XW-SELFTEST (Recarnet generation), save, stop
#   tools/pack/run_pack.sh client   the same server makes a world, then the real client joins it with the whole pack and
#                                   runs the "pack" UI smoke test (client/test/PackTests.java), then quits
#
# Run from the repo root. Writes server.log / client.log there, screenshots to run/screenshots, and the rest
# (mods fetched, Forge logs, crash reports, pack-report-*.txt from scan_log.py) to out/.
set -u
MODE=${1:-client}
ROOT=$(pwd)
P=$ROOT/packrun
OUT=$ROOT/out
mkdir -p "$P" "$OUT"
JAVA=$(command -v java)
MC=$(python3 -I -c 'import json,sys;print(json.load(open(sys.argv[1]))["minecraft"])' tools/pack/pack.json)
FORGE=$(python3 -I -c 'import json,sys;print(json.load(open(sys.argv[1]))["forge"])' tools/pack/pack.json)

# ---- our jar: the real reobfuscated build
chmod +x gradlew
if ! ./gradlew build -x test --no-daemon > "$OUT/build.log" 2>&1; then tail -60 "$OUT/build.log"; echo "PACK build failed"; exit 1; fi
XW=$(ls build/libs/xenowarriors-*.jar | grep -v -E -- '-(sources|dev|slim)\.jar' | head -1)
echo "PACK our jar: $XW"

# ---- Forge installer (the pack's exact version; the newest 47.4.x if that one is ever gone)
INST=$P/forge-installer.jar
if ! curl -fsSL -o "$INST" "https://maven.minecraftforge.net/net/minecraftforge/forge/$MC-$FORGE/forge-$MC-$FORGE-installer.jar"; then
  LATEST=$(curl -fsSL https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml | grep -o "<version>$MC-47\.4\.[0-9]*</version>" | sed 's/<[^>]*>//g' | sort -V | tail -1)
  echo "PACK Forge $FORGE not found, using ${LATEST#$MC-}"
  FORGE=${LATEST#$MC-}
  curl -fsSL -o "$INST" "https://maven.minecraftforge.net/net/minecraftforge/forge/$MC-$FORGE/forge-$MC-$FORGE-installer.jar" || { echo "PACK no Forge installer"; exit 1; }
fi
echo "PACK Forge $MC-$FORGE"

# ---- dedicated server (server-side subset of the pack)
S=$P/server
rm -rf "$S"; mkdir -p "$S/mods"
python3 -I tools/pack/fetch_pack.py tools/pack/pack.json "$S/mods" server "$OUT/mods-server.tsv"
cp "$XW" "$S/mods/"
(cd "$S" && "$JAVA" -jar "$INST" --installServer . > "$OUT/forge-install-server.log" 2>&1) || { tail -30 "$OUT/forge-install-server.log"; echo "PACK server install failed"; exit 1; }
echo "eula=true" > "$S/eula.txt"
printf "online-mode=false\nlevel-seed=xenowarriors\nspawn-protection=0\nmax-tick-time=-1\n" > "$S/server.properties"
echo "-Xmx3G" > "$S/user_jvm_args.txt"
echo "PACK starting the dedicated server"
(cd "$S" && XW_SELFTEST=1 XW_PACKTEST=1 timeout 1500 bash ./run.sh nogui < /dev/null > "$ROOT/server.log" 2>&1) || true
cp "$S/logs/latest.log" "$OUT/server-latest.log" 2>/dev/null || true
cp "$S/logs/debug.log" "$OUT/server-debug.log" 2>/dev/null || true
for f in "$S"/crash-reports/*.txt; do [ -f "$f" ] && cp "$f" "$OUT/server-$(basename "$f")"; done
python3 -I tools/pack/scan_log.py "$S/logs/latest.log" "$S/logs/debug.log" > "$OUT/pack-report-server.txt" 2>&1 || true
head -c 30000 "$OUT/pack-report-server.txt"
for f in "$S"/crash-reports/*.txt; do [ -f "$f" ] && head -80 "$f"; done
grep -E "XW-SELFTEST|XW-TEST pack" "$ROOT/server.log" | head -40
# (the Recarnet region files, for the selftest release)
mkdir -p run && rm -rf run/world && cp -r "$S/world" run/world 2>/dev/null || true
if [ "$MODE" = server ]; then grep -q "XW-SELFTEST OK" "$ROOT/server.log"; exit $?; fi

# ---- client: the whole pack, joining the world the server just made
C=$P/client
rm -rf "$C"; mkdir -p "$C/mods" "$C/saves"
python3 -I tools/pack/fetch_pack.py tools/pack/pack.json "$C/mods" client "$OUT/mods-client.tsv"
cp "$XW" "$C/mods/"
cp -r "$S/world" "$C/saves/uitest"
# no first-run screens; keep running without window focus
printf "onboardAccessibility:false\npauseOnLostFocus:false\nnarrator:0\ntutorialStep:none\nskipMultiplayerWarning:true\njoinedFirstServer:true\nrenderDistance:8\nsimulationDistance:8\nsoundCategory_master:0.0\n" > "$C/options.txt"
# focus "packshaders": the same with an Oculus shader pack on (MakeUp - Ultra Fast from Modrinth; PACK_SHADER = another slug)
case "${XW_UITEST_FOCUS:-pack}" in *shaders*)
  SLUG=${PACK_SHADER:-makeup-ultra-fast-shaders}
  mkdir -p "$C/shaderpacks" "$C/config"
  SURL=$(curl -fsSL -A "amenxl/xeno-warriors-mod pack-test (CI)" "https://api.modrinth.com/v2/project/$SLUG/version?game_versions=%5B%22$MC%22%5D" \
    | python3 -I -c 'import json,sys; v=json.load(sys.stdin)[0]; f=next((x for x in v["files"] if x["primary"]), v["files"][0]); print(f["url"] + " " + f["filename"])')
  SFILE=${SURL#* }
  curl -fsSL -o "$C/shaderpacks/$SFILE" "${SURL%% *}" && echo "PACK shader pack: $SFILE"
  printf "enableShaders=true\nshaderPack=%s\n" "$SFILE" | tee "$C/config/oculus.properties" > "$C/config/iris.properties"
  ;;
esac
python3 -m pip install -q "portablemc>=4.3,<5" || { echo "PACK can't install portablemc"; exit 1; }
PMC="python3 -m portablemc --main-dir $P/mc --work-dir $C"
echo "PACK installing the Forge client"
$PMC start --dry "forge:$MC-$FORGE" > "$OUT/forge-install-client.log" 2>&1 || { tail -40 "$OUT/forge-install-client.log"; $PMC start --help; echo "PACK client install failed"; exit 1; }
SCREEN="1280x720x24"
echo "PACK starting the client"
XW_UITEST=1 XW_UITEST_FOCUS=${XW_UITEST_FOCUS:-pack} XW_UITEST_WORLD=uitest XW_PACKTEST=1 LIBGL_ALWAYS_SOFTWARE=1 \
  timeout 2400 xvfb-run -a -s "-screen 0 $SCREEN" $PMC start "forge:$MC-$FORGE" --jvm "$JAVA" --jvm-args="-Xmx4G -XX:+UseG1GC" \
  --resolution 1280x720 -u XwTester > "$ROOT/client.log" 2>&1 &
CPID=$!
# a crash during loading leaves Forge's error screen open: stop as soon as a crash report appears
while kill -0 $CPID 2>/dev/null; do
  if ls "$C"/crash-reports/*.txt > /dev/null 2>&1; then echo "PACK client crashed, stopping it"; sleep 15; pkill -f "$P/mc" ; sleep 5; kill $CPID 2>/dev/null; break; fi
  sleep 10
done
wait $CPID 2>/dev/null || true
cp "$C/logs/latest.log" "$OUT/client-latest.log" 2>/dev/null || true
cp "$C/logs/debug.log" "$OUT/client-debug.log" 2>/dev/null || true
for f in "$C"/crash-reports/*.txt; do [ -f "$f" ] && cp "$f" "$OUT/client-$(basename "$f")"; done
for f in "$C"/hs_err_pid*.log; do [ -f "$f" ] && { cp "$f" "$OUT/client-$(basename "$f")"; echo "PACK the client JVM crashed:"; head -40 "$f"; grep -m5 -E "^(C|J)  " "$f"; }; done
tail -5 "$ROOT/client.log"
mkdir -p run/screenshots && cp "$C"/screenshots/*.png run/screenshots/ 2>/dev/null || true
python3 -I tools/pack/scan_log.py "$C/logs/latest.log" "$C/logs/debug.log" > "$OUT/pack-report-client.txt" 2>&1 || true
head -c 40000 "$OUT/pack-report-client.txt"
for f in "$C"/crash-reports/*.txt; do [ -f "$f" ] && head -80 "$f"; done
grep -E "XW-UITEST (start|done)|XW-TEST pack" "$ROOT/client.log" | head -150
grep -q "XW-UITEST done" "$ROOT/client.log"
