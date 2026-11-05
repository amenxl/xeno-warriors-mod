#!/usr/bin/env python3
"""CI only: look through a Minecraft/Forge log for trouble after a pack test.

usage: scan_log.py <log> [<log> ...]

Prints every ERROR/FATAL line, every mixin problem (apply failed, conflicts, skipped injections, missing refmap),
every WARN line that names Xeno Warriors, and each exception whose stack trace runs through our code; then a one-line
summary: "PACK-SCAN <log>: errors=N fatal=N mixin=N xw_warn=N xw_traces=N crash=yes/no".
"""
import re
import sys

LEVEL = re.compile(r"/(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\]")
MIXIN_BAD = re.compile(r"(apply failed|InvalidInjection|InvalidMixin|Critical injection failure|injection failure|conflict|Skipping|"
                       r"could not be read|refmap|Reference map|MixinApplyError|MixinTransformerError|was not located|No refMap loaded)", re.I)
OURS = re.compile(r"xenowarriors|com\.xenowarriors", re.I)
# known, harmless lines (vanilla / Forge noise in a headless CI run)
NOISE = re.compile(r"(Failed to (load|verify) .*(narrator|Narrator)|Unable to load library 'flite'|OpenAL|Failed to open OpenAL device|"
                   r"realms|Realms|authlib|YggdrasilAuthentication|Failed to fetch user properties|"
                   r"No key layouts|ALSA|Couldn't load .*options|Missing sound for event)")


def scan(path):
    try:
        lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
    except OSError as e:
        print(f"PACK-SCAN {path}: missing ({e})")
        return
    errors, fatal, mixin, xwwarn, traces = [], [], [], [], []
    crash = False
    i = 0
    while i < len(lines):
        ln = lines[i]
        m = LEVEL.search(ln)
        lvl = m.group(1) if m else None
        if "---- Minecraft Crash Report ----" in ln or "Crash report saved to" in ln:
            crash = True
        if lvl in ("ERROR", "FATAL") and not NOISE.search(ln):
            (fatal if lvl == "FATAL" else errors).append(ln)
        if "mixin" in ln.lower() and MIXIN_BAD.search(ln) and lvl != "DEBUG" and lvl != "TRACE" and "xw-test" not in ln.lower():
            mixin.append(ln)
        if lvl == "WARN" and OURS.search(ln) and "XW-TEST" not in ln:
            xwwarn.append(ln)
        # a stack trace: the exception line, then "at ..." / "Caused by" lines
        if (re.search(r"(Exception|Error|Throwable)(:|$)", ln) and not lvl) or ln.startswith("Caused by"):
            j = i + 1
            block = [ln]
            while j < len(lines) and (lines[j].startswith("\tat ") or lines[j].startswith("Caused by") or lines[j].startswith("\t... ")
                                      or lines[j].startswith("\tSuppressed")):
                block.append(lines[j]); j += 1
            if any("com.xenowarriors" in b for b in block):
                traces.append("\n".join([lines[i - 1] if i > 0 else ""] + block[:40]))
            i = j
            continue
        i += 1
    print(f"===== {path}")
    for title, items in (("FATAL", fatal), ("ERROR", errors), ("MIXIN", mixin), ("XW WARN", xwwarn)):
        if items:
            print(f"--- {title} ({len(items)})")
            seen = set()
            for x in items:
                key = re.sub(r"^\[[^\]]*\] ", "", x)
                if key in seen:
                    continue
                seen.add(key)
                print(x[:600])
    if traces:
        print(f"--- STACK TRACES THROUGH com.xenowarriors ({len(traces)})")
        for t in traces[:30]:
            print(t)
            print()
    print(f"PACK-SCAN {path}: errors={len(errors)} fatal={len(fatal)} mixin={len(mixin)} xw_warn={len(xwwarn)} xw_traces={len(traces)} crash={'yes' if crash else 'no'}")


if __name__ == "__main__":
    for p in sys.argv[1:]:
        scan(p)
