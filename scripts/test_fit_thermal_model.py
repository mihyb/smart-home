#!/usr/bin/env python3
"""Tests for scripts/fit-thermal-model.py.

The interesting ones build a room whose physics are known -- a chosen time
constant, solar coefficient, internal gain and panel output -- run the fit over
it, and check the coefficients come back. A fit that silently includes steps it
should have dropped shows up here as a tau that is no longer the one the room was
given.

    python3 scripts/test_fit_thermal_model.py
    python3 -m unittest discover -s scripts -p 'test_*.py'
"""

import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("fit-thermal-model.py")
SPEC = importlib.util.spec_from_file_location("fit_thermal_model", MODULE_PATH)
assert SPEC and SPEC.loader
fit_thermal_model = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(fit_thermal_model)

MINUTE_MS = 60 * 1000
HOUR_MS = 60 * MINUTE_MS

# The room the synthetic tests are built from.
TRUE_TAU_H = 24.0
TRUE_SOLAR_K_PER_H_PER_WM2 = 0.004
TRUE_BASE_K_PER_H = 0.05
TRUE_HEATER_K_PER_H = 0.9
START_INDOOR_C = 20.0

# Long enough to clear MIN_SAMPLES_OFF twice over (wind_split needs that) and
# MIN_SAMPLES_ON in the heated tail.
OFF_STEPS = 1100
ON_STEPS = 400
EPOCH_MS = 1_700_000_000_000

# Outdoor swings on a day, sun on a two-thirds of a day, so that the solar term
# is not just a rescaled copy of the temperature gap.
OUTDOOR_MEAN_C = 4.0
OUTDOOR_SWING_C = 8.0
OUTDOOR_PERIOD_STEPS = 144
SOLAR_PEAK_W_PER_M2 = 500.0
SOLAR_PERIOD_STEPS = 96
WIND_MEAN_M_PER_S = 3.0
WIND_SWING_M_PER_S = 2.0
WIND_PERIOD_STEPS = 37

TOLERANCE = 1e-4


def sawtooth(index: int, period: int) -> float:
    """A triangle wave in [0, 1]. Deterministic, and no import of math needed."""
    position = (index % period) / period
    return 2.0 * position if position < 0.5 else 2.0 * (1.0 - position)


def outdoor_at(index: int) -> float:
    return OUTDOOR_MEAN_C + OUTDOOR_SWING_C * (sawtooth(index, OUTDOOR_PERIOD_STEPS) - 0.5)


def solar_at(index: int) -> float:
    return SOLAR_PEAK_W_PER_M2 * sawtooth(index, SOLAR_PERIOD_STEPS)


def wind_at(index: int) -> float:
    return WIND_MEAN_M_PER_S + WIND_SWING_M_PER_S * (sawtooth(index, WIND_PERIOD_STEPS) - 0.5)


class SyntheticRoom:
    """A room evolved by the model the fit assumes, so the fit can be checked.

    The panel switches on for the last ON_STEPS steps, which is what gives the
    heater-on slice something to fit.
    """

    def __init__(self, step_h: float) -> None:
        self.step_h = step_h
        self.grid = [EPOCH_MS + index * int(step_h * HOUR_MS) for index in range(OFF_STEPS + ON_STEPS + 1)]
        self.indoor: list[float] = [START_INDOOR_C]
        for index in range(len(self.grid) - 1):
            inside = self.indoor[index]
            heater = TRUE_HEATER_K_PER_H if index >= OFF_STEPS else 0.0
            rate = (
                (outdoor_at(index) - inside) / TRUE_TAU_H
                + TRUE_SOLAR_K_PER_H_PER_WM2 * solar_at(index)
                + TRUE_BASE_K_PER_H
                + heater
            )
            self.indoor.append(inside + rate * step_h)

    def series(self, values: list[float]) -> list[tuple[int, str]]:
        return [(time, f"{value:.10f}") for time, value in zip(self.grid, values)]

    def constant(self, state: str) -> list[tuple[int, str]]:
        return [(self.grid[0] - HOUR_MS, state)]

    def heater_series(self) -> list[tuple[int, str]]:
        return [(self.grid[0] - HOUR_MS, "OFF"), (self.grid[OFF_STEPS], "ON")]

    def all_series(self, pump: str = "OFF", window: str | None = None) -> dict[str, list[tuple[int, str]]]:
        series = {
            "room_temp": self.series(self.indoor),
            fit_thermal_model.OUTDOOR_ITEM: self.series([outdoor_at(i) for i in range(len(self.grid))]),
            fit_thermal_model.SOLAR_ITEM: self.series([solar_at(i) for i in range(len(self.grid))]),
            fit_thermal_model.WIND_ITEM: self.series([wind_at(i) for i in range(len(self.grid))]),
            fit_thermal_model.CIRCUIT_PUMP_ITEM: self.constant(pump),
            "room_heater": self.heater_series(),
        }
        if window is not None:
            series["room_window"] = self.constant(window)
        return series


