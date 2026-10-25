#!/bin/bash
# Makes sure an Appium server answers at appiumUrl (config.properties); starts
# one if not. A server started here is left running for later builds (Jenkins
# would otherwise kill it when the build ends); its log is ~/appium-ci.log.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

URL="$(cfg appiumUrl)"
URL="${URL:-http://127.0.0.1:4723}"
PORT="$(echo "$URL" | sed -E 's#.*:([0-9]+).*#\1#')"

ready() {
  curl -s --max-time 3 "$URL/status" | grep -q '"ready":true'
}

if ready; then
  log "Appium already running at $URL"
  exit 0
fi

log "starting Appium on port $PORT"
JENKINS_NODE_COOKIE=dontKillMe BUILD_ID=dontKillMe \
  nohup appium --port "$PORT" > "$HOME/appium-ci.log" 2>&1 &

for _ in $(seq 1 30); do
  if ready; then
    log "Appium ready"
    exit 0
  fi
  sleep 1
done
echo "Appium did not start; see $HOME/appium-ci.log" >&2
exit 1
