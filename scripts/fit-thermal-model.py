#!/usr/bin/env python3
"""Fit a per-room thermal model from openHAB history.

What it produces, per room, is how fast the room loses heat and what its electric
panel can do about it -- the two numbers a predictive heating job needs and that
nothing in this repository could answer before:

    tau_off_h             hours for the room to lose 63 % of the gap to outdoor
    solar_k_per_h_per_wm2 how much sun through the windows is worth
    base_k_per_h          internal gains: people, appliances, neighbouring rooms
    heater_k_per_h        what the panel adds, on top of those gains
    hold_k_above_outdoor  how far above outdoor the panel alone can hold the room

The last one is the answer to "is a panel enough, or does the boiler have to be
lit": a room that holds 7 K above outdoor is comfortable at 14 C outside and
hopeless at -5 C, and no amount of switching it on earlier changes that.

The model is one capacity per room, fitted twice -- once over samples with the
panel off, once with it on:

    dTin/dt = (Tout - Tin)/tau + solar*I + base [+ heater]

Fitting it separately for on and off is deliberate. A house has two time
constants, not one: the air and the surfaces it touches respond in an hour, the
structure in days. A single fit over everything splits the difference and gets
both wrong -- it then predicts a preheat that has to start eight hours early.
tau_off is dominated by the structure, tau_on by the air, and it is tau_on that
decides when to switch a panel on.

Where the data comes from: the jdbc (SQLite) persistence service, read through
openHAB's REST API rather than off the file, so there is no sudo, no torn read of
a database openHAB is writing, and nothing to copy down. rrd4j deliberately is not
used -- it averages samples into coarser archives as they age, so a cooldown read
back from it is a smoothed curve rather than what the sensor said, and the time
constant fitted from it comes out wrong.

Samples are dropped, not modelled, whenever something else was heating the room:
the boiler's circuit pump running, or a window open. That is why the fit needs
Atmos_C1_Pump, which is what the item exists for.

Usage:
    python3 scripts/fit-thermal-model.py                    # last 21 days -> config/thermal-model.tsv
    python3 scripts/fit-thermal-model.py --days 40
    python3 scripts/fit-thermal-model.py --host 192.168.1.132 --out /tmp/model.tsv

Run it after a cold spell, read the r2 columns before believing any of it, and
commit the result -- the runtime is meant to evaluate these coefficients, never to
learn them, so that a bad fit shows up in a diff instead of in the house.
"""

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

DEFAULT_HOST = "192.168.1.132"
DEFAULT_PORT = 8080
DEFAULT_DAYS = 21
DEFAULT_OUT = "config/thermal-model.tsv"
PERSISTENCE_SERVICE = "jdbc"
REQUEST_TIMEOUT_S = 60

# The grid every series is resampled onto. Ten minutes is long enough that a
# 0.1 K sensor resolution does not swamp the rate of change, and short enough to
# see the first hour of a heat-up, which is where tau_on lives.
STEP_MINUTES = 10

# How long a stored value may stand in for the present. A measurement that has
# not been updated within this is treated as missing rather than held, so a dead
# sensor leaves a gap instead of a flat line the fit would read as thermal mass.
MEASUREMENT_HOLD_MINUTES = 30
# Forecast items are written hourly, so they need a longer leash.
FORECAST_HOLD_MINUTES = 90
# Switches deliberately have no staleness limit at all, which is why none is
# named here. Persisted on change, a switch's last stored value *is* its state
# until the next one -- Atmos_C1_Pump does not change from May to October, and a
# window can stay shut for a month. Expiring a hold on those drops every sample
# taken since, which is nearly all of them.

# A room does not change temperature faster than this. Anything above it is a
# sensor glitch or a battery change, not thermodynamics.
MAX_PLAUSIBLE_RATE_K_PER_H = 8.0

# Below these a fit is reported as refused rather than printed. A handful of
# samples will always produce coefficients; they just will not mean anything.
MIN_SAMPLES_OFF = 200
MIN_SAMPLES_ON = 60

# Item names. The integration contract in this repository is item names, so they
# are named once, here.
OUTDOOR_ITEM = "Atmos_Outdoor"
SOLAR_ITEM = "weather_solar_h0"
WIND_ITEM = "weather_wind_h0"
CIRCUIT_PUMP_ITEM = "Atmos_C1_Pump"

