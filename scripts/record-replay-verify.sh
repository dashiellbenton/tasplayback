#!/usr/bin/env bash
# Scripted record -> replay -> compare, on one node. Prints the verify report and the devtest state.
#   scripts/record-replay-verify.sh <gradle-node> <run-dir> <logdir> [extra gradle args...]
set -u
node="$1"; rundir="$2"; logs="$3"; shift 3
mkdir -p "$logs"
rm -rf "$rundir/tasplayback"
common="-Dtasplayback.cleanOutput=true -Dtasplayback.debugImages=${DEBUG_IMAGES:-0}"
scripts/devtest.sh "$node" "$logs/record.log" "-Dtasplayback.devtest=rec -Dtasplayback.autoRecord=true -Dtasplayback.tripwire=true -Dtasplayback.verifyEvery=10 $common" "$@"
scripts/devtest.sh "$node" "$logs/replay.log" "-Dtasplayback.autoplay=latest -Dtasplayback.stepFrames=true -Dtasplayback.exitAfterReplay=true -Dtasplayback.noCursor=true $common" "$@"
grep -hE "TRIPWIRE|Exception" "$logs/record.log" "$logs/replay.log" | grep -v "OpenAL\|SignedJWT" | head -5
cat "$rundir/tasplayback/devtest-rec.json"
python3 -c "import json,sys; r=json.load(open(sys.argv[1])); print('compared %d frames: mean diff avg %.2f, max %.2f' % (r['comparedFrames'], r['meanAbsDiffAverage'], r['meanAbsDiffMax'])); print('per frame:', ' '.join('%.1f' % d for d in r['diffs']))" "$rundir/tasplayback/verify-report.json"
