package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.config.ConfigurationProperties.BoilerAdviceJobConfig
import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.BoilerAdviceJob
import com.hyblerm.homecontroller.service.util.Time
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

private const val STATUS_ID = "heating_advice_control"
private const val CHARGE_ID = "Atmos_Buffer_Charge"
private const val MIN_TOMORROW_ID = "weather_temp_min_tomorrow"
private const val MAX_TOMORROW_ID = "weather_temp_max_tomorrow"
private const val SOLAR_TOMORROW_ID = "weather_solar_sum_tomorrow"
private const val UPDATED_ID = "weather_updated"
private const val ADVICE_ID = "boiler_advice"
private const val NEEDED_ID = "boiler_advice_needed"
private const val SHORTFALL_ID = "boiler_advice_shortfall"
private const val ALERT_ID = "boiler_advice_alert"

// 18 C base, a day averaging 3 C: 15 degree-days. A day of heating costs the baseline
// 20 % whatever the weather, and 4 % more per degree-day, so 20 + 15 * 4 = 80 %.
private const val BASE_TEMPERATURE = 18.0
private const val BASELINE_PERCENT_PER_DAY = 20.0
private const val CHARGE_PER_DEGREE_DAY = 4.0
private const val SOLAR_CREDIT_PER_MEGAJOULE = 1.5
private const val STALE_AFTER_HOURS = 3L
private const val ADVISE_FROM_HOUR = 16
private const val ADVISE_TO_HOUR = 22

private const val COLD_TOMORROW_MIN = "0.0"
private const val COLD_TOMORROW_MAX = "6.0"
private const val NEEDED_FOR_COLD_TOMORROW = "80.0"

private val ZONE: ZoneId = ZoneId.of("Europe/Prague")
private val EVENING: LocalDateTime = LocalDateTime.of(2026, 1, 15, 18, 30)
private val MORNING: LocalDateTime = LocalDateTime.of(2026, 1, 15, 9, 0)

class BoilerAdviceJobTest {

    private val dataAccess = mock(DataAccess::class.java)
    private val time = mock(Time::class.java)

    @BeforeEach
    fun `a cold tomorrow, a fresh forecast, and the evening`() {
        mockItem(STATUS_ID, "ON")
        mockItem(MIN_TOMORROW_ID, COLD_TOMORROW_MIN)
        mockItem(MAX_TOMORROW_ID, COLD_TOMORROW_MAX)
        mockItem(SOLAR_TOMORROW_ID, "0.0")
        forecastAgeHours(0)
        at(EVENING)
        // Outputs start out unset, so every test sees the job write them.
        mockItem(ADVICE_ID, "NULL")
        mockItem(NEEDED_ID, "NULL")
        mockItem(SHORTFALL_ID, "NULL")
        mockItem(ALERT_ID, "OFF")
    }

    @Test
    fun `checkAdvice does nothing at all when the advisory is switched off`() {
        mockItem(STATUS_ID, "OFF")

        job().checkAdvice()

        verify(dataAccess, never()).commandItem(any(), any())
    }