# Window contacts publish zigbee2mqtt's alarm_1 with on="true", so ON is an open
# window. The timer jobs agree: they let the dehumidifiers run only while the
# contact reads OFF. Note the sitemap colours ON green on those two rows, which
# reads the other way round.
STATE_ON = "ON"
STATE_OFF = "OFF"
WINDOW_OPEN_STATE = STATE_ON
HEATER_ON_STATE = STATE_ON
PUMP_ON_STATE = STATE_ON

MISSING = "-"
TSV_HEADER = (
    "room",
    "sensor_item",
    "heater_item",
    "samples_off",
    "tau_off_h",
    "solar_k_per_h_per_wm2",
    "base_k_per_h",
    "r2_off",
    "tau_calm_h",
    "tau_windy_h",
    "samples_on",
    "tau_on_h",
    "heater_k_per_h",
    "hold_k_above_outdoor",
    "r2_on",
)


@dataclass(frozen=True)
class Room:
    """A room, its sensor, and whatever can heat or vent it locally.

    Sklep is deliberately absent. A cellar exchanges heat with the ground rather
    than with the outside air, so outdoor temperature is the wrong driver and a
    fit against it would produce a number that means nothing.
    """

    name: str
    sensor_item: str
    heater_item: str | None = None
    window_item: str | None = None


ROOMS = (
    Room("pracovna", "pracovna_teplota_temp", "Infrared_heating_panel_switch", "pracovna_okno_OnOff_Switch"),
    Room("obyvak", "obyvak_temp_temperature", "obyvak_topeni_switch"),
    Room("pokojicek", "pokojicek_temp_temperature", None, "pokoj_okno_pokoj_okno_switch"),
    Room("chodba", "chodba_temp_temperature"),
    Room("koupelna", "koupelna_temp_temperature"),
    Room("zadveri", "zadveri_temp_temperature"),
)


@dataclass
class Fit:
    """One least-squares result, or the reason there isn't one."""

    samples: int
    coefficients: list[float]
    r_squared: float
    refused: str = ""

    @property
    def ok(self) -> bool:
        return not self.refused


def fetch_series(host: str, port: int, item: str, start: datetime, end: datetime) -> list[tuple[int, str]]:
    """One item's stored history as (epoch millis, raw state), oldest first.

    boundary=true asks openHAB for the point before the window as well, without
    which every series starts as unknown until its first change inside it.
    """
    query = urllib.parse.urlencode(
        {
            "serviceId": PERSISTENCE_SERVICE,
            "starttime": start.isoformat().replace("+00:00", "Z"),
            "endtime": end.isoformat().replace("+00:00", "Z"),
            "boundary": "true",
        }
    )
    url = f"http://{host}:{port}/rest/persistence/items/{urllib.parse.quote(item)}?{query}"
    try:
        with urllib.request.urlopen(url, timeout=REQUEST_TIMEOUT_S) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        raise SystemExit(f"{item}: openHAB answered {error.code} {error.reason} -- is {PERSISTENCE_SERVICE} installed?")
    except (urllib.error.URLError, OSError) as error:
        raise SystemExit(f"{item}: cannot reach openHAB at {host}:{port} ({error})")
    except json.JSONDecodeError as error:
        raise SystemExit(f"{item}: openHAB did not answer with JSON ({error})")

    points = [(int(point["time"]), str(point["state"])) for point in payload.get("data", [])]
    points.sort(key=lambda point: point[0])
    return points


def hold(series: list[tuple[int, str]], grid: list[int], max_hold_ms: int | None) -> list[str | None]:
    """Resample onto the grid by holding the last stored value.

    A zero-order hold is what openHAB's own semantics imply: a value persisted on
    change is the item's state until the next one. Beyond max_hold_ms the hold
    stops, because at that point a measurement has gone quiet for a reason.
    max_hold_ms of None never stops, which is right for a switch and wrong for
    anything being measured.
    """
    held: list[str | None] = []
    index = 0
    for point in grid:
        while index + 1 < len(series) and series[index + 1][0] <= point:
            index += 1
        stale = max_hold_ms is not None and series and point - series[index][0] > max_hold_ms
        if not series or series[index][0] > point or stale:
            held.append(None)
        else:
            held.append(series[index][1])
    return held


