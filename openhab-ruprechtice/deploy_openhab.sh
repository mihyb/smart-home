#!/usr/bin/env bash
# SUPERSEDED — do not use. Run ../scripts/deploy-openhab.sh instead.
#
# This used to be:
#   ssh dev@192.168.1.109 'cd /etc/openhab && git fetch origin && git reset --hard origin/initital_setup'
#
# That is destructive against the live server. As of the monorepo migration
# the server had untracked config (items/atmos.items — the Atmos boiler
# items, and things/atmos.things) plus jrule jars newer than the tracked
# copies. `git reset --hard` deletes the first and downgrades the second,
# silently, on a running heating system.
#
# scripts/deploy-openhab.sh does the same job with rsync: additive, dry-run
# by default, and it surfaces server-side drift instead of destroying it.
echo "deploy_openhab.sh is superseded. Use:  ./scripts/deploy-openhab.sh [--apply]" >&2
exit 1
