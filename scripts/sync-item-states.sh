#!/usr/bin/env bash
# Copy item states between openHAB instances.
#
#   ./scripts/sync-item-states.sh export                     # .109 -> config/item-states.tsv
#   ./scripts/sync-item-states.sh apply                      # file  -> .132  (dry run)
#   ./scripts/sync-item-states.sh apply --write              # file  -> .132
#   ./scripts/sync-item-states.sh diff                       # compare the two hosts
#
# Why this exists: setpoints like timer_job_*_from/_to, the chick brooder
# min/max and the *_status switches are *item state*, not Thing or item
# configuration. They are set from the sitemap and kept alive only by rrd4j's
# restoreOnStartup. Nothing in git captures them, so a rebuilt machine comes up
# with every schedule NULL and HomeController with nothing to act on.
#
# Only virtual items are synced — anything bound to a channel gets its state
# from the device and must not be forced.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="${SRC:-192.168.1.132}"
DST="${DST:-192.168.1.132}"
FILE="${FILE:-${ROOT}/config/item-states.tsv}"

# Items whose state is user-configuration rather than device-reported.
PATTERN="${PATTERN:-^timer_job_|^chick_0|^boiler_auto_(control|running|idle)|_min_temp$|_max_temp$}"

fetch() {  # host -> name<TAB>state, only non-NULL, only matching PATTERN
  curl -s --max-time 30 "http://$1:8080/rest/items" \
    | jq -r --arg p "$PATTERN" \
        '.[] | select(.name|test($p)) | select(.state!="NULL" and .state!="UNDEF")
         | "\(.name)\t\(.state)"' \
    | sort
}

case "${1:-}" in
  export)
    mkdir -p "$(dirname "${FILE}")"
    {
      echo "# openHAB item states captured from ${SRC} on $(date -u +%Y-%m-%dT%H:%M:%SZ)"
      echo "# These are setpoints set from the sitemap, not device readings."
      echo "# Restore with: ./scripts/sync-item-states.sh apply --write"
      fetch "${SRC}"
    } > "${FILE}"
    echo "==> $(grep -vc '^#' "${FILE}") states -> ${FILE}"
    ;;

  apply)
    [ -f "${FILE}" ] || { echo "no ${FILE} — run 'export' first" >&2; exit 1; }
    write=""; [ "${2:-}" = "--write" ] && write=1
    [ -n "${write}" ] || echo "==> DRY RUN (pass --write to apply)"
    ok=0; skip=0
    while IFS=$'\t' read -r name state; do
      case "${name}" in \#*|"") continue ;; esac
      current=$(curl -s --max-time 15 "http://${DST}:8080/rest/items/${name}" | jq -r '.state // "MISSING"')
      if [ "${current}" = "MISSING" ]; then
        echo "    SKIP  ${name} — not defined on ${DST}"; skip=$((skip+1)); continue
      fi
      if [ "${current}" = "${state}" ]; then
        ok=$((ok+1)); continue
      fi
      printf "    SET   %-34s %s -> %s\n" "${name}" "${current}" "${state}"
      if [ -n "${write}" ]; then
        curl -s -X PUT --max-time 15 -H "Content-Type: text/plain" \
          -d "${state}" "http://${DST}:8080/rest/items/${name}/state" >/dev/null
        ok=$((ok+1))
      fi
    done < "${FILE}"
    echo "==> ${ok} in sync, ${skip} missing on ${DST}"
    ;;

  diff)
    # Compares the captured snapshot against the live host. It used to compare
    # two live instances, which stopped meaning anything once the old openHAB
    # was retired — every item read as missing and the check cried wolf.
    [ -f "${FILE}" ] || { echo "no ${FILE} — run 'export' first" >&2; exit 1; }
    printf "  %-34s %-12s %s\n" "ITEM" "snapshot" "${DST}"
    join -t$'\t' -a1 -a2 -e "(unset)" -o 0,1.2,2.2 \
      <(grep -v '^#' "${FILE}" | sort) <(fetch "${DST}") \
      | awk -F'\t' '{ printf "  %-34s %-12s %s%s\n", $1, $2, $3, ($2==$3?"":"   <-- differs") }'
    ;;

  *)
    echo "usage: $0 {export|apply [--write]|diff}" >&2; exit 1 ;;
esac
