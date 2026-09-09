#!/usr/bin/env bash
# Tail logs from either host.
#
#   ./scripts/logs.sh controller        # HomeController app log, follow
#   ./scripts/logs.sh controller-unit   # systemd journal for the unit
#   ./scripts/logs.sh openhab           # openhab.log, follow
#   ./scripts/logs.sh openhab-events    # openhab events.log (item state changes)
set -euo pipefail

CTL_HOST="${CTL_HOST:-192.168.1.124}"
CTL_USER="${CTL_USER:-majkl}"
OH_HOST="${OPENHAB_HOST:-192.168.1.109}"
OH_USER="${OPENHAB_USER:-dev}"

SSH="ssh -o ConnectTimeout=8"

case "${1:-controller}" in
  controller)      ${SSH} -t "${CTL_USER}@${CTL_HOST}" 'tail -f /home/majkl/app/home-portal.log' ;;
  controller-unit) ${SSH} -t "${CTL_USER}@${CTL_HOST}" 'journalctl -u homecontroller -f' ;;
  openhab)         ${SSH} -t "${OH_USER}@${OH_HOST}"   'tail -f /var/log/openhab/openhab.log' ;;
  openhab-events)  ${SSH} -t "${OH_USER}@${OH_HOST}"   'tail -f /var/log/openhab/events.log' ;;
  *) echo "usage: $0 {controller|controller-unit|openhab|openhab-events}" >&2; exit 1 ;;
esac