def changed_within(series: list[tuple[int, str]], start_ms: int, end_ms: int) -> bool:
    """Whether the item changed state strictly inside the step.

    Holding the endpoints is not enough on its own: a panel that switched on and
    off again inside one step reads the same at both ends, and that step belongs
    to neither slice.
    """
    return any(start_ms < time <= end_ms for time, _ in series)


def known_switch(state: str | None) -> bool:
    """Whether a stored switch state is one that says anything.

    An item whose thing has gone offline persists NULL or UNDEF, and those are
    strings like any other: read as "not ON" they become an OFF that never
    happened, and the step gets filed under the wrong slice. Same rule as
    everywhere else in this repository -- unreadable is not a "no".
    """
    return state in (STATE_ON, STATE_OFF)


def number(state: str | None) -> float | None:
    """The number in a stored state, or None when there isn't one.

    Persisted QuantityType states carry their unit ("3.5 °C"), which is dropped
    here rather than converted -- the same rule as HomeController's
    getQuantityOrNull: only safe because every item read here has a fixed, known
    unit. NULL and UNDEF fall out as None.
    """
    if state is None:
        return None
    try:
        return float(state.split(" ")[0])
    except ValueError:
        return None


def solve(matrix: list[list[float]], vector: list[float]) -> list[float] | None:
    """Gaussian elimination with partial pivoting. None when singular."""
    size = len(vector)
    augmented = [row[:] + [vector[index]] for index, row in enumerate(matrix)]
    for column in range(size):
        pivot = max(range(column, size), key=lambda row: abs(augmented[row][column]))
        if abs(augmented[pivot][column]) < 1e-12:
            return None
        augmented[column], augmented[pivot] = augmented[pivot], augmented[column]
        for row in range(column + 1, size):
            factor = augmented[row][column] / augmented[column][column]
            for target in range(column, size + 1):
                augmented[row][target] -= factor * augmented[column][target]
    result = [0.0] * size
    for row in reversed(range(size)):
        total = augmented[row][size] - sum(augmented[row][col] * result[col] for col in range(row + 1, size))
        result[row] = total / augmented[row][row]
    return result


def least_squares(rows: list[tuple[list[float], float]], minimum: int) -> Fit:
    """Ordinary least squares through the normal equations, plus r2."""
    if len(rows) < minimum:
        return Fit(len(rows), [], 0.0, f"only {len(rows)} samples, {minimum} needed")

    width = len(rows[0][0])
    matrix = [[sum(row[i] * row[j] for row, _ in rows) for j in range(width)] for i in range(width)]
    vector = [sum(row[i] * target for row, target in rows) for i in range(width)]
    coefficients = solve(matrix, vector)
    if coefficients is None:
        return Fit(len(rows), [], 0.0, "regressors are collinear")

    mean = sum(target for _, target in rows) / len(rows)
    total = sum((target - mean) ** 2 for _, target in rows)
    residual = sum(
        (target - sum(coefficient * value for coefficient, value in zip(coefficients, row))) ** 2 for row, target in rows
    )
    r_squared = 1.0 - residual / total if total > 0 else 0.0
    return Fit(len(rows), coefficients, r_squared)


@dataclass
class Sample:
    """One usable step: how fast the room moved, and what was acting on it."""

    rate_k_per_h: float
    gap_k: float  # outdoor minus indoor, so a positive coefficient is a loss
    solar_w_per_m2: float
    wind_m_per_s: float
    heater_on: bool


