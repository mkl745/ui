#!/usr/bin/env bash
set -o pipefail
./gradlew :app:connectedDebugAndroidTest 2>&1 | tee "$RUNNER_TEMP/ui-test-output.txt"
