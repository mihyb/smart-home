#!/usr/bin/env bash
# Health of both halves of the system.
#
#   ./scripts/status.sh              # both
#   ./scripts/status.sh controller   # just HomeController
#   ./scripts/status.sh openhab      # just OpenHAB
set -uo pipefail

CTL_HOST="${CTL_HOST:-192.168.1.124}"
CTL_USER="${CTL_USER:-majkl}"
CTL_PORT="${CTL_PORT:-8181}"
OH_HOST="${OPENHAB_HOST:-192.168.1.109}"
OH_USER="${OPENHAB_USER:-dev}"
OH_PORT="${OPENHAB_PORT:-8080}"

SSH="ssh -o BatchMode=yes -o ConnectTimeout=8"
WHAT="${1:-all}"

controller() {
  echo "=== HomeController @ ${CTL_HOST} ==="
  echo "systemd:   $(${SSH} "${CTL_USER}@${CTL_HOST}" 'systemctl is-active homecontroller' 2>/dev/null || echo UNREACHABLE)"
  echo "uptime:    $(${SSH} "${CTL_USER}@${CTL_HOST}" 'systemctl show homecontroller -p ActiveEnterTimestamp --value' 2>/dev/null || echo '?')"
  echo "jar built: $(${SSH} "${CTL_USER}@${CTL_HOST}" 'date -r /home/majkl/app/HomeController-0.0.1-SNAPSHOT.jar' 2>/dev/null || echo '?')"
  # grep -c exits 1 on zero matches, so swallow that rather than reporting '?'
  echo "errors in log: $(${SSH} "${CTL_USER}@${CTL_HOST}" 'grep -c ERROR /home/majkl/app/home-portal.log 2>/dev/null || true' 2>/dev/null || echo '?')"
}

openhab() {
  echo "=== OpenHAB @ ${OH_HOST} ==="
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "http://${OH_HOST}:${OH_PORT}/rest/" 2>/dev/null)
  echo "REST:      ${code:-unreachable} (http://${OH_HOST}:${OH_PORT}/rest/)"
  echo "items:     $(curl -s --max-time 8 "http://${OH_HOST}:${OH_PORT}/rest/items" 2>/dev/null | grep -o '"name"' | wc -l | tr -d ' ')"
  echo "uncommitted config on server:"
  ${SSH} "${OH_USER}@${OH_HOST}" 'cd /etc/openhab && git status --porcelain' 2>/dev/null | sed 's/^/  /' || echo "  UNREACHABLE"
}

case "${WHAT}" in
  controller) controller ;;
  openhab)    openhab ;;
  *)          controller; echo; openhab ;;
esac
