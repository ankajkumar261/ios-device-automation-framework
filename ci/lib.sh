#!/bin/bash
# Shared helpers for the CI scripts. Device and signing details come from the
# framework's own configuration (src/test/resources/config.properties and
# devices/<name>.properties) — nothing device-specific is written here.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="$ROOT/src/test/resources/config.properties"
DEVICES="$ROOT/src/test/resources/devices"

# Value of <key> in config.properties ("" when unset).
cfg() {
  grep -E "^$1=" "$CONFIG" | tail -1 | cut -d= -f2- || true
}

# Value of <key> in device profile <name>.
profile_value() {
  grep -E "^$2=" "$DEVICES/$1.properties" | tail -1 | cut -d= -f2- || true
}

# UDID of the simulator the suite drives (simulator.device's profile).
sim_udid() {
  local profile
  profile="$(cfg simulator.device)"
  [ -n "$profile" ] || { echo "simulator.device is not set in config.properties" >&2; return 1; }
  profile_value "$profile" udid
}

# UDID of the iPhone on a USB cable; empty when none. With several, the first by
# name (the same rule as the framework's real.device=auto); an iPad never counts.
wired_device_udid() {
  local json
  json="$(mktemp)"
  xcrun devicectl list devices --json-output "$json" >/dev/null 2>&1 || { rm -f "$json"; return 0; }
  python3 - "$json" <<'PY'
import json, sys
devices = json.load(open(sys.argv[1])).get("result", {}).get("devices", [])

def get(d, *paths):
    for path in paths:
        cur = d
        for key in path:
            if not isinstance(cur, dict):
                cur = None
                break
            cur = cur.get(key)
        if cur:
            return cur
    return None

def is_usable_iphone(d):
    device_type = get(d, ("properties", "hardware", "deviceType"),
                         ("hardwareProperties", "deviceType"))
    if device_type == "iPad":
        return False
    if device_type != "iPhone":
        return False
    transport = get(d, ("properties", "connection", "transportType"),
                       ("connectionProperties", "transportType"))
    if transport != "wired":
        return False
    pairing = get(d, ("properties", "connection", "pairingState"),
                     ("connectionProperties", "pairingState"))
    if pairing != "paired":
        return False
    # Xcode 27 / iOS 27 omits `reality` for newer phones (deprecated fields).
    # Reject simulators, accept missing reality as physical.
    reality = get(d, ("properties", "hardware", "reality"),
                     ("hardwareProperties", "reality"))
    if reality is not None and reality != "physical":
        return False
    return True

wired = sorted(
    (get(d, ("properties", "state", "name"),
            ("deviceProperties", "name")) or "",
     get(d, ("properties", "hardware", "udid"),
            ("hardwareProperties", "udid")))
    for d in devices if is_usable_iphone(d) and get(d, ("properties", "hardware", "udid"),
            ("hardwareProperties", "udid")))
if len(wired) > 1:
    print("[ci] several iPhones on USB: %s; testing %s" % (", ".join(n for n, _ in wired), wired[0][0]),
          file=sys.stderr)
print(wired[0][1] if wired else "")
PY
  rm -f "$json"
}

# Progress goes to stderr, so scripts can print a result (a path) on stdout.
log() {
  echo "[ci] $*" >&2
}