def room(window: bool = False) -> "fit_thermal_model.Room":
    return fit_thermal_model.Room(
        "test", "room_temp", "room_heater", "room_window" if window else None
    )


class HoldTest(unittest.TestCase):

    def test_holds_the_last_stored_value(self):
        series = [(EPOCH_MS, "1"), (EPOCH_MS + 5 * MINUTE_MS, "2")]
        grid = [EPOCH_MS, EPOCH_MS + MINUTE_MS, EPOCH_MS + 5 * MINUTE_MS, EPOCH_MS + 6 * MINUTE_MS]
        self.assertEqual(fit_thermal_model.hold(series, grid, HOUR_MS), ["1", "1", "2", "2"])

    def test_gives_up_rather_than_holding_a_stale_value(self):
        # A sensor that stopped reporting must leave a gap. Held forward it would
        # read as a room that stopped changing, which is thermal mass to the fit.
        series = [(EPOCH_MS, "1")]
        grid = [EPOCH_MS, EPOCH_MS + 2 * HOUR_MS]
        self.assertEqual(fit_thermal_model.hold(series, grid, HOUR_MS), ["1", None])

    def test_has_no_value_before_the_first_sample(self):
        series = [(EPOCH_MS, "1")]
        self.assertEqual(fit_thermal_model.hold(series, [EPOCH_MS - MINUTE_MS], HOUR_MS), [None])


class ChangedWithinTest(unittest.TestCase):

    def test_a_change_inside_the_step_is_seen(self):
        series = [(EPOCH_MS + MINUTE_MS, "ON")]
        self.assertTrue(fit_thermal_model.changed_within(series, EPOCH_MS, EPOCH_MS + 10 * MINUTE_MS))

    def test_a_change_before_the_step_is_not(self):
        series = [(EPOCH_MS, "ON")]
        self.assertFalse(fit_thermal_model.changed_within(series, EPOCH_MS, EPOCH_MS + 10 * MINUTE_MS))


class NumberTest(unittest.TestCase):

    def test_drops_the_unit_of_a_quantity_state(self):
        self.assertEqual(fit_thermal_model.number("86.5 °C"), 86.5)

    def test_reads_a_bare_number(self):
        self.assertEqual(fit_thermal_model.number("3.5"), 3.5)

    def test_absent_and_undefined_states_are_none(self):
        self.assertIsNone(fit_thermal_model.number(None))
        self.assertIsNone(fit_thermal_model.number("NULL"))
        self.assertIsNone(fit_thermal_model.number("UNDEF"))
        self.assertIsNone(fit_thermal_model.number("ON"))


class LeastSquaresTest(unittest.TestCase):

    def test_recovers_the_coefficients_it_was_built_from(self):
        rows = [([float(x), float(x * x), 1.0], 2.0 * x + 3.0 * x * x + 4.0) for x in range(20)]
        fit = fit_thermal_model.least_squares(rows, minimum=1)
        self.assertTrue(fit.ok)
        for actual, expected in zip(fit.coefficients, [2.0, 3.0, 4.0]):
            self.assertAlmostEqual(actual, expected, places=6)
        self.assertAlmostEqual(fit.r_squared, 1.0, places=9)

    def test_refuses_rather_than_fitting_too_little(self):
        fit = fit_thermal_model.least_squares([([1.0, 1.0], 1.0)], minimum=10)
        self.assertFalse(fit.ok)
        self.assertIn("samples", fit.refused)

    def test_refuses_collinear_regressors(self):
        rows = [([float(x), 2.0 * x], float(x)) for x in range(20)]
        self.assertFalse(fit_thermal_model.least_squares(rows, minimum=1).ok)


