#!/usr/bin/env python3
"""CI only: download the user's real mod pack (tools/pack/pack.json) from the mods' official sources at test time.

usage: fetch_pack.py <pack.json> <mods dir> <client|server> <report file>

For each mod: a direct official URL if listed; else the exact jar (same file name) from Modrinth (the listed
projects, then a search); else, for the listed projects only, the same version number; else CurseForge's CDN (exact
file name only). Mods that can't be fetched automatically are skipped and reported. Nothing here is committed.
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

UA = {"User-Agent": "amenxl/xeno-warriors-mod pack-test (CI)"}
MC = "1.20.1"


def get(url, tries=3):
    for i in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code in (404, 410):
                raise
            err = e
        except Exception as e:  # network hiccup: retry
            err = e
        time.sleep(3 * (i + 1))
    raise err


def jget(url):
    return json.loads(get(url))


def modrinth_versions(slug):
    q = urllib.parse.quote
    try:
        return jget(f"https://api.modrinth.com/v2/project/{q(slug)}/version?loaders={q(json.dumps(['forge']))}&game_versions={q(json.dumps([MC]))}")
    except Exception:
        return []


def modrinth_search(query):
    facets = urllib.parse.quote(json.dumps([["categories:forge"], [f"versions:{MC}"]]))
    try:
        return [h["slug"] for h in jget(f"https://api.modrinth.com/v2/search?query={urllib.parse.quote(query)}&facets={facets}&limit=8")["hits"]]
    except Exception:
        return []


def find_modrinth(m):
    want = m["file"].lower()
    listed = m.get("modrinth", [])
    slugs = list(listed)
    if m.get("search"):
        slugs += [s for s in modrinth_search(m["search"]) if s not in slugs]
    cache = {}
    for slug in slugs:                                      # 1. the exact file
        vs = cache.setdefault(slug, modrinth_versions(slug))
        for v in vs:
            for f in v["files"]:
                if f["filename"].lower() == want:
                    return "exact", f"modrinth:{slug} {v['version_number']}", f["url"], f["filename"]
    match = m.get("match")
    for slug in listed:                                     # 2. the same version number (listed projects only)
        for v in cache.get(slug) or modrinth_versions(slug):
            if match and (match in v["version_number"] or any(match in f["filename"] for f in v["files"])) and "neoforge" not in v["version_number"].lower():
                f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
                return "near", f"modrinth:{slug} {v['version_number']}", f["url"], f["filename"]
    return None


def find_curseforge(m):
    q = m.get("curseforge")
    if not q:
        return None
    want = m["file"].lower()
    try:
        hits = jget("https://www.curseforge.com/api/v1/mods/search?gameId=432&index=0&classId=6&pageSize=20&sortField=1&filterText=" + urllib.parse.quote(q))["data"]
    except Exception as e:
        print(f"  curseforge search failed for {q}: {e}")
        return None
    for h in hits[:8]:
        for page in range(6):
            try:
                files = jget(f"https://www.curseforge.com/api/v1/mods/{h['id']}/files?pageIndex={page}&pageSize=50&sort=dateCreated&sortDescending=true&removeAlphas=false")["data"]
            except Exception:
                break
            for f in files:
                if f.get("fileName", "").lower() == want:
                    fid = int(f["id"])
                    url = f"https://mediafilez.forgecdn.net/files/{fid // 1000}/{fid % 1000}/{urllib.parse.quote(f['fileName'])}"
                    return "exact", f"curseforge:{h.get('slug', h['id'])} file {fid}", url, f["fileName"]
            if len(files) < 50:
                break
    return None


def main():
    pack, out, side, report = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    os.makedirs(out, exist_ok=True)
    spec = json.load(open(pack))
    rows = []
    for m in spec["mods"]:
        name = m["name"]
        if side == "server" and m.get("side") == "client":
            rows.append((name, "client-only", "-", m["file"], "left out of the dedicated server"))
            continue
        if m.get("skip"):
            rows.append((name, "SKIPPED", "-", m["file"], m["skip"]))
            continue
        found = None
        if m.get("url"):
            try:
                data = get(m["url"])
                open(os.path.join(out, m["file"]), "wb").write(data)
                rows.append((name, "exact", m["url"].split("/")[2], m["file"], ""))
                print(f"PACK fetched {name}: {m['file']} from {m['url']}")
                continue
            except Exception as e:
                print(f"  {name}: direct url failed ({e}), trying Modrinth")
        found = find_modrinth(m) or find_curseforge(m)
        if not found:
            rows.append((name, "SKIPPED", "-", m["file"], "not found on Modrinth or CurseForge's CDN"))
            print(f"PACK SKIP {name}: not found")
            continue
        kind, src, url, fn = found
        try:
            data = get(url)
        except Exception as e:
            rows.append((name, "SKIPPED", src, fn, f"download failed: {e}"))
            print(f"PACK SKIP {name}: download failed {e}")
            continue
        open(os.path.join(out, fn), "wb").write(data)
        rows.append((name, kind, src, fn, "" if kind == "exact" else f"pack has {m['file']}"))
        print(f"PACK fetched {name}: {fn} ({kind}, {src})")
    with open(report, "w") as f:
        f.write(f"# {side} mods (Forge {spec['forge']}, Minecraft {spec['minecraft']})\n")
        for r in rows:
            f.write("\t".join(r) + "\n")
    print(open(report).read())


if __name__ == "__main__":
    main()
