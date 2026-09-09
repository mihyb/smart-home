# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

## Repository structure

A real monorepo — both projects are subtrees with their full history merged in,
not submodules. One clone, one `git log`, one branch. A change to an item name
and the code that consumes it belongs in a single commit.

| Directory | Purpose |
|---|---|
| `openhab-ruprechtice/` | OpenHAB config (items, things, rules, sitemaps, services). Deployed to `/etc/openhab` — **this directory's root maps to the server's `/etc/openhab`** |
| `HomeController/` | Spring Boot / Kotlin rules engine that polls and commands OpenHAB over REST |
| `scripts/` | Build, deploy, status and log helpers for both halves |

## System overview

**OpenHAB** is the device hub — it integrates the physical hardware (Z-Wave, MQTT,
Atmos boiler) and exposes items over REST. Config is file-based; there is no build
step.

**HomeController** is a custom rules engine on top. It reads item states and sends
commands back to OpenHAB's REST API. It never talks to hardware directly. The
interesting logic is in `HomeController/src/main/kotlin/.../service/rules/`.

**The integration contract is item names.** Strings defined in
`openhab-ruprechtice/items/*.items` are the same strings referenced in
`HomeController/src/main/resources/application.yaml` under `app.timerJobs` and
`app.minMaxJobs`. Nothing type-checks this across the boundary — grep both sides
when renaming.

## Hosts

The two halves run on **different machines**. HomeController was moved off the
OpenHAB box in Aug 2026 because that box has only 732 MB of RAM.

| | OpenHAB | HomeController |
|---|---|---|
| Address | `192.168.1.109` | `192.168.1.124` |
| SSH user | `dev` | `majkl` |
| Location | `/etc/openhab` | `/home/majkl/app` |
| Process | OpenHAB service | systemd unit `homecontroller` |
| Port | `8080` (REST at `/rest/`) | `8181` |

HomeController finds OpenHAB via `OPENHAB_HOST`/`OPENHAB_PORT`, set in the systemd
unit and defaulted in `application.yaml`. The unit file is tracked at
`HomeController/deploy/homecontroller.service` and matches the live one; first-time
host setup is `HomeController/deploy/provision.sh` (see `deploy/MIGRATION.md`).

## Everyday commands

All from the repo root. Deploys hit the live home system — confirm before running.

```bash
./scripts/status.sh                  # health of both halves, plus server-side drift
./scripts/status.sh controller       # or: openhab

./scripts/deploy-openhab.sh          # DRY RUN — shows what would change
./scripts/deploy-openhab.sh --apply  # actually sync config to /etc/openhab

./scripts/deploy-controller.sh       # build + deploy + verify
./scripts/deploy-controller.sh --no-build

./scripts/logs.sh controller         # app log      (controller-unit for journald)
./scripts/logs.sh openhab            # openhab.log  (openhab-events for state changes)
```

HomeController build and test, from `HomeController/`:

```bash
./build.sh                                  # sdkman java 17, ktlintFormat, clean build bootJar
./gradlew test                              # add --rerun-tasks; results cache aggressively
./gradlew test --tests "...TimerJobTest"
./gradlew bootRun                           # HITS LIVE OPENHAB unless spring.profiles.active=test-mode
```

## Deploying OpenHAB — read before changing

The server used to *pull*: `git fetch && git reset --hard origin/initital_setup`
inside `/etc/openhab`. **That is destructive and is no longer used.** When this
monorepo was built, the live server held config that existed in no repo:

- `items/atmos.items` — 17 Atmos WG1000 boiler items. Now captured here.
- `things/atmos.things` — **still uncaptured.** Root-owned, unreadable as `dev`,
  and it likely holds the Atmos gateway credentials. Capture it deliberately, and
  decide whether it belongs in git before committing it.
- jrule jars newer than the tracked copies, from an OpenHAB upgrade on the server.
  Now pulled down and committed.

`scripts/deploy-openhab.sh` uses rsync without `--delete`: additive, dry-run by
default. Server-side drift shows up in the dry run instead of being destroyed.
`./scripts/status.sh openhab` lists it any time.

`openhab-ruprechtice/deploy_openhab.sh` is a stub that refuses to run.

## Rules in `application.yaml`

- `app.timerJobs` — time-window on/off. Adding a scheduled device is a YAML-only
  change: four OpenHAB item names (`switchItem`, `statusItem`, `startHourItem`,
  `endHourItem`), optional `mode` (ALL/WEEKDAY/WEEKEND) and optional `conditions`
  (item must equal value, else the device is switched off and the window skipped).
- `app.minMaxJobs` — thermostat-style. Switches on below `minValueItem`, off above
  `maxValueItem`. Used for the chick brooder.

## Architecture (`com.hyblerm.homecontroller.*`)

- `service/rules/` — pure domain logic, depends only on the `DataAccess` abstraction
- `service/repository/` — `DataAccess` interface, electricity pricing facade
- `repository/` — Spring implementations (`OpenHabRepository` via WebClient;
  `OpenHabReadOnlyRepository` for `test-mode`)
- `config/` — `ConfigurationProperties` (`app.*`), 20s cache on item GETs, scheduling

Dependencies point inward; `service/rules/` has no Spring or HTTP imports.

**Test shape:** mock `DataAccess` and `Time` (inject `Clock.fixed`), stub items as
`OpenHabModel.Item("link", name, value)`, assert with
`verify(dataAccess).commandItem(...)`. `TimerJobTest` and `MinMaxJobTest` are the
canonical examples. Write the test first.

## Monitored rooms (Czech → English)

Pracovna (office), Chodba (hallway), Pokojíček (kids room), Obyvák (living room),
Koupelna (bathroom), Zadveri (entry hall), Kotelna (boiler room), Loznice
(bedroom), Sklep (basement).
