#!/usr/bin/env bash
# Build and deploy HomeController to its host, then verify the service came back.
#
#   ./scripts/deploy-controller.sh              # build + deploy + verify
#   ./scripts/deploy-controller.sh --no-build   # deploy the existing jar
#
# Wraps HomeController/build.sh and HomeController/deploy.sh so the whole
# thing is one command from the monorepo root.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}/HomeController"

if [ "${1:-}" != "--no-build" ]; then
  echo "==> Building (java 17 via sdkman, ktlintFormat + clean build bootJar)"
  ./build.sh
else
  echo "==> Skipping build"
fi

echo "==> Deploying"
./deploy.sh

echo "==> Verifying"
"${ROOT}/scripts/status.sh" controller
