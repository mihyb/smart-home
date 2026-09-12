#!/usr/bin/env bash
# Every item name HomeController references must exist in the openHAB config.
#
# This is the one failure the system cannot detect at runtime: a typo or a
# renamed item makes HomeController poll an item that is not there, and it
# simply never acts. No error, no log line that stands out — a timer that
# silently does nothing. Cheap to check, so check it on every change.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
YAML="${ROOT}/HomeController/src/main/resources/application.yaml"
ITEMS="${ROOT}/openhab-ruprechtice/items"

referenced=$(grep -oE '(switchItem|statusItem|startHourItem|endHourItem|valueItem|minValueItem|maxValueItem):[[:space:]]*"[^"]+"' "${YAML}" \
  | sed 's/.*"\(.*\)"/\1/' | sort -u)

defined=$(grep -rhoE '^[[:space:]]*(Switch|Number[^[:space:]]*|String|Contact|Dimmer|Rollershutter|Color|DateTime|Location|Group[^[:space:]]*)[[:space:]]+[A-Za-z0-9_]+' "${ITEMS}"/*.items \
  | awk '{print $NF}' | sort -u)

missing=$(comm -23 <(echo "${referenced}") <(echo "${defined}") || true)

echo "  HomeController references: $(echo "${referenced}" | grep -c . ) items"
echo "  openHAB defines:           $(echo "${defined}" | grep -c . ) items"
if [ -n "${missing}" ]; then
  echo
  echo "  MISSING — HomeController would poll these and silently do nothing:"
  echo "${missing}" | sed 's/^/    /'
  exit 1
fi
echo "  OK — every referenced item exists"
