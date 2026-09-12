#!/usr/bin/env bash
# Move HomeController from 192.168.1.124 to 192.168.1.132, alongside openHAB.
#
#   ./scripts/cutover-controller.sh            # dry run
#   ./scripts/cutover-controller.sh --apply
#
# Order matters. HomeController is the only thing that commands devices, so two
# copies must never run at once, and it logs errors continuously if its openHAB
# disappears underneath it. So: stop the controller first, then the old openHAB,
# then bring the controller up against the new one.
#
# Nothing is uninstalled. The old openHAB stays on .109 and the old jar is kept
# on .124, so rolling back is just starting them again — see the end of this file.
#
# mosquitto and zigbee2mqtt on .109 are untouched throughout: they are separate
# units with no dependency on openhab, and the devices stay reachable.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

OLD_CTL="${OLD_CTL:-majkl@192.168.1.124}"      # where HomeController runs today
OLD_OH="${OLD_OH:-dev@192.168.1.109}"          # the openHAB being retired
NEW="${NEW:-ruprecht@192.168.1.132}"           # openHAB + HomeController from now on
JAR="HomeController-0.0.1-SNAPSHOT.jar"
BUILT="${ROOT}/HomeController/build/libs/${JAR}"

APPLY=""
[ "${1:-}" = "--apply" ] && APPLY=1
SSH="ssh -o BatchMode=yes -o ConnectTimeout=10"

say() { printf '\n==> %s\n' "$*"; }
run() { printf '    %s\n' "$2"; [ -n "${APPLY}" ] && $SSH "$1" "$2" || true; }

[ -f "${BUILT}" ] || { echo "no ${BUILT} — run HomeController/build.sh first" >&2; exit 1; }
[ -n "${APPLY}" ] || echo "DRY RUN — nothing will be changed. Pass --apply to execute."

say "Preflight"
for h in "${OLD_CTL}" "${OLD_OH}" "${NEW}"; do
  printf '    %-28s ' "${h}"
  $SSH "${h}" true 2>/dev/null && echo "reachable" || { echo "UNREACHABLE"; exit 1; }
done
printf '    new openHAB REST              '
curl -sf -o /dev/null --max-time 10 "http://${NEW#*@}:8080/rest/" && echo "responding" || {
  echo "NOT RESPONDING — refusing to cut over to an openHAB that is not up"; exit 1; }

say "1. Stop HomeController on the old host (nothing commands devices from here on)"
run "${OLD_CTL}" 'sudo systemctl stop homecontroller'
run "${OLD_CTL}" 'sudo systemctl disable homecontroller'   # must not come back on reboot

say "2. Keep the old jar as the rollback artefact"
run "${OLD_CTL}" "cp -n /home/majkl/app/${JAR} /home/majkl/app/${JAR}.pre-cutover || true"

say "3. Stop openHAB on the old host — stopped, not removed"
run "${OLD_OH}" 'sudo systemctl stop openhab'
run "${OLD_OH}" 'sudo systemctl disable openhab'
say "   mosquitto and zigbee2mqtt stay up:"
[ -n "${APPLY}" ] && $SSH "${OLD_OH}" 'for s in mosquitto zigbee2mqtt; do printf "    %-14s %s\n" "$s" "$(systemctl is-active $s)"; done' || true

say "4. Install HomeController on the new host"
run "${NEW}" 'install -d -m 755 /home/ruprecht/app/data'
if [ -n "${APPLY}" ]; then
  scp -q -o BatchMode=yes "${BUILT}" "${NEW}:/tmp/${JAR}"
  scp -q -o BatchMode=yes "${ROOT}/HomeController/deploy/homecontroller.service" "${NEW}:/tmp/homecontroller.service"
fi
run "${NEW}" "mv /tmp/${JAR} /home/ruprecht/app/${JAR}"
run "${NEW}" 'sudo mv /tmp/homecontroller.service /etc/systemd/system/homecontroller.service'
run "${NEW}" 'sudo systemctl daemon-reload'
run "${NEW}" 'sudo systemctl enable homecontroller'

say "5. Start it"
run "${NEW}" 'sudo systemctl start homecontroller'

if [ -n "${APPLY}" ]; then
  sleep 20
  say "Result"
  $SSH "${NEW}" 'printf "    service: %s\n" "$(systemctl is-active homecontroller)"'
  $SSH "${OLD_CTL}" 'printf "    old controller: %s (disabled)\n" "$(systemctl is-active homecontroller)"'
  $SSH "${OLD_OH}" 'printf "    old openHAB:    %s (disabled)\n" "$(systemctl is-active openhab)"'
  cat <<EOF

Verify:
  ./scripts/status.sh
  ssh ${NEW} 'journalctl -u homecontroller -f'

Roll back:
  ssh ${NEW} 'sudo systemctl disable --now homecontroller'
  ssh ${OLD_OH} 'sudo systemctl enable --now openhab'
  ssh ${OLD_CTL} 'sudo systemctl enable --now homecontroller'
EOF
else
  echo
  say "Nothing was executed."
fi