    @Test
    fun `checkAdvice says fire the boiler when the tank will not cover tomorrow`() {
        mockItem(CHARGE_ID, "35")

        job().checkAdvice()

        verify(dataAccess).commandItem(ADVICE_ID, "FIRE")
        verify(dataAccess).commandItem(NEEDED_ID, NEEDED_FOR_COLD_TOMORROW)
        verify(dataAccess).commandItem(SHORTFALL_ID, "45.0")
        verify(dataAccess).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice says the tank is enough when it covers tomorrow`() {
        mockItem(CHARGE_ID, "85")

        job().checkAdvice()

        verify(dataAccess).commandItem(ADVICE_ID, "ENOUGH")
        verify(dataAccess).commandItem(SHORTFALL_ID, "-5.0")
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice asks for nothing when tomorrow needs no heating, not even the baseline`() {
        // A day averaging the base temperature has no degree-days in it. The circuit is
        // off then and the tank drains nothing, so the baseline must not be charged --
        // applied regardless it would ask for wood in July.
        mockItem(MIN_TOMORROW_ID, "14.0")
        mockItem(MAX_TOMORROW_ID, "22.0")
        mockItem(CHARGE_ID, "0")

        job().checkAdvice()

        verify(dataAccess).commandItem(NEEDED_ID, "0.0")
        verify(dataAccess).commandItem(ADVICE_ID, "ENOUGH")
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice takes tomorrow's sun off what the tank has to hold`() {
        // 80 % less 10 MJ at 1.5 % each is 65 %, which 70 % in the tank covers --
        // without the credit the same day would have asked for a fire.
        mockItem(SOLAR_TOMORROW_ID, "10.0")
        mockItem(CHARGE_ID, "70")

        job().checkAdvice()

        verify(dataAccess).commandItem(NEEDED_ID, "65.0")
        verify(dataAccess).commandItem(ADVICE_ID, "ENOUGH")
    }

    @Test
    fun `checkAdvice advises without the sun credit when tomorrow's sun is unknown`() {
        // Tomorrow's sun is the one input allowed to be missing: the day is then
        // costed as if it were cloudy, and a tank fuller than it had to be is the
        // cheaper mistake than a cold house.
        mockItem(SOLAR_TOMORROW_ID, "NULL")
        mockItem(CHARGE_ID, "50")

        job().checkAdvice()

        verify(dataAccess).commandItem(NEEDED_ID, NEEDED_FOR_COLD_TOMORROW)
        verify(dataAccess).commandItem(ADVICE_ID, "FIRE")
    }

    @Test
    fun `checkAdvice charges a mild day the baseline, not almost nothing`() {
        // The bug this replaced: with demand linear through zero, a day one degree-day
        // under the base read as 4 % of the tank. Measured, such a day costs about 35 --
        // the circuit still runs its programme. Nearly all the demand is the baseline.
        mockItem(MIN_TOMORROW_ID, "16.0")
        mockItem(MAX_TOMORROW_ID, "18.0")
        mockItem(CHARGE_ID, "11")

        job().checkAdvice()

        verify(dataAccess).commandItem(NEEDED_ID, "24.0")
        verify(dataAccess).commandItem(ADVICE_ID, "FIRE")
    }

    @Test
    fun `checkAdvice never asks for more than a full tank`() {
        mockItem(MIN_TOMORROW_ID, "-20.0")
        mockItem(MAX_TOMORROW_ID, "-10.0")
        mockItem(CHARGE_ID, "10")

        job().checkAdvice()

        verify(dataAccess).commandItem(NEEDED_ID, "100.0")
    }

    @Test
    fun `checkAdvice holds rather than guessing when the forecast has no value`() {
        mockItem(CHARGE_ID, "35")
        mockItem(MIN_TOMORROW_ID, "NULL")

        job().checkAdvice()

        verify(dataAccess).commandItem(ADVICE_ID, "UNKNOWN")
        verify(dataAccess, never()).commandItem(eq(SHORTFALL_ID), any())
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice holds when the tank charge cannot be read`() {
        // Atmos_Buffer_Charge is NULL until all four probes have reported, and
        // while the gateway is down.
        mockItem(CHARGE_ID, "NULL")

        job().checkAdvice()

        verify(dataAccess).commandItem(ADVICE_ID, "UNKNOWN")
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice reads the timestamp the HTTP binding actually writes`() {
        // Captured off the live instance. Two things about it would break a stricter
        // parser: the offset has no colon, which ISO_OFFSET_DATE_TIME refuses, and the
        // fraction is nanoseconds where Atmos_Clock writes milliseconds. Getting this
        // wrong does not fail loudly -- every forecast reads as undatable, so the
        // advice is UNKNOWN for ever and the house is simply never told anything.
        mockItem(CHARGE_ID, "35")
        mockItem(UPDATED_ID, "2026-01-15T18:29:51.445982170+0100")

        job().checkAdvice()

        verify(dataAccess).commandItem(ADVICE_ID, "FIRE")
    }

    @Test
    fun `checkAdvice will not advise from a forecast it cannot date`() {
        // A forecast that stopped arriving looks exactly like a current one: the
        // last numbers just stand in the items.
        mockItem(CHARGE_ID, "35")
        forecastAgeHours(STALE_AFTER_HOURS + 1)

        job().checkAdvice()

        verify(dataAccess).commandItem(ADVICE_ID, "UNKNOWN")
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice keeps the advice but holds the alert outside the advice hours`() {
        // The number is true all day; being told about it at nine in the morning
        // is not useful, because the fire happens in the evening.
        mockItem(CHARGE_ID, "35")
        at(MORNING)

        job().checkAdvice()

        verify(dataAccess).commandItem(ADVICE_ID, "FIRE")
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice commands nothing when nothing has changed`() {
        mockItem(CHARGE_ID, "35")
        mockItem(ADVICE_ID, "FIRE")
        mockItem(NEEDED_ID, NEEDED_FOR_COLD_TOMORROW)
        mockItem(SHORTFALL_ID, "45.0")
        mockItem(ALERT_ID, "ON")

        job().checkAdvice()

        verify(dataAccess, never()).commandItem(any(), any())
    }

    @Test
    fun `checkAdvice withdraws the alert once the tank covers tomorrow again`() {
        mockItem(CHARGE_ID, "85")
        mockItem(ALERT_ID, "ON")

        job().checkAdvice()

        verify(dataAccess).commandItem(ALERT_ID, "OFF")
    }

    private fun job(): BoilerAdviceJob = BoilerAdviceJob(dataAccess, config(), time)

    private fun at(moment: LocalDateTime) {
        whenever(time.clock()).thenReturn(Clock.fixed(moment.atZone(ZONE).toInstant(), ZONE))
    }

    private fun forecastAgeHours(hours: Long) {
        // openHAB renders a DateTime state with the offset and no colon in it.
        val stamp = EVENING.minusHours(hours).atZone(ZONE)
        mockItem(UPDATED_ID, stamp.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")))
    }

    private fun mockItem(name: String, state: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, state))
    }

    private fun config(): BoilerAdviceJobConfig {
        val config = BoilerAdviceJobConfig()
        config.statusItem = STATUS_ID
        config.chargeItem = CHARGE_ID
        config.minTomorrowItem = MIN_TOMORROW_ID
        config.maxTomorrowItem = MAX_TOMORROW_ID
        config.solarTomorrowItem = SOLAR_TOMORROW_ID
        config.forecastUpdatedItem = UPDATED_ID
        config.adviceItem = ADVICE_ID
        config.neededItem = NEEDED_ID
        config.shortfallItem = SHORTFALL_ID
        config.alertItem = ALERT_ID
        config.baseTemperatureCelsius = BASE_TEMPERATURE
        config.baselinePercentPerDay = BASELINE_PERCENT_PER_DAY
        config.chargePercentPerDegreeDay = CHARGE_PER_DEGREE_DAY
        config.chargePercentPerMegajoule = SOLAR_CREDIT_PER_MEGAJOULE
        config.staleForecastHours = STALE_AFTER_HOURS
        config.adviseFromHour = ADVISE_FROM_HOUR
        config.adviseToHour = ADVISE_TO_HOUR
        return config
    }
}
