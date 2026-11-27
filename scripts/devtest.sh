#!/usr/bin/env bash
# Runs one scripted dev-client session headless and waits for it to exit.
#   scripts/devtest.sh <gradle-node> <log> "<client JVM args>" [extra gradle args...]
set -u
node="$1"; log="$2"; jvm="$3"; shift 3
display=":$((50 + RANDOM % 9))"
rm -f "/tmp/.X${display#:}-lock" "/tmp/.X11-unix/X${display#:}" 2>/dev/null
Xvfb "$display" -screen 0 1280x720x24 -nolisten tcp >/dev/null 2>&1 &
xvfb=$!
trap 'kill $xvfb 2>/dev/null' EXIT
for _ in $(seq 1 50); do [ -S "/tmp/.X11-unix/X${display#:}" ] && break; sleep 0.2; done
export LIBGL_ALWAYS_SOFTWARE=1 SDL_VIDEO_FORCE_EGL=1
DISPLAY="$display" timeout "${DEVTEST_TIMEOUT:-900}" ./gradlew "$@" "$node:runClient" -PtaspJvm="$jvm" --console=plain >"$log" 2>&1
echo "exit $?"
