#!/usr/bin/env python3
"""Measure how fast the accumulation tank drains, and what that costs per day.

Answers the one question the boiler advisory needs calibrating against: how much of
the tank does a day eat? It prints the two numbers to put in `app.boilerAdviceJobs`
in HomeController's application.yaml --

    baselinePercentPerDay      what a day costs regardless of the weather
    chargePercentPerDegreeDay  what each degree-day adds on top

-- and the days-per-full-tank each implies, which is the figure to sanity-check
against experience. "A full tank lasts two or three days" is a measurement, and it is
the one worth trusting over any fit.

WHY A CONSTANT TERM AT ALL. The tank feeds the heating circuit and nothing else --
hot water comes off the boiler while it burns and off the electric boiler when it does
not -- so the drain looks like it should be pure weather. It is not: the circuit runs
the regulator's weekly programme whenever it is in AUTO, so most of the load is
schedule-driven and the regulator's equithermal curve only modulates it. Over a
September-to-October range the measured slope is small and the constant carries almost
everything. A model that is linear through zero therefore predicts nearly no demand
for a mild day, which is wrong by a factor of three.

THE CENSORING TRAP, which is what makes a naive fit useless. A day that starts with
20 % in the tank cannot drain 40 %, however cold it is -- the drain measures what was
available, not what was wanted. Those days pull the fitted demand down hard. Only days
that began above FULL_ENOUGH_PERCENT are used, which in practice means the day after a
fire. There are few of them, so the fit is honest about how few.

RANGE. The slope is only as good as the spread of weather it was fitted over, and the
script says what that spread was. Fitted over mild weather it will under-predict a cold
spell -- and under-predicting means asking for too little wood, which is the expensive
direction. Re-run it after the first real cold and compare.

Reads rrd4j, because that is where the long history is; it is averaged, which is fine
for daily totals and not fine for anything shorter (see scripts/fit-thermal-model.py,
which needs the exact shape of a cooldown and so reads jdbc instead).

Usage:
    python3 scripts/fit-buffer-drain.py
    python3 scripts/fit-buffer-drain.py --days 60 --base 18
"""

import argparse
import collections
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

DEFAULT_HOST = "192.168.1.132"
DEFAULT_PORT = 8080
DEFAULT_DAYS = 30
PERSISTENCE_SERVICE = "rrd4j"
REQUEST_TIMEOUT_S = 60

CHARGE_ITEM = "Atmos_Buffer_Charge"
OUTDOOR_ITEM = "Atmos_Outdoor"
BURNING_ITEM = "Atmos_Exhaust_Fan"

# Below this at the start of a day, the drain measures an empty tank rather than a
# day's demand.
FULL_ENOUGH_PERCENT = 50.0

# A fire anywhere in the day makes its charge trace a mixture of filling and
# emptying, and the daily drop stops meaning anything. rrd4j averages a switch, so
# this is a duty cycle rather than a state.
MAX_BURN_DUTY = 0.02

# rrd4j answers on a 15 minute grid, so a full day is 96 samples; this allows for a
# few gaps while still rejecting today, which is only ever partial.
MIN_SAMPLES_PER_DAY = 90

DEFAULT_BASE_CELSIUS = 18.0
FULL_TANK_PERCENT = 100.0
MIN_DAYS_FOR_A_SLOPE = 5

STATE_ON = "ON"
STATE_OFF = "OFF"
UNREADABLE = ("NULL", "UNDEF")


@dataclass
class Day:
    start_percent: float | None = None
    drained_percent: float = 0.0
    temperatures: list[float] = field(default_factory=list)
    burn_duty: list[float] = field(default_factory=list)


def fetch(host: str, port: int, item: str, days: int) -> list[tuple[int, float]]:
    """One item's history as (epoch millis, value), ON/OFF read as 1/0."""
    start = (datetime.now(timezone.utc) - timedelta(days=days)).strftime("%Y-%m-%dT%H:%M:%SZ")
    query = urllib.parse.urlencode({"serviceId": PERSISTENCE_SERVICE, "starttime": start})
    url = f"http://{host}:{port}/rest/persistence/items/{urllib.parse.quote(item)}?{query}"
    try:
        with urllib.request.urlopen(url, timeout=REQUEST_TIMEOUT_S) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        raise SystemExit(f"{item}: openHAB answered {error.code} {error.reason}")
    except (urllib.error.URLError, OSError) as error:
        raise SystemExit(f"{item}: cannot reach openHAB at {host}:{port} ({error})")
    except json.JSONDecodeError as error:
        raise SystemExit(f"{item}: openHAB did not answer with JSON ({error})")

    points: list[tuple[int, float]] = []
    for point in payload.get("data", []):
        state = str(point["state"])
        if state in UNREADABLE:
            continue
        if state == STATE_ON:
            value = 1.0
        elif state == STATE_OFF:
            value = 0.0
        else:
            try:
                value = float(state.split(" ")[0])
            except ValueError:
                continue
        points.append((int(point["time"]), value))
    points.sort(key=lambda point: point[0])
    return points


