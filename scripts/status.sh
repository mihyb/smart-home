#!/usr/bin/env bash
# Health of both halves of the system.
#
#   ./scripts/status.sh              # both
#   ./scripts/status.sh controller   # just HomeController
#   ./scripts/status.sh openhab      # just OpenHAB
set -uo pipefail

CTL_HOST="${CTL_HOST:-192.168.1.132}"
CTL_USER="${CTL_USER:-ruprecht}"
CTL_PORT="${CTL_PORT:-8181}"
OH_HOST="${OPENHAB_HOST:-192.168.1.132}"
OH_USER="${OPENHAB_USER:-ruprecht}"
OH_PORT="${OPENHAB_PORT:-8080}"
# Stored every five minutes, so an hour of it is a round number to compare against.
HISTORY_PROBE_ITEM="${HISTORY_PROBE_ITEM:-Atmos_Outdoor}"

SSH="ssh -o BatchMode=yes -o ConnectTimeout=8"
WHAT="${1:-all}"

controller() {
  echo "=== HomeController @ ${CTL_HOST} ==="
  echo "systemd:   $(${SSH} "${CTL_USER}@${CTL_HOST}" 'systemctl is-active homecontroller' 2>/dev/null || echo UNREACHABLE)"
  echo "uptime:    $(${SSH} "${CTL_USER}@${CTL_HOST}" 'systemctl show homecontroller -p ActiveEnterTimestamp --value' 2>/dev/null || echo '?')"
  echo "jar built: $(${SSH} "${CTL_USER}@${CTL_HOST}" 'date -r /home/ruprecht/app/HomeController-0.0.1-SNAPSHOT.jar' 2>/dev/null || echo '?')"
  # grep -c exits 1 on zero matches, so swallow that rather than reporting '?'
  echo "errors in log: $(${SSH} "${CTL_USER}@${CTL_HOST}" 'grep -c ERROR /home/ruprecht/app/home-portal.log 2>/dev/null || true' 2>/dev/null || echo '?')"
}

openhab() {
  echo "=== OpenHAB @ ${OH_HOST} ==="
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "http://${OH_HOST}:${OH_PORT}/rest/" 2>/dev/null)
  echo "REST:      ${code:-unreachable} (http://${OH_HOST}:${OH_PORT}/rest/)"
  echo "items:     $(curl -s --max-time 8 "http://${OH_HOST}:${OH_PORT}/rest/items" 2>/dev/null | grep -o '"name"' | wc -l | tr -d ' ')"
  # Is the jdbc history actually being written? A persistence service that failed
  # to come up is silent — openHAB simply stores nothing, and the gap only shows
  # up weeks later when the thermal fit has no data. The service list itself is an
  # admin endpoint (401 unauthenticated), but one item's own history is not, and
  # it answers the real question. GNU and BSD date disagree about arithmetic.
  local hour_ago
  hour_ago=$(date -u -v-1H +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%SZ)
  echo "history 1h:    ${HISTORY_PROBE_ITEM}: $(curl -s --max-time 8 "http://${OH_HOST}:${OH_PORT}/rest/persistence/items/${HISTORY_PROBE_ITEM}?serviceId=jdbc&starttime=${hour_ago}" 2>/dev/null | jq -r 'if .datapoints then .datapoints + " points" else "jdbc is not serving" end' 2>/dev/null || echo '?')"
  # A forecast that stopped arriving looks exactly like a current one, because the
  # last numbers just stand in the items. This is the cheapest way to see it.
  echo "forecast from:  $(curl -s --max-time 8 "http://${OH_HOST}:${OH_PORT}/rest/items/weather_updated" 2>/dev/null | jq -r '.state // "MISSING"' 2>/dev/null || echo '?')"
  # The new host has no git checkout of /etc/openhab — config is rsynced from
  # this repo, which is the point. Drift is whatever deploy-openhab.sh reports.
  echo "config drift:  ./scripts/deploy-openhab.sh (dry run) shows it"
}

case "${WHAT}" in
  controller) controller ;;
  openhab)    openhab ;;
  *)          controller; echo; openhab ;;
esac
