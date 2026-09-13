#!/usr/bin/env bash
# Ship the boot jar to the HomeController host and restart the service.
# Override the target without editing this file:  HOST=192.168.1.130 ./deploy.sh
set -euo pipefail

HOST="${HOST:-192.168.1.132}"
APP_USER="${APP_USER:-ruprecht}"
APP_DIR="${APP_DIR:-/home/${APP_USER}/app}"
JAR="build/libs/HomeController-0.0.1-SNAPSHOT.jar"

[ -f "${JAR}" ] || { echo "${JAR} not found — run ./build.sh first" >&2; exit 1; }

echo "==> Copying $(basename "${JAR}") to ${APP_USER}@${HOST}:${APP_DIR}/"
scp "${JAR}" "${APP_USER}@${HOST}:${APP_DIR}/"

echo "==> Restarting homecontroller.service"
ssh "${APP_USER}@${HOST}" 'sudo systemctl restart homecontroller && sleep 5 && systemctl is-active homecontroller'

echo "App deployed and running on ${HOST}. Logs: ssh ${APP_USER}@${HOST} 'journalctl -u homecontroller -f'"