def day_of(millis: int) -> str:
    return datetime.fromtimestamp(millis / 1000).strftime("%Y-%m-%d")


def collect(charge: list[tuple[int, float]], outdoor: list[tuple[int, float]], burning: list[tuple[int, float]]) -> dict[str, Day]:
    days: dict[str, Day] = collections.defaultdict(Day)
    for index in range(1, len(charge)):
        time, value = charge[index]
        previous = charge[index - 1][1]
        day = days[day_of(time)]
        if day.start_percent is None:
            day.start_percent = previous
        if value < previous:
            day.drained_percent += previous - value
    for time, value in outdoor:
        days[day_of(time)].temperatures.append(value)
    for time, value in burning:
        days[day_of(time)].burn_duty.append(value)
    return days


def usable(day: Day) -> bool:
    if len(day.temperatures) < MIN_SAMPLES_PER_DAY or day.start_percent is None:
        return False
    if day.start_percent < FULL_ENOUGH_PERCENT:
        return False
    if day.burn_duty and sum(day.burn_duty) / len(day.burn_duty) > MAX_BURN_DUTY:
        return False
    return True


def fit(rows: list[tuple[float, float]]) -> tuple[float, float] | None:
    """Least squares for drain = constant + slope * degree_days."""
    count = len(rows)
    sum_x = sum(row[0] for row in rows)
    sum_y = sum(row[1] for row in rows)
    sum_xx = sum(row[0] ** 2 for row in rows)
    sum_xy = sum(row[0] * row[1] for row in rows)
    determinant = count * sum_xx - sum_x * sum_x
    if abs(determinant) < 1e-9:
        return None
    slope = (count * sum_xy - sum_x * sum_y) / determinant
    constant = (sum_y * sum_xx - sum_x * sum_xy) / determinant
    return constant, slope


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--days", type=int, default=DEFAULT_DAYS)
    parser.add_argument("--base", type=float, default=DEFAULT_BASE_CELSIUS, help="degree-day base temperature")
    arguments = parser.parse_args()

    days = collect(
        fetch(arguments.host, arguments.port, CHARGE_ITEM, arguments.days),
        fetch(arguments.host, arguments.port, OUTDOOR_ITEM, arguments.days),
        fetch(arguments.host, arguments.port, BURNING_ITEM, arguments.days),
    )

    rows: list[tuple[float, float]] = []
    print(f"{'day':<12}{'start %':>9}{'mean out':>10}{'degree-days':>13}{'drained %':>11}")
    for name in sorted(days):
        day = days[name]
        if not usable(day):
            continue
        mean = sum(day.temperatures) / len(day.temperatures)
        degree_days = max(0.0, arguments.base - mean)
        rows.append((degree_days, day.drained_percent))
        print(f"{name:<12}{day.start_percent:>9.0f}{mean:>10.1f}{degree_days:>13.1f}{day.drained_percent:>11.1f}")

    if len(rows) < MIN_DAYS_FOR_A_SLOPE:
        print(
            f"\nOnly {len(rows)} day(s) started above {FULL_ENOUGH_PERCENT:.0f} % with no fire in them. "
            "Not enough to fit anything -- come back after a few more burns.",
            file=sys.stderr,
        )
        return 1

    coefficients = fit(rows)
    if coefficients is None:
        print("\nEvery usable day had the same weather, so the slope cannot be separated.", file=sys.stderr)
        return 1
    constant, slope = coefficients

    mean_drain = sum(row[1] for row in rows) / len(rows)
    coldest = max(row[0] for row in rows)
    mildest = min(row[0] for row in rows)
    print(f"\n{len(rows)} usable days, demand not capped by an empty tank")
    print(f"  measured: {mean_drain:.1f} %/day on average, so {FULL_TANK_PERCENT / mean_drain:.1f} days per full tank")
    print(f"  fitted over degree-days {mildest:.1f} to {coldest:.1f} "
          f"(daily means {arguments.base - coldest:.1f} to {arguments.base - mildest:.1f} C)")
    print("\n  For HomeController/src/main/resources/application.yaml, app.boilerAdviceJobs:")
    print(f"      baselinePercentPerDay: {constant:.0f}")
    print(f"      chargePercentPerDegreeDay: {slope:.1f}")
    print(f"\n  {'mean out':>9}{'degree-days':>13}{'wants %':>9}{'days per tank':>15}   note")
    for mean in (15.0, 10.0, 5.0, 0.0, -7.0, -15.0):
        degree_days = max(0.0, arguments.base - mean)
        wants = constant + slope * degree_days
        note = "" if mildest <= degree_days <= coldest else "extrapolated, not measured"
        print(f"  {mean:>9.1f}{degree_days:>13.1f}{wants:>9.1f}{FULL_TANK_PERCENT / wants:>15.1f}   {note}")
    print(
        "\n  Under-predicting asks for too little wood, which is the expensive direction.\n"
        "  Re-run after the first proper cold spell and compare the extrapolated rows\n"
        "  against what the tank actually did."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