class SyntheticFitTest(unittest.TestCase):

    def setUp(self):
        self.step_h = fit_thermal_model.STEP_MINUTES / 60.0
        self.synthetic = SyntheticRoom(self.step_h)

    def collect(self, **kwargs):
        return fit_thermal_model.collect(
            room(window="window" in kwargs), self.synthetic.grid, self.synthetic.all_series(**kwargs), self.step_h
        )

    def test_recovers_tau_solar_and_gains_with_the_panel_off(self):
        samples = [sample for sample in self.collect() if not sample.heater_on]
        fit = fit_thermal_model.fit_slice(samples, fit_thermal_model.MIN_SAMPLES_OFF)
        self.assertTrue(fit.ok, fit.refused)
        self.assertAlmostEqual(fit_thermal_model.tau_of(fit), TRUE_TAU_H, delta=TRUE_TAU_H * TOLERANCE)
        self.assertAlmostEqual(fit.coefficients[1], TRUE_SOLAR_K_PER_H_PER_WM2, delta=TOLERANCE)
        self.assertAlmostEqual(fit.coefficients[2], TRUE_BASE_K_PER_H, delta=TOLERANCE)

    def test_recovers_what_the_panel_adds(self):
        samples = self.collect()
        off = fit_thermal_model.fit_slice(
            [sample for sample in samples if not sample.heater_on], fit_thermal_model.MIN_SAMPLES_OFF
        )
        on = fit_thermal_model.fit_slice(
            [sample for sample in samples if sample.heater_on], fit_thermal_model.MIN_SAMPLES_ON
        )
        self.assertTrue(on.ok, on.refused)
        self.assertAlmostEqual(on.coefficients[2] - off.coefficients[2], TRUE_HEATER_K_PER_H, delta=TOLERANCE)
        # What the panel can hold above outdoor, internal gains included.
        expected_hold = (TRUE_HEATER_K_PER_H + TRUE_BASE_K_PER_H) * TRUE_TAU_H
        self.assertAlmostEqual(on.coefficients[2] / on.coefficients[0], expected_hold, delta=expected_hold * TOLERANCE)

    def test_the_step_the_panel_switched_in_belongs_to_neither_slice(self):
        # Holding the endpoints is not enough: that step is part heated.
        total = len(self.collect())
        self.assertEqual(total, OFF_STEPS + ON_STEPS - 1)

    def test_every_step_is_dropped_while_the_circuit_pump_runs(self):
        # The boiler heats the same rooms. Left in, these steps mix two heat
        # sources into one coefficient and the fit looks fine while being wrong.
        self.assertEqual(self.collect(pump="ON"), [])

    def test_an_unknown_pump_state_is_not_taken_for_off(self):
        series = self.synthetic.all_series()
        series[fit_thermal_model.CIRCUIT_PUMP_ITEM] = []
        self.assertEqual(
            fit_thermal_model.collect(room(), self.synthetic.grid, series, self.step_h), []
        )

    def test_a_persisted_null_is_not_taken_for_off(self):
        # An item whose thing is offline persists the string NULL. Read as "not
        # ON" it becomes an OFF that never happened.
        self.assertEqual(self.collect(pump="NULL"), [])
        self.assertEqual(self.collect(pump="UNDEF"), [])

    def test_a_switch_that_has_not_changed_in_months_still_counts(self):
        # The pump does not change from May to October and a window can stay shut
        # for weeks. Expiring that hold drops every sample taken since.
        self.assertEqual(len(self.collect()), OFF_STEPS + ON_STEPS - 1)

    def test_every_step_is_dropped_while_a_window_is_open(self):
        self.assertEqual(self.collect(window=fit_thermal_model.WINDOW_OPEN_STATE), [])

    def test_a_closed_window_does_not_drop_anything(self):
        self.assertEqual(len(self.collect(window="OFF")), OFF_STEPS + ON_STEPS - 1)

    def test_a_sensor_glitch_is_dropped_rather_than_fitted(self):
        series = self.synthetic.all_series()
        spike = self.synthetic.indoor[:]
        spike[10] = START_INDOOR_C + 40.0
        series["room_temp"] = self.synthetic.series(spike)
        # The spike ruins the step into it and the step out of it, and nothing else.
        self.assertEqual(
            len(fit_thermal_model.collect(room(), self.synthetic.grid, series, self.step_h)),
            OFF_STEPS + ON_STEPS - 1 - 2,
        )

    def test_a_room_that_never_moved_is_refused_not_fitted(self):
        flat = [
            fit_thermal_model.Sample(0.0, -15.0, 0.0, WIND_MEAN_M_PER_S, False)
            for _ in range(fit_thermal_model.MIN_SAMPLES_OFF + 1)
        ]
        fit = fit_thermal_model.fit_slice(flat, fit_thermal_model.MIN_SAMPLES_OFF)
        self.assertFalse(fit.ok)


if __name__ == "__main__":
    unittest.main(verbosity=2)
