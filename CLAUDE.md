# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

## Repository structure

A real monorepo — both projects are subtrees with their full history merged in,
not submodules. One clone, one `git log`, one branch. A change to an item name
and the code that consumes it belongs in a single commit.

| Directory | Purpose |
|---|---|
| `openhab-ruprechtice/` | openHAB config. **This directory's root maps to the server's `/etc/openhab`** |
| `HomeController/` | Spring Boot / Kotlin rules engine that polls and commands openHAB over REST |
| `atmos-connector/` | **Submodule** — the custom `atmoswg1000` binding for the Atmos boiler. Standalone reusable component, own repo and release cycle |
| `scripts/` | Build, deploy, cutover, status and log helpers |
| `config/` | `item-states.tsv` — captured setpoints, see *Item state* below |

`atmos-connector` is a submodule while the other two are subtrees, and that is
deliberate. openHAB config and HomeController are co-developed — an item rename
touches both, so they need to land in one commit. The binding is a standalone
library consumed as a built JAR. The cost is the usual submodule one: `git pull`
leaves it at the old pin unless you pass `--recurse-submodules`.

```bash
git clone --recurse-submodules git@github-personal:mihyb/smart-home.git
git submodule update --init --remote   # in an existing clone
```

## Hosts

**Everything now runs on 192.168.1.132.** The two old machines are being retired.

| | `.132` ruprecht-home-system | `.109` home-portal | `.124` |
|---|---|---|---|
| Role | openHAB 5.2.1 **and** HomeController | mosquitto + zigbee2mqtt only | nothing |
| OS | Ubuntu 24.04, 7.2 GB | Ubuntu 18.04 (EOL), 732 MB | Ubuntu 24.04 |
| SSH | `ruprecht@192.168.1.132` | `dev@192.168.1.109` | `majkl@192.168.1.124` |
| openHAB | active | **stopped and disabled, not removed** | — |
| HomeController | active | — | **stopped and disabled**, old jar kept as `.pre-cutover` |

`.109` still owns the SONOFF Zigbee dongle (`/dev/serial/by-id/usb-ITEAD_SONOFF_…`)
via zigbee2mqtt, and still runs the MQTT broker. Those are the last things to
move. Because HomeController and openHAB are now on the same box, the controller
talks to openHAB over loopback (`OPENHAB_HOST=127.0.0.1`).

Rollback is symmetrical and everything needed for it is still in place:

```bash
ssh ruprecht@192.168.1.132 'sudo systemctl disable --now homecontroller'
ssh dev@192.168.1.109      'sudo systemctl enable --now openhab'
ssh majkl@192.168.1.124    'sudo systemctl enable --now homecontroller'
```

## Everyday commands

All from the repo root. Deploys hit the live home system.

```bash
./scripts/status.sh                  # health of both halves, plus server-side drift
./scripts/deploy-openhab.sh          # DRY RUN; --apply to write
./scripts/deploy-controller.sh       # build + deploy + verify
./scripts/cutover-controller.sh      # the .124 -> .132 move; dry-run by default
./scripts/sync-item-states.sh diff   # compare setpoints between two instances
./scripts/logs.sh controller         # or: controller-unit | openhab | openhab-events
```

HomeController build and test, from `HomeController/`:

```bash
./build.sh                                  # sdkman java 17, ktlintFormat, clean build bootJar
./gradlew test --rerun-tasks                # results cache aggressively
```

The Atmos binding, from `atmos-connector/`:

```bash
./mvnw -s .mvn/settings.xml clean package   # stops at package; verify needs an openhab-addons checkout
```

## Things that are not obvious and have bitten

**Item state is config, and lives nowhere else.** The timer windows
(`timer_job_*_from/_to/_status`), brooder setpoints and Atmos modes exist only as
openHAB item state. They are not in any file. `scripts/sync-item-states.sh`
exports and applies them, and `config/item-states.tsv` is a snapshot — re-export
before relying on it.

**Persistence is what keeps them alive.** `openhab-ruprechtice/persistence/rrd4j.persist`
declares `restoreOnStartup`. Without it a restart wipes every schedule and the
controller comes up with nothing to do. `.109` registered rrd4j implicitly and
`.132` did not, so this is declared rather than assumed. Note `default = everyChange`
inside `Strategies` is rejected by this openHAB and silently disables the whole file.

**Things files: nesting changes a Thing's UID.** openHAB derives a nested Thing's
UID from its enclosing bridge. `mqtt:topic:bojler_switch` has no broker segment,
so nesting it renames it to `mqtt:topic:fb0a76c816:bojler_switch` and every item
linked to the old UID silently resolves to nothing — the Thing still reports
ONLINE and the switch still renders. It is declared standalone with a `(bridge)`
reference. `scripts/jsondb-to-things.py` now refuses to nest in that case.

