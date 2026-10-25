#!/bin/bash
# Boots the configured simulator and installs the freshly built app on it.
#
#   ci/prepare-simulator.sh <path/to/iOS Nutrition App.app>
#
# Installing over the old app keeps its data; the suite restores its own
# onboarded baseline, keyed by a hash of the new build's code.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

APP="${1:?path to the built .app}"
UDID="$(sim_udid)"

log "booting simulator $UDID"
xcrun simctl boot "$UDID" 2>/dev/null || true   # already booted is fine
xcrun simctl bootstatus "$UDID" -b >/dev/null

log "installing $(basename "$APP")"
xcrun simctl install "$UDID" "$APP"
