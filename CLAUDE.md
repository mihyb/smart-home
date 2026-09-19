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
| `fencee-connector/` | **Submodule** — the custom `fenceecloud` binding for the electric fence (GW100 gateway, two PDX70 energizers). Same shape as `atmos-connector`: own repo, own release cycle, consumed as a built JAR |
| `scripts/` | Build, deploy, cutover, status and log helpers |
| `config/` | `item-states.tsv` — captured setpoints, see *Item state* below |

The two bindings are submodules while the other two directories are subtrees,
and that is deliberate. openHAB config and HomeController are co-developed — an item rename
touches both, so they need to land in one commit. Each binding is a standalone
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

**The boiler's zigbee availability is the HDO signal, not a health signal.**
Its socket hangs off the low-tariff contactor, so it leaves the zigbee network
every time HDO drops. `wheater_status` and the old `bojler_online` read the same
`zigbee2mqtt/w_heater_switch/availability` topic, so "offline" there never means
a broken device. It was in `system_health` briefly and reported a degraded
system once a day. Anything else on that circuit has the same property.

**A sitemap condition compares in the item's display unit.** The battery items
that arrive as `Number:Dimensionless` hold a ratio (`0.19`) but render `19 %`,
and `valuecolor=[<20=...]` matches the rendered 19, not the stored 0.19 --
openHAB parses the bare number using the unit from the state description, which
the `%.0f %%` format sets. So percent thresholds are written as percent on both
scales. Rules see the raw state and do need the two cases; `battery_status.rules`
normalises by item type.

**`deploy-openhab.sh` used to report success while writing nothing.**
`/etc/openhab` is `openhab:openhab` 755 and `ruprecht` is not in that group, so
rsync was denied on every file, exited 0 on the local side, and the smoke test
happily measured the config already on the server. It runs the receiver under
`sudo` now. If a config change ever seems not to take, check the file on the
server before believing the deploy.

**The electric fence has no local path at all.** The GW100 gateway answers
ping and nothing else — 1039 TCP ports closed, no UDP, no mDNS — and holds one
outbound connection to fencee Cloud. Every reading and every command crosses
that, so the binding signs in with the phone app's account and the fence is
blind whenever the internet or the cloud is down. A stale fence voltage
therefore does not mean a live fence, which is why the sitemap shows the time
of the last message next to each energizer.

**Items read `NULL` when their thing is offline.** `getDouble()` throws on that;
`getDoubleOrNull()` exists for rules that read sensors. MinMaxJob and TimerJob
skip the cycle rather than die.

## Rules in `application.yaml`

- `app.timerJobs` — time-window on/off. Four item names, optional `mode`
  (ALL/WEEKDAY/WEEKEND) and optional `conditions` (item must equal value, else the
  device is switched off and the window skipped).
- `app.minMaxJobs` — thermostat-style. On below `minValueItem`, off above
  `maxValueItem`. Used for the chick brooder.
- `app.boilerModeJobs` — follows the solid-fuel boiler. `runningItem` (the
  exhaust fan) ON means burning. Which mode each of the four cases means is not
  in this file: it is read from `boiler_auto_running_heating`,
  `boiler_auto_running_water`, `boiler_auto_idle_heating` and
  `boiler_auto_idle_water`, so it is chosen from the sitemap. Like every other
  setpoint here they are **item state only** — `scripts/sync-item-states.sh`
  captures them.
  It re-asserts the target every cycle, but **a mode changed by hand switches
  the automation off** rather than being taken back: the job records where it
  left each circuit in `boiler_auto_last_heating` / `boiler_auto_last_water`,
  and a circuit that has moved since means a person did it — possibly at the
  controller's own panel, where they cannot know an automation exists. Switch
  `boiler_auto_control` back on to resume. It never
  commands a mode the circuit already holds, which is what keeps the gateway's
  socket table intact. AWAY and VISIT are refused as targets — they end at a
  time of day and fall back to AUTO, so the job would re-send them every five
  minutes for the rest of the day.

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
| `things/fenceecloud.things` | fencee Cloud account e-mail and password, plus the gateway's cloud pairing id |
| `.claude/settings.local.json` | per-machine permissions |

**This repository is public.** `mihyb/smart-home` is public on GitHub while both
connector submodules are private, so anything committed here is published:
account ids, cloud pairing ids and device ids belong in the gitignored file and
in the private submodule, not in the `.example` next to it. LAN addresses and
zigbee topics are already here and are harmless.

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
