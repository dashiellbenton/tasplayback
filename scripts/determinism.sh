#!/usr/bin/env bash
# Hard requirement 1: the same scripted input with recording OFF, OFF again (vanilla baseline) and ON must
# end in the same player state. Also reports RNG tripwire violations of the recorder.
#   scripts/determinism.sh <gradle-node> <run-dir> <logdir> [extra gradle args...]
set -u
node="$1"; rundir="$2"; logs="$3"; shift 3
mkdir -p "$logs"
rm -rf "$rundir/tasplayback"
scripts/devtest.sh "$node" "$logs/off1.log" "-Dtasplayback.devtest=off1 -Dtasplayback.tripwire=true" "$@"
scripts/devtest.sh "$node" "$logs/off2.log" "-Dtasplayback.devtest=off2 -Dtasplayback.tripwire=true" "$@"
scripts/devtest.sh "$node" "$logs/on.log" "-Dtasplayback.devtest=on -Dtasplayback.autoRecord=true -Dtasplayback.tripwire=true" "$@"
python3 - "$rundir/tasplayback" <<'PY'
import json, sys
d = sys.argv[1]
s = {k: json.load(open(f"{d}/devtest-{k}.json")) for k in ("off1", "off2", "on")}
ignore = {"label", "recording", "levelGameTime", "tripwireViolations"}
def strip(x): return {k: v for k, v in x.items() if k not in ignore}
base_ok = strip(s["off1"]) == strip(s["off2"])
rec_ok = strip(s["off1"]) == strip(s["on"])
for k, v in s.items(): print(k, json.dumps(strip(v)))
print("vanilla run-to-run identical:", base_ok)
print("recording ON identical to OFF:", rec_ok)
tw = s["on"]["tripwireViolations"]
print("RNG tripwire while recording:", "not measured on this node" if tw < 0 else f"{tw} violations (recorder capture code drew from a game RNG)")
sys.exit(0 if (base_ok and rec_ok and tw <= 0) else 1)
PY
