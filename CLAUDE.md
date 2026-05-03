# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository structure

This is a virtual mono-repo. Each subdirectory is a git submodule for a separate concern of a personal smart-home automation system running on a home server at `192.168.1.109`.

| Submodule | Purpose |
|---|---|
| `openhab-ruprechtice/` | OpenHAB 2.x configuration files (items, rules, sitemaps, things, services) deployed directly to the home server |
| `HomeController/` | Spring Boot / Kotlin rules engine that polls and commands OpenHAB over its REST API |

## System overview

**OpenHAB** (`openhab-ruprechtice`) is the device hub — it integrates physical hardware (Z-Wave, MQTT, etc.) and exposes items via REST. Its config lives in `/etc/openhab` on the server and is managed as a git repo.

**HomeController** is a custom rules engine layered on top of OpenHAB. It reads item states and sends commands back to OpenHAB's REST API (`http://192.168.1.109:8080/rest/`). It does not directly talk to hardware. The interesting logic is all in `src/main/kotlin/.../service/rules/`.

The two repos share item names as the integration contract — item names defined in `openhab-ruprechtice/items/*.items` are the same strings referenced in `HomeController/src/main/resources/application.yaml` (`app.timerJobs`, `app.minMaxJobs`).

## Home server

- Address: `192.168.1.109`
- SSH user: `dev`
- OpenHAB REST API: `http://192.168.1.109:8080/rest/`
- HomeController runs on port `8181`

## Monitored rooms (Czech → English)

Pracovna (office), Chodba (hallway), Pokojíček (kids room), Obyvák (living room), Koupelna (bathroom), Zadveri (entry hall), Kotelna (boiler room), Loznice (bedroom), Sklep (basement).

## openhab-ruprechtice

OpenHAB 2.x file-based configuration. No build step — files are deployed as-is.

**Deploy:** `./deploy_openhab.sh` — SSHes to `dev@192.168.1.109`, runs `git fetch origin && git reset --hard origin/initital_setup` inside `/etc/openhab`. The server pulls config from git rather than receiving files; the remote must point to this repo's origin. Live deployment — changes take effect immediately on the running OpenHAB instance.

**Key directories:**
- `items/` — item definitions. `timerJob.items` defines the `Number`/`Switch` items that HomeController reads and writes for each scheduled job. New automations require item definitions here first.
- `sitemaps/ruprechtice.sitemap` — Basic UI layout; the canonical list of all controllable devices and sensors by room.
- `things/` — device bindings and channel definitions.
- `rules/` — native OpenHAB DSL rules (currently only sensor calibration).
- `transform/` — `.map` files for state translations (Czech/English).
- `automation/jrule/` — JRule Java-based automation (jars in `jar/`, generated sources in `gen/`).

## HomeController

See `HomeController/CLAUDE.md` for full build, test, and architecture details. Summary:

**Build & run:**
- `./build.sh` — full build: sources sdkman, selects `java 17.0.7-tem`, runs `ktlintFormat` then `clean build bootJar`
- `./gradlew test` — run all tests
- `./gradlew test --tests "com.hyblerm.homecontroller.service.rules.TimerJobTest"` — single test class
- `./gradlew bootRun` — run locally (**hits live OpenHAB** unless `spring.profiles.active=test-mode`)
- `./deploy.sh` — SCPs `build/libs/HomeController-0.0.1-SNAPSHOT.jar` to `dev@192.168.1.109:/home/dev/app/`, then SSHes and runs `/home/dev/app/run-home-portal.sh` (that script lives only on the server). Live deployment.

**Rule types in `application.yaml`:**
- `app.timerJobs` — time-window on/off rules. Adding a new scheduled device is a YAML-only change; each entry maps four OpenHAB item names (`switchItem`, `statusItem`, `startHourItem`, `endHourItem`) plus optional `mode` and `conditions`.
- `app.minMaxJobs` — thermostat-style min/max rules (e.g. chick brooder temperature).

**Architecture layers** (`com.hyblerm.homecontroller.*`):
- `service/rules/` — pure domain logic, depends only on `DataAccess` abstraction
- `service/repository/` — `DataAccess` interface, electricity pricing facade
- `repository/` — Spring implementations (`OpenHabRepository` via WebClient; `OpenHabReadOnlyRepository` for `test-mode`)
- `config/` — `ConfigurationProperties` (`app.*`), cache (20s TTL on item GETs), scheduling

**Test shape:** mock `DataAccess` + `Time` (inject `Clock.fixed`), stub items as `OpenHabModel.Item("link", name, value)`, assert via `verify(dataAccess).commandItem(...)`. See `TimerJobTest` as the canonical example.
