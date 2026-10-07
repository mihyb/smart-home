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
| `config/` | `item-states.tsv` — captured setpoints, see *Item state* below. `thermal-model.tsv` — fitted per-room coefficients, see *Weather forecast and history* |

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
python3 scripts/fit-thermal-model.py       # history -> config/thermal-model.tsv
python3 scripts/fit-buffer-drain.py        # what a day costs the tank -> boilerAdviceJobs
python3 scripts/test_fit_thermal_model.py  # its tests; stdlib unittest, no dependencies
```

The heating advice needs no command: it runs in HomeController and shows up in the
*Doporučení topení* frame at the top of the sitemap, and as notifications.

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

**fencee Cloud times out on itself, several times a day.** `GET
/client/v2/devices` answers `HTTP 504 Endpoint request timed out` — their API
gateway giving up on their own backend, not a timeout of ours — around eight
times a day, and the next attempt always works. One failed request used to blank
every fence item for a full poll interval, because the binding only retried on
the next tick 300 s later. It now retries once inside the same tick after 15 s,
keeps its session and its push socket across a transport failure, and treats an
expired session as something to sign in again for rather than as a refused
password. `grep fenceecloud:account /var/log/openhab/events.log` on the server
shows the windows.

**The electric fence has no local path at all.** The GW100 gateway answers
ping and nothing else — 1039 TCP ports closed, no UDP, no mDNS — and holds one
outbound connection to fencee Cloud. Every reading and every command crosses
that, so the binding signs in with the phone app's account and the fence is
blind whenever the internet or the cloud is down. A stale fence voltage
therefore does not mean a live fence, which is why the sitemap shows the time
of the last message next to each energizer.

**A boiler that has stopped its fan is the hottest one there is.** The mode
job used to read `Atmos_Exhaust_Fan` alone as "is it burning", which is true
right up to the moment it matters: on an overheat the boiler shuts its own fan
down while the water is at its hottest, so the automation read "out", put the
heating back on its schedule and the hot water into STANDBY — taking both
circuits off the boiler at the one moment the heat had nowhere to go. Burning is
now either signal: the fan running, **or** `Atmos_Boiler_Water` above
`burningAboveCelsius` (85). Both are needed to call it out, and both have to be
readable — one that reads `NULL` is not a "no", so the job holds. 85 is above
the residual heat a boiler that has gone out coasts down through, which is why a
plain temperature threshold could not do this job on its own: set low enough to
catch the overheat it also read residual heat as a burn, and the water circuit
kept draining a tank with nothing refilling it.

**A window contact reads ON when the window is open.** The two of them publish
zigbee2mqtt's `alarm_1` with `on="true"`, so ON is the alarm — open. That is why
the dehumidifier timer jobs require the contact to read `off` before they run, and
why the thermal fit drops every step where it reads ON. The sitemap colours those
two rows `ON="green"`, which reads the other way round.

**A `Number:Temperature` item is not a number to HomeController.** The REST API
renders a QuantityType with its unit, so `Atmos_Boiler_Water` arrives as
`86.5 °C` and `getDoubleOrNull()` returns null for every reading it will ever
have — silently, which in a rule means "no value, skip the cycle" forever.
`getQuantityOrNull()` takes the number and drops the unit. It converts nothing,
so it is only safe where the item's unit is fixed and known.

**Items read `NULL` when their thing is offline.** `getDouble()` throws on that;
`getDoubleOrNull()` exists for rules that read sensors. MinMaxJob and TimerJob
skip the cycle rather than die.

## Weather forecast and history

Two things the house had no way of knowing: what the weather is about to do, and
what it did. Both are groundwork for heating that decides rather than follows a
clock, and neither is worth anything unless it is *stored*.

**The forecast is Open-Meteo over the HTTP binding** — `things/openmeteo.things`
and `items/weather.items`. No account and no API key, so nothing about it is
gitignored and a rebuilt machine gets its forecast back with the rest of the
config. `weather_temp_h0` … `_h12` are hourly outdoor temperatures with h0 the
hour we are in, `weather_solar_h0` … `_h6` the shortwave radiation, which is the
only sun signal in the house — there is no pyranometer, so that forecast is also
the solar history the fit regresses against.

**`past_hours=0&forecast_hours=13` is what makes the indexes mean anything.** A
JSONPATH index is fixed, so the arrays have to start at the current hour. Left
out, Open-Meteo returns whole days starting at midnight and `[1]` stops meaning
"+1 h" as soon as the day is underway — every item still holds a plausible
temperature, just one from the wrong part of the day. Verified against the live
API: at 08:31 local the first hourly entry was 08:00. The daily radiation sums are
the one place the unit is not what it looks like: `MJ/m²`, not `Wh/m²`.

**Three persistence services now, each with one job.** rrd4j keeps
`restoreOnStartup` and the sitemap charts, mapdb the four String setpoints rrd4j
cannot restore, and jdbc (SQLite, `persistence/jdbc.persist`) the history a model
can be fitted from. That last one is not duplication: rrd4j is a round-robin
database and averages samples into coarser archives as they age, so a cooldown
read back from it is a smoothed curve rather than what the sensor said, and the
time constant fitted from it comes out wrong. There is deliberately no
`restoreOnStartup` in `jdbc.persist` — two services restoring one item would put
two sources on the same state at startup.

**The addon id is `jdbc-sqlite`, not `jdbc`.** Each database has its own feature in
the distribution and plain `jdbc` resolves no driver. The addon ships its own
`services/jdbc.cfg` with `override="false"`, so the copy in this repo wins.
`./scripts/status.sh openhab` reports how many points the last hour actually
stored, because a persistence service that failed to come up is otherwise silent
— openHAB just stores nothing, and the gap surfaces weeks later when there is
nothing to fit.

**`Atmos_C1_Pump` exists for the fit.** Every room is heated by the boiler's
circuit as well as by its own electric panel, so a room warming while that pump
runs says nothing about either the panel or the heat loss. The fit drops every
sample taken while it is ON. Without that the coefficients come out of a mixture
of two heat sources and look reasonable while being wrong.

**The model is fitted offline and committed, never learned at runtime.**
`scripts/fit-thermal-model.py` reads history through openHAB's REST persistence
API — no sudo, and no torn read of a database openHAB is writing — and writes
`config/thermal-model.tsv`: per room a time constant with the panel off and one
with it on, what the sun is worth, the internal gains, what the panel adds, and
how far above outdoor the panel alone can hold the room. That last number is the
one to read first: a room that holds 7 K above outdoor is comfortable at 14 °C
outside and hopeless at −5, and starting it earlier changes nothing about that.
Two time constants and not one because a house has two — the air responds in an
hour, the structure in days, and a single fit over both predicts a preheat that
would have to start the previous evening. The script refuses a fit rather than
producing a number from too few samples or a non-physical loss coefficient, and
writes nothing at all if no room fitted, so a bad fit shows up in a diff instead
of in the house. `scripts/test_fit_thermal_model.py` checks it against a synthetic
room whose coefficients are known; it found two real bugs on the way in, a stale
hold that dropped every sample once the circuit pump had been idle three days, and
a persisted `NULL` being read as `OFF`.

## Heating advice

Three questions are answered every five minutes, and **none of the answers switches
anything**. Each job writes what it thinks into an item, the alarm table turns the
`*_alert` items into a push, and the panels stay switched by hand. That is the point
of this stage: the advice can be read against what the house and the tank actually
did before it is ever allowed to act. `scripts/status.sh` and the *Doporučení topení*
frame at the top of the sitemap are where it is read.

**Per-room comfort bands are item state; occupancy hours are not.** A band is a
preference somebody changes because they were chilly, so it belongs on the sitemap. A
school timetable changes twice a year and "weekdays only" needs a day selector that a
`Setpoint` widget cannot express, so the hours live in `application.yaml`.
`room_min_*` / `room_max_*` are item state set from the sitemap — 20-22 in the living room, 19-22 in the kids'
room, 18-22 in the study. Below the minimum something should heat, above the maximum
it should stop, between them there is nothing to say; the same shape MinMaxJob
already uses for the brooder. Hallway, bathroom, entry hall and cellar are monitored
and nothing more, and they stay out of every decision by having no band set rather
than by being special-cased anywhere. A room whose band reads NULL gets no vote.

**A room nobody is in does not ask for heat.** Occupancy is written as the hours a
room is *empty*, because that is how the house is actually known — school, working
hours, bedtime — and because it makes the safe default the right one: a room with no
hours written down is always counted, so it asks for heat rather than quietly going
without. The night, 21:00 to 06:00, applies to every room and is configured once;
`offWindows` on a room are on top of it, and a `mode` of `weekday` carries the school
day. As set: kids' room off 07:00–14:00 on weekdays, study off 15:00–22:00, living
room nothing but the night.

**The minimum-run rule is on the panels only, and that asymmetry is deliberate.**
Switching a panel on ten minutes before its room empties buys nothing — the
electricity is spent on the hour it is used. A *fire* that late is not wasted at all,
because what it does not deliver tonight it leaves in the tank for tomorrow. So
`minimumRunMinutes` (45) gates the panel advice and there is no equivalent on the
boiler. `HouseDemandJob` publishes how many minutes of use each room has left and the
panel job applies its own threshold to that, rather than each job computing hours of
its own.

**One cold room is local, several are the boiler's.** `HouseDemandJob` counts the
rooms below their band and decides which kind of problem it is. Firing a system this
size for one room that dipped wastes a burn — it takes hours to come up and is
charged to 75-100 % in one go because cycling it is worse — and running panels in
three rooms wastes money, because wood is the cheaper heat. **A cold room with no
panel of its own escalates on its own, whatever the count**: the kids' room has a
dehumidifier and no heating, so the boiler is the only thing that can answer for it.
A room with its window open takes no part — it is cold for a reason, and not a reason
to light a boiler.

**The escalation is deliberately hard to trigger.** `heating_house_alert` comes up
only when the tank is flat *and* nothing is burning, because heat in the tank is
already on its way into the rooms and a fire lit ten minutes ago has not charged it
yet. On top of that the alarm waits half an hour and repeats at two hours. A fire
takes an hour to act on and hours to undo, so this is the one advisory that must not
be eager.

**`boiler_advice` answers tomorrow, not now.** What the forecast will cost the tank
against what the tank holds. The alert is raised only between 16:00 and 22:00, because
that is when lighting a boiler for tomorrow is still something a person can do. The
sun credit starts at zero on purpose — a credit that is guessed asks for too little
wood.

**Most of the tank's daily demand is not weather, it is the schedule.** The tank feeds
the heating circuit and nothing else — hot water comes off the boiler while it burns
and off the electric boiler when it does not — so the drain looks like it ought to be
pure outdoor temperature. It is not. The circuit runs the regulator's weekly programme
whenever it is in AUTO, so `scripts/fit-buffer-drain.py` measures **33 % of the tank a
day plus only 0.7 % per degree-day**: 2.6 days per full tank, which is the two-to-three
days this house actually gets. The first version of the advisory had demand linear
through zero and so predicted 13 % for a mild day that really costs about 35 — nearly
all of the demand sat in an intercept the model did not have.

**A naive fit of that measures an empty tank, not a day's demand.** A day that starts
with 20 % in the tank cannot drain 40 % however cold it is, so those days pull the
fitted demand down hard; across all days the apparent cost came out at 20 %/day against
38 on the days that began full. The script uses only days that started above 50 %,
which in practice means the day after a fire, and there are few of them.

**The baseline is charged only on a day that needs heating at all.** In summer the
circuit is off and the tank drains nothing, so a flat baseline would ask for wood in
July. That leaves a cliff at the base temperature, which is blunt but is the shape the
regulator's own summer changeover already has.

**Both coefficients were fitted over a mild autumn and nothing colder** — daily means
of 8.5 to 14.2 °C. The slope is the weak half, and `fit-buffer-drain.py` marks every
row it extrapolates. Under-predicting asks for too little wood, which is the expensive
direction, so re-run it after the first proper cold spell and compare the extrapolated
rows against what the tank did.

**Wood first is encoded, not assumed.** A panel is never advised for a room the
boiler is already heating: not while `Atmos_C1_Pump` runs, and not while the tank
holds more than `tankCoversAbovePercent`. When the house as a whole is cold the
advice becomes `BOILER` and the panel alert stays down, so one message about the
house replaces one per room. A panel already running is never told to stop for that
reason — it is doing no harm, and nagging costs more trust than it saves.

**`wheater_status` is not a general cheap-electricity signal.** There are two
tariffs: a general one that is low about twenty hours a day, and a narrower one for
the water boiler. `wheater_status` is the water boiler's circuit, through its plug's
zigbee availability, so it says nothing about the other. With the general tariff low
twenty hours out of twenty-four there is almost nothing to shift the panels into, so
no advisory tries to — the saving here is using wood instead of electricity, not
using electricity at a better hour.

**Anything unreadable holds.** Every one of these jobs answers `UNKNOWN` rather than
guessing when a sensor, a band or the forecast cannot be read, and `UNKNOWN` raises
no alert. That would be a silent failure — an advisory that has stopped working looks
exactly like a house that is fine — so the `advice-blind` alarm fires after three
hours of it.

**If the advisories are ever given the switch**, the timer jobs for
`Infrared_heating_panel_switch` and `obyvak_topeni_switch` have to come off first.
Both are in `app.timerJobs` with their status items currently OFF; two things driving
one switch on different rules would fight every five minutes.

## Rules in `application.yaml`

- `app.timerJobs` — time-window on/off. Four item names, optional `mode`
  (ALL/WEEKDAY/WEEKEND) and optional `conditions` (item must equal value, else the
  device is switched off and the window skipped).
- `app.minMaxJobs` — thermostat-style. On below `minValueItem`, off above
  `maxValueItem`. Used for the chick brooder.
- `app.boilerModeJobs` — follows the solid-fuel boiler. Burning means
  `runningItem` (the exhaust fan) ON **or** `temperatureItem` (the boiler's own
  water) above `burningAboveCelsius` — see *A boiler that has stopped its fan*
  above. Which mode each of the four cases means is not in this file: it is read
  from `boiler_auto_running_heating`,
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
