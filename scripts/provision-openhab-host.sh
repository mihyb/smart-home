#!/usr/bin/env bash
# Provision a fresh Ubuntu Server host to run openHAB 5.2.1.
#
#   HOST=192.168.1.x ./scripts/provision-openhab-host.sh            # dry run
#   HOST=192.168.1.x ./scripts/provision-openhab-host.sh --apply
#
# Target: HP EliteDesk 705, Ubuntu Server 24.04 LTS, x86_64.
# Installs openHAB + Java 21 only. mosquitto and zigbee2mqtt deliberately stay
# on 192.168.1.109 for the parallel-running phase — this host points at that
# broker until cutover, so there is no second broker and no fight over the
# Zigbee dongle. See docs/MIGRATION-OPENHAB.md.
#
# Idempotent: safe to re-run.
set -euo pipefail

HOST="${HOST:?set HOST=<ip-or-name> of the new machine}"
USER_NAME="${USER_NAME:-$(whoami)}"
OH_VERSION="${OH_VERSION:-5.2.1}"

APPLY=""
[ "${1:-}" = "--apply" ] && APPLY=1

SSH="ssh -o BatchMode=yes -o ConnectTimeout=10 ${USER_NAME}@${HOST}"

say() { printf '==> %s\n' "$*"; }

run() {
  # Echo every command; only execute it under --apply.
  printf '    %s\n' "$1"
  [ -n "${APPLY}" ] || return 0
  ${SSH} "$1"
}

say "Target: ${USER_NAME}@${HOST}  (openHAB ${OH_VERSION})"
[ -n "${APPLY}" ] || say "DRY RUN — showing what would run. Pass --apply to execute."

say "Preflight"
if ! ${SSH} true 2>/dev/null; then
  echo "    ERROR: cannot SSH to ${USER_NAME}@${HOST}." >&2
  echo "    Install openssh-server on the box and copy your key:  ssh-copy-id ${USER_NAME}@${HOST}" >&2
  exit 1
fi
${SSH} '
  . /etc/os-release
  printf "    os:     %s\n" "$PRETTY_NAME"
  printf "    arch:   %s\n" "$(uname -m)"
  printf "    ram:    %s\n" "$(free -h | awk "/^Mem:/{print \$2}")"
  printf "    disk:   %s free\n" "$(df -h / | awk "NR==2{print \$4}")"
  case "$VERSION_ID" in
    24.04|26.04) ;;
    *) printf "    WARNING: expected Ubuntu 24.04/26.04 LTS, got %s\n" "$VERSION_ID" ;;
  esac
'

say "Java 21 (openHAB 5 requires it)"
run 'sudo apt-get update -qq'
run 'sudo apt-get install -y -qq openjdk-21-jre-headless'

say "openHAB apt repository"
run 'sudo apt-get install -y -qq apt-transport-https curl gnupg'
run 'curl -fsSL https://openhab.jfrog.io/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /usr/share/keyrings/openhab.gpg'
run 'echo "deb [signed-by=/usr/share/keyrings/openhab.gpg] https://openhab.jfrog.io/artifactory/openhab-linuxpkg stable main" | sudo tee /etc/apt/sources.list.d/openhab.list >/dev/null'
run 'sudo apt-get update -qq'

say "openHAB ${OH_VERSION}"
run "sudo apt-get install -y -qq openhab=${OH_VERSION}-1 || sudo apt-get install -y -qq openhab"
run 'sudo apt-mark hold openhab'   # pin: upgrades must be deliberate, the binding is version-matched
run 'sudo systemctl daemon-reload'
run 'sudo systemctl enable openhab'

say "Serial access for the Zigbee dongle (needed at cutover, harmless before)"
run "sudo usermod -aG dialout openhab"
run "sudo usermod -aG dialout ${USER_NAME}"

say "Never sleep — this is an always-on appliance"
run 'sudo systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target'

say "Directories for the custom binding and config"
run 'sudo install -d -o openhab -g openhab /usr/share/openhab/addons'
run 'sudo install -d -o openhab -g openhab /etc/openhab'

if [ -n "${APPLY}" ]; then
  say "Result"
  ${SSH} '
    printf "    java:    %s\n" "$(java -version 2>&1 | head -1)"
    printf "    openhab: %s\n" "$(dpkg-query -W -f="\${Version}" openhab 2>/dev/null || echo NOT INSTALLED)"
    printf "    enabled: %s\n" "$(systemctl is-enabled openhab 2>/dev/null)"
  '
  cat <<EOF

Next:
  1. BIOS: set "After Power Loss" to Power On — otherwise a power cut leaves
     the heating dead until someone notices.
  2. Give this host a static IP or DHCP reservation before going further.
  3. Deploy config:  HOST=${HOST} ./scripts/deploy-openhab.sh --apply
  4. Build and install the binding:
     *** DO NOT DO THIS YET ***  The 5.2.1 jar leaks websockets to the ATMOS
     gateway and exhausts its socket table, leaving it unreachable until it is
     physically power-cycled. Stopping the bundle is not enough — orphaned Jetty
     threads survive bundle:stop and keep dialling; only an openHAB restart
     clears them. Wait for a fixed build.
       cd atmos-connector && ./mvnw -s .mvn/settings.xml clean package
       scp bundles/*/target/org.openhab.binding.atmoswg1000-${OH_VERSION}.jar \\
           ${USER_NAME}@${HOST}:/tmp/ && ssh ${USER_NAME}@${HOST} \\
           'sudo mv /tmp/org.openhab.binding.atmoswg1000-${OH_VERSION}.jar /usr/share/openhab/addons/'
EOF
else
  echo
  say "Nothing was executed."
fi
