#!/bin/bash
# Packs the Allure results into one self-contained HTML file to forward to people
# without Jenkins access: it opens by double-click in any browser, offline. A .zip
# of it is written next to it, the copy to click in Jenkins (it downloads).
# Never fails the build: no results or no Allure CLI just means no file.
#
#   ci/report-file.sh <output.html>   (results are read from allure-results/)

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

OUT="${1:?output .html path}"
cd "$ROOT"
if [ ! -d allure-results ] || [ -z "$(ls -A allure-results 2>/dev/null)" ]; then
  log "no Allure results; no report file"
  exit 0
fi
if ! command -v allure >/dev/null; then
  log "Allure CLI not found (brew install allure); no report file"
  exit 0
fi
TMP="$(mktemp -d)"
if allure generate --single-file allure-results -o "$TMP" --clean >/dev/null 2>&1 && [ -f "$TMP/index.html" ]; then
  mkdir -p "$(dirname "$OUT")"
  mv "$TMP/index.html" "$OUT"
  log "report file: $OUT"
  # Jenkins opens an .html artifact in the browser with its scripts blocked (a blank
  # page), but a .zip is always downloaded: the zip is the one to click.
  (cd "$(dirname "$OUT")" && zip -q -j "$(basename "$OUT" .html).zip" "$(basename "$OUT")") \
    && log "zipped: ${OUT%.html}.zip"
else
  log "Allure could not build the report file"
fi
rm -rf "${TMP:?}"
exit 0
