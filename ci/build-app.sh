#!/bin/bash
# Builds the app from a checkout of the app repo.
#
#   ci/build-app.sh simulator <app-repo-dir> <out-dir>   -> prints the .app path
#   ci/build-app.sh device    <app-repo-dir> <out-dir>   -> prints the .ipa path
#
# simulator: Debug build for the configured simulator; no signing needed.
# device:    archive + development export, signed with the team in
#            config.properties (real.xcodeOrgId) through Xcode's automatic signing
#            (Xcode must be signed in to that team on this Mac).
# Full xcodebuild output goes to <out-dir>/build-<target>.log.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

TARGET="${1:?simulator or device}"
APP_DIR="$(cd "${2:?app repo dir}" && pwd)"
OUT="${3:?output dir}"
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
PROJECT="$APP_DIR/iOS Nutrition App.xcodeproj"
SCHEME="${APP_SCHEME:-iOS Nutrition App}"
LOG="$OUT/build-$TARGET.log"

case "$TARGET" in
  simulator)
    UDID="$(sim_udid)"
    log "building '$SCHEME' for simulator $UDID (log: $LOG)"
    xcodebuild -project "$PROJECT" -scheme "$SCHEME" -configuration Debug \
      -destination "id=$UDID" -derivedDataPath "$OUT/DerivedData" \
      -skipPackagePluginValidation -skipMacroValidation \
      build > "$LOG" 2>&1 || { tail -40 "$LOG" >&2; exit 1; }
    APP="$OUT/DerivedData/Build/Products/Debug-iphonesimulator/iOS Nutrition App.app"
    [ -d "$APP" ] || { echo "Build finished but no app at $APP" >&2; exit 1; }
    echo "$APP"
    ;;
  device)
    TEAM="$(cfg real.xcodeOrgId)"
    [ -n "$TEAM" ] || { echo "real.xcodeOrgId is not set in config.properties" >&2; exit 1; }
    log "archiving '$SCHEME' for iOS devices, team $TEAM (log: $LOG)"
    xcodebuild -project "$PROJECT" -scheme "$SCHEME" -destination "generic/platform=iOS" \
      -archivePath "$OUT/iOS Nutrition App.xcarchive" -derivedDataPath "$OUT/DerivedData-device" \
      -allowProvisioningUpdates DEVELOPMENT_TEAM="$TEAM" \
      -skipPackagePluginValidation -skipMacroValidation \
      archive > "$LOG" 2>&1 || { tail -40 "$LOG" >&2; exit 1; }
    cat > "$OUT/ExportOptions.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key><string>debugging</string>
  <key>teamID</key><string>$TEAM</string>
  <key>signingStyle</key><string>automatic</string>
  <key>destination</key><string>export</string>
</dict>
</plist>
PLIST
    log "exporting a development .ipa"
    xcodebuild -exportArchive -archivePath "$OUT/iOS Nutrition App.xcarchive" -exportPath "$OUT/ipa" \
      -exportOptionsPlist "$OUT/ExportOptions.plist" -allowProvisioningUpdates \
      >> "$LOG" 2>&1 || { tail -40 "$LOG" >&2; exit 1; }
    IPA="$(ls "$OUT"/ipa/*.ipa 2>/dev/null | head -1)"
    [ -n "$IPA" ] || { echo "Export finished but no .ipa in $OUT/ipa" >&2; exit 1; }
    echo "$IPA"
    ;;
  *)
    echo "usage: ci/build-app.sh simulator|device <app-repo-dir> <out-dir>" >&2
    exit 2
    ;;
esac
