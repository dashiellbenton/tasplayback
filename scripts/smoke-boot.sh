#!/usr/bin/env bash
# Boots one node's dev client headless (Xvfb + Mesa llvmpipe), waits until TASPlayback's
# init log line AND mixin log line appear (or a failure / timeout), screenshots the
# window, then stops the client.
#
#   scripts/smoke-boot.sh <gradle-project-path> <out-dir> [extra gradle args...]
#   e.g. scripts/smoke-boot.sh :1.21.11 build/smoke
#
# Exit 0 = both log lines seen. Exit 1 = failure or timeout.
set -u
node="$1"; out="$2"; shift 2
mkdir -p "$out"
name="${node##*:}"
log="$out/$name.log"
display="${SMOKE_DISPLAY:-:$((90 + RANDOM % 9))}"
screen="${SMOKE_SCREEN:-1280x720x24}"
timeout_s="${SMOKE_TIMEOUT:-900}"

export LIBGL_ALWAYS_SOFTWARE=1
# 26.x (SDL3) asks for an sRGB-capable framebuffer; Xvfb's GLX visuals have none, Mesa's EGL configs do.
export SDL_VIDEO_FORCE_EGL="${SDL_VIDEO_FORCE_EGL:-1}"
# LWJGL2 (1.12.2) needs the `xrandr` binary to enumerate display modes.
command -v xrandr >/dev/null || echo "warning: xrandr not installed; 1.12.2 clients will crash at Display init" >&2
rm -f "/tmp/.X${display#:}-lock" "/tmp/.X11-unix/X${display#:}" 2>/dev/null
Xvfb "$display" -screen 0 "$screen" +extension GLX +extension RENDER -nolisten tcp >"$out/$name.xvfb.log" 2>&1 &
xvfb_pid=$!
trap 'kill $xvfb_pid 2>/dev/null' EXIT
for _ in $(seq 1 50); do [ -S "/tmp/.X11-unix/X${display#:}" ] && break; sleep 0.2; done
if ! kill -0 "$xvfb_pid" 2>/dev/null; then echo "SMOKE FAIL $node (Xvfb did not start, see $out/$name.xvfb.log)"; exit 1; fi

DISPLAY="$display" ./gradlew "$@" "$node:runClient" --console=plain >"$log" 2>&1 &
gradle_pid=$!

result=1
deadline=$((SECONDS + timeout_s))
while (( SECONDS < deadline )); do
  if grep -q "TASPlayback .* loaded (loader=" "$log" && grep -q "TASPlayback mixins active" "$log"; then
    sleep "${SMOKE_SETTLE:-25}"   # let it reach the title screen
    DISPLAY="$display" import -window root "$out/$name.png" 2>/dev/null
    # Still alive and no crash report after settling = success.
    if kill -0 "$gradle_pid" 2>/dev/null && ! grep -q -- "---- Minecraft Crash Report ----" "$log"; then result=0; fi
    break
  fi
  if ! kill -0 "$gradle_pid" 2>/dev/null; then break; fi
  if grep -qE "Mixin apply failed|InvalidMixinException|---- Minecraft Crash Report ----" "$log"; then break; fi
  sleep 2
done

# Stop the client JVM (child of the Gradle daemon) and the gradle client.
pkill -f "^[^ ]*/java .*(devlaunchinjector|net\.fabricmc\.loader\.impl\.launch\.knot|GradleStart)" 2>/dev/null
kill "$gradle_pid" 2>/dev/null
wait "$gradle_pid" 2>/dev/null
if (( result == 0 )); then echo "SMOKE OK   $node"; else echo "SMOKE FAIL $node (see $log)"; fi
exit $result
