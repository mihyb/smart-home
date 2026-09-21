# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & test

Spring Boot 2.6 / Kotlin 1.9 / Gradle. Source level is Java 11, but the Gradle plugins require **JDK 17** to build (see `build.sh`, which sources `sdkman` and selects `17.0.7-tem`).

- Full build (matches CI/local convention): `./build.sh` — runs `ktlintFormat` then `clean build bootJar`.
- Tests only: `./gradlew test` (JUnit 5 / `useJUnitPlatform()`).
- Single test class: `./gradlew test --tests "com.hyblerm.homecontroller.service.rules.TimerJobTest"`.
- Single test method: append `.methodName` to the `--tests` filter (use backticked Kotlin names verbatim).
- Lint: `./gradlew ktlintCheck` / `./gradlew ktlintFormat` (ktlint is part of the standard build).
- Run locally: `./gradlew bootRun`. **Warning:** without `spring.profiles.active=test-mode` this commands the live OpenHAB instance at `${OPENHAB_HOST:127.0.0.1}` — which on the server is openHAB on the same host, but from a laptop is nothing, so set `OPENHAB_HOST` explicitly if you mean to reach the real one. The `test-mode` profile swaps in `OpenHabReadOnlyRepository`, which logs `commandItem` calls instead of executing them.
- Deploy: `./deploy.sh` scps the boot jar to `ruprecht@192.168.1.132` and restarts `homecontroller.service` over SSH. Override with `HOST=... ./deploy.sh`. This is a real deployment to the home server — confirm before running. `deploy/MIGRATION.md` describes the older .109 → .124 move and is historical; the current topology is in the repo-root CLAUDE.md.

## Architecture

The app is a Spring Boot rules engine that polls and commands an **OpenHAB** smart-home server over its REST API. It is not a CRUD app; the interesting flow is scheduled rule evaluation, not request handling.

**Layering** (`com.hyblerm.homecontroller.*`):
- `api/` — Spring REST controllers (currently just `ManualJobApi`, a stub for manually triggering jobs).
- `service/rules/` — domain logic. `service/rules/common/job/` holds rule implementations (`TimerJob`, `CalendarJob`) extending `JobBase`. `service/rules/common/items/` wraps OpenHAB items as domain objects (`Switch`, `CalendarItem`, `GenericItem`). Rules talk only to the `DataAccess` abstraction, never to WebClient directly.
- `service/repository/` — `DataAccess` interface plus the electricity-pricing facade (`ElectricityRateProvider`, `ElectricityRates`, evaluators).
- `repository/` — concrete Spring repositories. `OpenHabRepository` is the primary `DataAccess` impl (WebClient against `app.openhab.baseUrl`). `OpenHabReadOnlyRepository` is `@Primary @Profile("test-mode")` and overrides `commandItem` to a no-op log. `BuyElectricityDailyRateLoader` scrapes `ote-cr.cz` HTML for hourly spot prices and caches them to H2 via `ElectricityRepository` (Spring Data JPA).
- `config/` — `ConfigurationProperties` binds `app.*` from `application.yaml` (notably `app.timerJobs`, the list of scheduled switch rules). `CacheConfig` defines a custom `CacheWithExpiration` (TTL 20s) used to cache OpenHAB item GETs (`@Cacheable("item")` on `OpenHabRepository.getItem`). `ScheduleConfig` enables `@EnableScheduling` outside the `test` profile.

**Scheduled execution.** `TimerJobRunner` is the entry point — `@Scheduled(fixedRate = 5, MINUTES)` iterates every entry in `config.timerJobs` and invokes a fresh `TimerJob(...).checkSwitch()`. `MinMaxJobRunner` does the same for `config.minMaxJobs`, a thermostat-style rule that switches on below `minValueItem` and off above `maxValueItem`. `BoilerModeJobRunner` does the same for `config.boilerModeJobs`, which sets the Atmos heating and hot-water modes from whether the boiler is burning — the exhaust fan running *or* the boiler's own water above `burningAboveCelsius`, because the fan stops on an overheat exactly when the heat needs taking away, and residual heat with the fan off is not a burn. Adding a new timer-driven switch is a YAML edit (see `application.yaml` `app.timerJobs`), not a code change. Each entry references OpenHAB items by name for `switchItem`, `statusItem`, `startHourItem`, `endHourItem`; `mode` (`ALL`/`WEEKDAY`/`WEEKEND`) and `conditions` (additional item-state preconditions) are optional.

**TimerJob semantics.** `TimerJob.checkSwitch` reads start/end hours dynamically from OpenHAB items each tick, and handles cross-midnight ranges (`startHour > endHour`) via a separate code path. Conditions short-circuit to "off"; mode gating short-circuits to "skip".

**Time abstraction.** `service/util/Time.kt` wraps `Clock.systemDefaultZone()` so tests can inject `Clock.fixed(...)` via Mockito. Always use `time.clock()` rather than `Instant.now()` etc. inside rule code that needs to be testable.

**Persistence.** H2 in file mode at `${H2_DATA_PATH:/tmp/data/demo}` with `ddl-auto: create` (schema is recreated on every boot — only used as a cache for scraped electricity rates, not durable state). The test profile swaps to `jdbc:h2:mem:mydb`.

**Spring Boot Admin.** The app is both an Admin server and an Admin client pointing at itself (`spring.boot.admin.client.url: http://127.0.0.1:${app.port}`), so the running instance self-monitors at port 8181.

## Test conventions

- Mockito + `mockito-kotlin` + AssertJ + JUnit 5. Inline mock-maker is enabled via `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`.
- Rule tests follow the pattern in `TimerJobTest`: mock `DataAccess` and `Time`, stub `time.clock()` with `Clock.fixed`, stub item lookups with `OpenHabModel.Item("link", name, value)`, then assert against `verify(dataAccess).commandItem(...)`. Reuse this shape for new rules.

## Items without a value

openHAB reports `NULL` for an item whose thing is offline, and for virtual items
until persistence restores them after a restart. `getDouble()` throws on that —
it killed the scheduled task every five minutes when the chick brooder's sensor
went offline. Rules that read sensors or setpoints use `getDoubleOrNull()` and
skip the cycle instead. Both cases have regression tests in `MinMaxJobTest`.

## Items whose state carries a unit

A `Number:Temperature` item comes back from the REST API as a rendered
QuantityType — `Atmos_Boiler_Water` is the string `86.5 °C`, not `86.5`. So
`getDoubleOrNull()` returns null for every reading one of them ever produces,
and a rule that guards on null quietly stops acting instead of failing.
`getQuantityOrNull()` reads the number and ignores the unit; it converts
nothing, so use it only where the item's unit is fixed. `OpenHabModelTest`
covers both.

## The item-name contract

Every item name in `application.yaml` must exist in the openHAB config, and
nothing checks it at runtime — a rename makes this app poll something absent and
silently never act. `../scripts/check-item-contract.sh` verifies it.
