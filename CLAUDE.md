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
| `atmos-connector/` | **Submodule** — the custom `atmoswg1000` OpenHAB binding for the Atmos boiler. Standalone reusable component, own repo and release cycle |
| `scripts/` | Build, deploy, status and log helpers for both halves |

`atmos-connector` is a submodule while the other two are subtrees, and that is
deliberate. OpenHAB config and HomeController are co-developed — an item rename
touches both, so they need to land in one commit. The binding is a standalone
library consumed as a built JAR; it has no reason to change in lockstep. The cost
is the usual submodule one: `git pull` leaves it at the old pin unless you pass
`--recurse-submodules`, so it will silently go stale if you forget. Clone with:

```bash
git clone --recurse-submodules git@github-personal:mihyb/smart-home.git
git submodule update --init --remote   # in an existing clone
```

### Git accounts

This machine has two GitHub identities. This is a **personal** repo and must use
the private one.

| | Account | Remote host | Commit email |
|---|---|---|---|
| Personal | `mihyb` | `git@github-personal:` (alias for github.com) | `m.hybler@gmail.com` |
| Work | `mhybler` | `git@github.com:` | `c_mhybler@groupon.com` |

`~/.ssh/config` maps `github-personal` to github.com with `~/.ssh/id_ed25519_mihyb`
and `IdentitiesOnly yes`. `~/.gitconfig` switches commit identity on the *remote*,
not the directory:

```
[includeIf "hasconfig:remote.*.url:git@github-personal:*/**"]
	path = ~/.gitconfig-personal
```

Keying on the remote matters because `~/Work/private/projects/` holds work repos
too (`claude-monitor` → github.groupondev.com); a `gitdir:` condition would
mislabel those. Note the glob: git treats `**` as special only as a whole path
component, so `git@github-personal:**` does *not* match — it collapses to `*`,
which cannot cross the `/` in `mihyb/repo`. The `:*/**` form is required.

New personal repos: `git remote add origin git@github-personal:mihyb/<repo>.git`
and the identity follows automatically. Verify with `git config user.email`.

Commits before Sep 2026 are authored under work or Cleverlance addresses — the
identity split postdates them.

### Reaching history from before the migration

Both projects' full histories are here (earliest commit Nov 2021), but they were
merged as subtrees, so pre-migration commits used paths without the
`openhab-ruprechtice/` or `HomeController/` prefix. Git's history simplification
stops at the merge, which makes a plain path-scoped log look empty:

```bash
git log -- openhab-ruprechtice/items/timerJob.items   # only the merge commit
git log --full-history -- 'openhab-ruprechtice/items/timerJob.items' 'items/timerJob.items'
```

Pass `--full-history` and both the new and old path. `git log` with no path, and
`git show <old-sha>`, work normally.

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

## The Atmos boiler binding

`atmos-connector/` builds `org.openhab.binding.atmoswg1000-<version>.jar`, which is
sideloaded into `/usr/share/openhab/addons/` on the OpenHAB host. It is not a
marketplace addon — there is no upstream to pull from.

The binding is pinned to the OpenHAB version by its pom parent:

```xml
<parent>
  <artifactId>org.openhab.addons.reactor.bundles</artifactId>
  <version>4.3.0</version>
</parent>
```

**Upgrading OpenHAB requires rebuilding this binding first.** Bump that parent to
the target version and fix the API drift. The 16 classes in `protocol/` are pure
protocol logic with no OpenHAB imports and should carry over untouched; the
handlers, discovery services and `HandlerFactory` are where the addon API moves
between majors. `Wg1000ProtocolTest` and `ValuesTest` verify the protocol still
parses after a bump.

`captured/` is gitignored — it holds ATMOS's own UI assets plus device-identifying
dumps (serial numbers, network details). Regenerate rather than commit:

```bash
python -m wg1000.cli --host <gateway> files --out ../captured
```

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
