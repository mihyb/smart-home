#!/usr/bin/env bash
# One-time setup on a fresh HomeController host. Run it ON the target box:
#   scp deploy/provision.sh deploy/homecontroller.service majkl@<host>:/tmp/
#   ssh majkl@<host> 'bash /tmp/provision.sh'
set -euo pipefail

APP_USER="${APP_USER:-majkl}"
APP_DIR="${APP_DIR:-/home/${APP_USER}/app}"
UNIT_SRC="${UNIT_SRC:-/tmp/homecontroller.service}"

echo "==> Checking Java runtime"
if ! command -v java >/dev/null 2>&1; then
    echo "java not found. Install a JRE 11+ first, e.g.:" >&2
    echo "  sudo apt-get update && sudo apt-get install -y openjdk-17-jre-headless" >&2
    exit 1
fi
java -version

echo "==> Creating ${APP_DIR}"
mkdir -p "${APP_DIR}/data"

echo "==> Installing systemd unit"
[ -f "${UNIT_SRC}" ] || { echo "missing ${UNIT_SRC}" >&2; exit 1; }
sudo install -m 0644 "${UNIT_SRC}" /etc/systemd/system/homecontroller.service
sudo systemctl daemon-reload
sudo systemctl enable homecontroller.service

# deploy.sh restarts the service over a non-interactive SSH session, so the
# deploy user needs those three verbs — and only those three — without a
# password prompt.
echo "==> Granting ${APP_USER} passwordless restart of homecontroller only"
SYSTEMCTL="$(command -v systemctl)"
sudo tee /etc/sudoers.d/homecontroller >/dev/null <<SUDOERS
${APP_USER} ALL=(root) NOPASSWD: ${SYSTEMCTL} restart homecontroller, ${SYSTEMCTL} start homecontroller, ${SYSTEMCTL} stop homecontroller
SUDOERS
sudo chmod 0440 /etc/sudoers.d/homecontroller
sudo visudo -cf /etc/sudoers.d/homecontroller

echo "==> Done. From the Mac: ./build.sh && ./deploy.sh"
echo "    Logs: journalctl -u homecontroller -f"