def collect(room: Room, grid: list[int], series: dict[str, list[tuple[int, str]]], step_h: float) -> list[Sample]:
    """Every step of this room's history the model is allowed to learn from."""
    step_ms = int(step_h * 3600 * 1000)
    inside = hold(series[room.sensor_item], grid, MEASUREMENT_HOLD_MINUTES * 60 * 1000)
    outside = hold(series[OUTDOOR_ITEM], grid, MEASUREMENT_HOLD_MINUTES * 60 * 1000)
    solar = hold(series[SOLAR_ITEM], grid, FORECAST_HOLD_MINUTES * 60 * 1000)
    wind = hold(series[WIND_ITEM], grid, FORECAST_HOLD_MINUTES * 60 * 1000)
    pump = hold(series[CIRCUIT_PUMP_ITEM], grid, None)
    heater = hold(series[room.heater_item], grid, None) if room.heater_item else None
    window = hold(series[room.window_item], grid, None) if room.window_item else None

    samples: list[Sample] = []
    for index in range(len(grid) - 1):
        start_ms, end_ms = grid[index], grid[index + 1]

        # The boiler heating the same room makes the step say nothing about the
        # panel or about the loss, so it is dropped rather than modelled. An
        # unknown pump state is dropped too: it is not a "no".
        if not known_switch(pump[index]) or pump[index] == PUMP_ON_STATE:
            continue
        if changed_within(series[CIRCUIT_PUMP_ITEM], start_ms, end_ms):
            continue
        if window is not None and room.window_item:
            if not known_switch(window[index]) or window[index] == WINDOW_OPEN_STATE:
                continue
            if changed_within(series[room.window_item], start_ms, end_ms):
                continue

        heater_on = False
        if heater is not None and room.heater_item:
            if not known_switch(heater[index]):
                continue
            # A step the panel switched in is part heated and belongs to neither
            # slice.
            if changed_within(series[room.heater_item], start_ms, end_ms):
                continue
            heater_on = heater[index] == HEATER_ON_STATE

        start_temp, end_temp = number(inside[index]), number(inside[index + 1])
        outdoor = number(outside[index])
        radiation = number(solar[index])
        speed = number(wind[index])
        if None in (start_temp, end_temp, outdoor, radiation, speed):
            continue

        rate = (end_temp - start_temp) / step_h
        if abs(rate) > MAX_PLAUSIBLE_RATE_K_PER_H:
            continue
        samples.append(Sample(rate, outdoor - start_temp, radiation, speed, heater_on))
    return samples


def fit_slice(samples: list[Sample], minimum: int) -> Fit:
    """Fit dTin/dt = gap/tau + solar*I + base over one slice."""
    rows = [([sample.gap_k, sample.solar_w_per_m2, 1.0], sample.rate_k_per_h) for sample in samples]
    fit = least_squares(rows, minimum)
    if fit.ok and fit.coefficients[0] <= 0:
        # A non-positive coefficient says the room gains heat as it gets colder
        # outside. That is not a slow house, it is a fit with nothing in it --
        # usually a slice with no real temperature swing.
        return Fit(fit.samples, [], fit.r_squared, "loss coefficient came out non-physical")
    return fit


def tau_of(fit: Fit) -> float | None:
    return 1.0 / fit.coefficients[0] if fit.ok else None


def wind_split(samples: list[Sample]) -> tuple[float | None, float | None]:
    """tau over the calm half of the samples and over the windy half.

    Wind is left out of the fitted model on purpose -- a fourth regressor on this
    much data buys less than it costs. Splitting at the median answers the only
    question worth asking of it: whether wind matters enough here to model later.
    """
    if len(samples) < 2 * MIN_SAMPLES_OFF:
        return None, None
    ordered = sorted(samples, key=lambda sample: sample.wind_m_per_s)
    middle = len(ordered) // 2
    calm = fit_slice(ordered[:middle], MIN_SAMPLES_OFF)
    windy = fit_slice(ordered[middle:], MIN_SAMPLES_OFF)
    return tau_of(calm), tau_of(windy)


