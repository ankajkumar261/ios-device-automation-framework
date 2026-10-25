#!/bin/bash
# Runs one test suite and keeps its reports under reports/<label>/ (the next
# Maven run would overwrite target/). Test failures do not fail this script —
# Jenkins marks the build UNSTABLE from the JUnit reports; a build or setup
# error still does.
#
#   ci/run-tests.sh <label> [maven args...]
#   e.g. ci/run-tests.sh simulator-smoke -Psmoke

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

LABEL="${1:?label}"
shift
DEST="$ROOT/reports/$LABEL"

cd "$ROOT"
log "running $LABEL: mvn test $*"
set +e
mvn -B clean test -Dmaven.test.failure.ignore=true "$@"
STATUS=$?
set -e

rm -rf "$DEST"
mkdir -p "$DEST"
[ -d target/surefire-reports ] && cp -R target/surefire-reports "$DEST/"
[ -f target/failure-triage.txt ] && cp target/failure-triage.txt "$DEST/"
# Written when a real device refused a session; the pipeline then falls back to the simulator.
[ -f target/device-unavailable.txt ] && cp target/device-unavailable.txt "$DEST/"
[ -d target/artifacts ] && cp -R target/artifacts "$DEST/"
exit $STATUS