**A `Switch` cannot be a linkable sitemap widget.** Giving one nested children
does not fail loudly: openHAB discards the *entire* sitemap and Basic UI reports
"you have not defined any sitemaps yet". Expandable rows must be `Text`.

**Items that receive REFRESH need `autoupdate="false"`.** Optimistic autoupdate
writes the literal command into the item, so a String item displays "REFRESH".

**This DSL has no `newThingStatusInfo` and cannot name `RefreshType`.** Both
produce rules that load fine and then throw on every trigger. Use
`getThingStatusInfo(uid)` and the two-argument `sendCommand(item, "REFRESH")`.

**Items read `NULL` when their thing is offline.** `getDouble()` throws on that;
`getDoubleOrNull()` exists for rules that read sensors. MinMaxJob and TimerJob
skip the cycle rather than die.

## Rules in `application.yaml`

- `app.timerJobs` — time-window on/off. Four item names, optional `mode`
  (ALL/WEEKDAY/WEEKEND) and optional `conditions` (item must equal value, else the
  device is switched off and the window skipped).
- `app.minMaxJobs` — thermostat-style. On below `minValueItem`, off above
  `maxValueItem`. Used for the chick brooder.

**The integration contract is item names.** Strings in
`openhab-ruprechtice/items/*.items` are the same strings in `application.yaml`.
Nothing type-checks this — grep both sides when renaming.

## The Atmos boiler binding

`atmos-connector/` builds the jar that is sideloaded into
`/usr/share/openhab/addons/`. It is not a marketplace addon.

**It is pinned to the openHAB version by its pom parent**, so upgrading openHAB
means rebuilding the binding first. The 16 classes in `protocol/` have no openHAB
imports and port cleanly; the handlers and discovery services are where the addon
API moves between majors.

**It once exhausted the gateway's socket table.** The bridge retried on a
fixed-delay job *and* scheduled another attempt from its failure handler, so
pollers doubled every cycle, each with a fresh Jetty client. The WG1000 has very
few sockets and stopped accepting connections on every port while still answering
ICMP and serving its cloud link — which reads like a dead device. Fixed by
`Backoff`. If it ever recurs: `ss -tan 'dst 192.168.1.122'` should show exactly
one ESTAB. `bundle:stop` is *not* enough to stop it — orphaned Jetty threads
survive and keep dialling; only an openHAB restart clears them.

Modes: only `AWAY` and `VISIT` expire, and the controller stores an **end time of
day**, not a duration, so nothing beyond 23:30 is expressible. `HOLIDAY` ends on a
date and is refused. The `mode-duration` channel is a plain `Number` in minutes,
not `Number:Time`, because a sitemap `Selection` sends a bare number that openHAB
would read as seconds.

## Secrets

Three files are gitignored and must be recreated from a password manager. Each has
a `.example` alongside it with `<<SET_ME>>` placeholders:

| File | Holds |
|---|---|
| `things/tuya.things` | Tuya cloud accessId/accessSecret/username/password, plus a localKey per device |
| `things/atmoswg1000.things` | Atmos gateway login — account `WG1000`, password was in `atmos-connector/tools/.wg1000-password` |
| `.claude/settings.local.json` | per-machine permissions |

## Git accounts

Two identities on this machine. This is a **personal** repo.

| | Account | Remote | Commit email |
|---|---|---|---|
| Personal | `mihyb` | `git@github-personal:` | `m.hybler@gmail.com` |
| Work | `mhybler` | `git@github.com:` | `c_mhybler@groupon.com` |

`~/.gitconfig` switches identity on the **remote**, not the directory, because
`~/Work/private/projects/` holds work repos too. The glob matters: `**` is special
only as a whole path component, so `git@github-personal:**` does not match — the
working form is `git@github-personal:*/**`.

## Reaching history from before the migration

Both histories are here (earliest Nov 2021), merged as subtrees, so pre-migration
commits used paths without the directory prefix and a plain path-scoped log looks
empty:

```bash
git log --full-history -- 'openhab-ruprechtice/items/timerJob.items' 'items/timerJob.items'
```

## Monitored rooms (Czech → English)

Pracovna (office), Chodba (hallway), Pokojíček (kids room), Obyvák (living room),
Koupelna (bathroom), Zadveri (entry hall), Kotelna (boiler room), Loznice
(bedroom), Sklep (basement).