def number_or_missing(value: float | None, digits: int = 2) -> str:
    return MISSING if value is None else f"{value:.{digits}f}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default=DEFAULT_HOST, help=f"openHAB host (default {DEFAULT_HOST})")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--days", type=int, default=DEFAULT_DAYS, help=f"history to read (default {DEFAULT_DAYS})")
    parser.add_argument("--out", default=DEFAULT_OUT, help=f"where to write the TSV (default {DEFAULT_OUT})")
    arguments = parser.parse_args()

    end = datetime.now(timezone.utc)
    start = end - timedelta(days=arguments.days)
    step_h = STEP_MINUTES / 60.0
    grid = list(
        range(
            int(start.timestamp() * 1000),
            int(end.timestamp() * 1000),
            STEP_MINUTES * 60 * 1000,
        )
    )

    wanted = {OUTDOOR_ITEM, SOLAR_ITEM, WIND_ITEM, CIRCUIT_PUMP_ITEM}
    for room in ROOMS:
        wanted.add(room.sensor_item)
        if room.heater_item:
            wanted.add(room.heater_item)
        if room.window_item:
            wanted.add(room.window_item)

    print(f"==> reading {arguments.days} days from {arguments.host}, service {PERSISTENCE_SERVICE}")
    series: dict[str, list[tuple[int, str]]] = {}
    for item in sorted(wanted):
        series[item] = fetch_series(arguments.host, arguments.port, item, start, end)
        print(f"    {item:<34} {len(series[item]):>7} points")

    rows: list[tuple[str, ...]] = []
    fitted = 0
    for room in ROOMS:
        samples = collect(room, grid, series, step_h)
        off = [sample for sample in samples if not sample.heater_on]
        on = [sample for sample in samples if sample.heater_on]
        off_fit = fit_slice(off, MIN_SAMPLES_OFF)
        on_fit = fit_slice(on, MIN_SAMPLES_ON) if room.heater_item else Fit(0, [], 0.0, "no panel in this room")
        calm_tau, windy_tau = wind_split(off)

        heater_gain: float | None = None
        hold_above: float | None = None
        if off_fit.ok and on_fit.ok:
            heater_gain = on_fit.coefficients[2] - off_fit.coefficients[2]
            # Where the panel stops gaining: rate = 0 with no sun. Internal gains
            # are in it, which is honest -- the room really does have them.
            hold_above = on_fit.coefficients[2] / on_fit.coefficients[0]

        print(f"\n--- {room.name} ({len(samples)} usable steps) ---")
        if off_fit.ok:
            fitted += 1
            print(
                f"    heater off: tau {tau_of(off_fit):.1f} h, sun "
                f"{off_fit.coefficients[1] * 1000:.2f} mK/h per W/m2, gains "
                f"{off_fit.coefficients[2]:.2f} K/h, r2 {off_fit.r_squared:.2f} ({off_fit.samples} samples)"
            )
            if calm_tau and windy_tau:
                print(f"    wind:       tau {calm_tau:.1f} h calm vs {windy_tau:.1f} h windy")
        else:
            print(f"    heater off: no fit -- {off_fit.refused}")
        if on_fit.ok:
            print(
                f"    heater on:  tau {tau_of(on_fit):.1f} h, panel "
                f"{heater_gain:.2f} K/h, holds {hold_above:.1f} K above outdoor, "
                f"r2 {on_fit.r_squared:.2f} ({on_fit.samples} samples)"
            )
        elif room.heater_item:
            print(f"    heater on:  no fit -- {on_fit.refused}")

        rows.append(
            (
                room.name,
                room.sensor_item,
                room.heater_item or MISSING,
                str(off_fit.samples),
                number_or_missing(tau_of(off_fit), 1),
                number_or_missing(off_fit.coefficients[1] if off_fit.ok else None, 5),
                number_or_missing(off_fit.coefficients[2] if off_fit.ok else None),
                number_or_missing(off_fit.r_squared if off_fit.ok else None),
                number_or_missing(calm_tau, 1),
                number_or_missing(windy_tau, 1),
                str(on_fit.samples),
                number_or_missing(tau_of(on_fit), 1),
                number_or_missing(heater_gain),
                number_or_missing(hold_above, 1),
                number_or_missing(on_fit.r_squared if on_fit.ok else None),
            )
        )

    if not fitted:
        print("\nNo room produced a fit. Nothing written -- an empty model would be worse than none.", file=sys.stderr)
        return 1

    out = Path(arguments.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8") as handle:
        handle.write(f"# Thermal model fitted from {arguments.days} days ending {end.isoformat(timespec='seconds')}\n")
        handle.write(f"# Source: {arguments.host} persistence service '{PERSISTENCE_SERVICE}'\n")
        handle.write("# Regenerate with: python3 scripts/fit-thermal-model.py\n")
        handle.write("# Read r2_off and r2_on before trusting a row; '-' is a fit that was refused.\n")
        handle.write("\t".join(TSV_HEADER) + "\n")
        for row in rows:
            handle.write("\t".join(row) + "\n")
    print(f"\n==> {fitted} of {len(ROOMS)} rooms fitted -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
