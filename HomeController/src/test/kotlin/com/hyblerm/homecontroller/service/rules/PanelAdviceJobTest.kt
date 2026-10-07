package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.config.ConfigurationProperties.PanelAdviceJobConfig
import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.ColdRoom
import com.hyblerm.homecontroller.service.rules.common.job.HouseDemand
import com.hyblerm.homecontroller.service.rules.common.job.PanelAdviceJob
import com.hyblerm.homecontroller.service.util.Time
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

private const val STATUS_ID = "heating_advice_control"
private const val PANEL_ID = "Infrared_heating_panel_switch"
private const val TEMPERATURE_ID = "pracovna_teplota_temp"
private const val MIN_ID = "room_min_pracovna"
private const val MAX_ID = "room_max_pracovna"
private const val WINDOW_ID = "pracovna_okno_OnOff_Switch"
private const val PUMP_ID = "Atmos_C1_Pump"
private const val CHARGE_ID = "Atmos_Buffer_Charge"
private const val ADVICE_ID = "panel_advice_pracovna"
private const val ALERT_ID = "panel_advice_pracovna_alert"

// The work room's band, as it is actually set: 18 to 22.
private const val BAND_MIN = "18.0"
private const val BAND_MAX = "22.0"

// Number:Temperature states arrive from the REST API with their unit attached.
private const val BELOW_BAND = "16.4 °C"
private const val INSIDE_BAND = "20.0 °C"
private const val ABOVE_BAND = "23.1 °C"

private const val TANK_COVERS_ABOVE_PERCENT = 40.0
private const val FLAT_TANK = "10"
private const val CHARGED_TANK = "55"
private const val ROOM_NAME = "pracovna"
private const val MINIMUM_RUN_MINUTES = 45
private const val PLENTY_OF_THE_DAY_LEFT = 300
private const val ADVISE_FROM_HOUR = 6
private const val ADVISE_TO_HOUR = 22

private val ZONE: ZoneId = ZoneId.of("Europe/Prague")
private val DAYTIME: LocalDateTime = LocalDateTime.of(2026, 1, 15, 14, 0)
private val NIGHT: LocalDateTime = LocalDateTime.of(2026, 1, 15, 3, 0)

class PanelAdviceJobTest {

    private val dataAccess = mock(DataAccess::class.java)
    private val time = mock(Time::class.java)

    @BeforeEach
    fun `a cold room, a flat tank, a closed window and the panel off`() {
        mockItem(STATUS_ID, "ON")
        mockItem(PANEL_ID, "OFF")
        mockItem(TEMPERATURE_ID, BELOW_BAND)
        mockItem(MIN_ID, BAND_MIN)
        mockItem(MAX_ID, BAND_MAX)
        mockItem(WINDOW_ID, "OFF")
        mockItem(PUMP_ID, "OFF")
        mockItem(CHARGE_ID, FLAT_TANK)
        mockItem(ADVICE_ID, "NULL")
        mockItem(ALERT_ID, "OFF")
        at(DAYTIME)
    }

    @Test
    fun `checkAdvice does nothing at all when the advisory is switched off`() {
        mockItem(STATUS_ID, "OFF")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess, never()).commandItem(any(), any())
    }

    @Test
    fun `checkAdvice advises the panel on when this room alone is below its band`() {
        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "ON")
        verify(dataAccess).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice never commands the panel itself`() {
        // The point of the job for now: it says what it would do and leaves the
        // switch to a person.
        job().checkAdvice(oneColdRoom())

        verify(dataAccess, never()).commandItem(PANEL_ID, "ON")
        verify(dataAccess, never()).commandItem(PANEL_ID, "OFF")
    }

    @Test
    fun `checkAdvice hands a cold house to the boiler instead of to the panel`() {
        // Three rooms on electricity cost more than a fire, and one message about
        // the house is better than one per room.
        job().checkAdvice(coldHouse())

        verify(dataAccess).commandItem(ADVICE_ID, "BOILER")
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice leaves a panel that is already running alone while the house waits for a fire`() {
        // It is doing no harm, and nagging about it would cost more trust than it saves.
        mockItem(PANEL_ID, "ON")

        job().checkAdvice(coldHouse())

        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice advises the panel off in a room nobody is in`() {
        job().checkAdvice(oneColdRoom(minutesLeft = 0))

        verify(dataAccess).commandItem(ADVICE_ID, "OFF")
    }

    @Test
    fun `checkAdvice will not pay to heat a room that is about to empty`() {
        // Ten minutes of use left buys nothing. A late fire is different -- it charges the
        // tank for tomorrow -- which is why no such rule applies to the boiler.
        job().checkAdvice(oneColdRoom(minutesLeft = 10))

        verify(dataAccess).commandItem(ADVICE_ID, "OFF")
    }

    @Test
    fun `checkAdvice heats a cold room that is still in use for a while`() {
        job().checkAdvice(oneColdRoom(minutesLeft = MINIMUM_RUN_MINUTES + 1))

        verify(dataAccess).commandItem(ADVICE_ID, "ON")
    }

    @Test
    fun `checkAdvice decides alone when the house verdict says nothing about its room`() {
        // An unconfigured or switched-off house demand must not mute the panels.
        job().checkAdvice(HouseDemand.unknown())

        verify(dataAccess).commandItem(ADVICE_ID, "ON")
    }

    @Test
    fun `checkAdvice advises the panel off while the boiler circuit is delivering`() {
        // Wood is cheaper than electricity, so a room the boiler is already heating
        // is never a room to put a panel into.
        mockItem(PUMP_ID, "ON")
        mockItem(PANEL_ID, "ON")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "OFF")
        verify(dataAccess).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice advises the panel off while the tank still holds heat`() {
        // The pump cycles, so an off pump on its own does not mean the boiler is
        // done with the room -- a charged tank does.
        mockItem(CHARGE_ID, CHARGED_TANK)

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "OFF")
    }

    @Test
    fun `checkAdvice advises the panel off with a window open`() {
        mockItem(WINDOW_ID, "ON")
        mockItem(PANEL_ID, "ON")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "OFF")
        verify(dataAccess).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice advises the panel off once the room is above its band`() {
        mockItem(TEMPERATURE_ID, ABOVE_BAND)
        mockItem(PANEL_ID, "ON")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "OFF")
        verify(dataAccess).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice says nothing while the room sits inside its band`() {
        // Between the two setpoints there is nothing to do, which is what keeps the
        // advice from flipping either side of one number.
        mockItem(TEMPERATURE_ID, INSIDE_BAND)

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "NONE")
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice holds when the room temperature cannot be read`() {
        mockItem(TEMPERATURE_ID, "NULL")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "UNKNOWN")
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice holds when the band has not been set`() {
        // A virtual setpoint reads NULL until persistence restores it.
        mockItem(MIN_ID, "NULL")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "UNKNOWN")
    }

    @Test
    fun `checkAdvice holds when the top of the band has not been set`() {
        mockItem(MAX_ID, "NULL")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "UNKNOWN")
    }

    @Test
    fun `checkAdvice holds when the tank charge cannot be read`() {
        // Not knowing whether the boiler covers the room is not knowing it does not.
        mockItem(CHARGE_ID, "NULL")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "UNKNOWN")
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice holds when the circuit pump cannot be read`() {
        mockItem(PUMP_ID, "NULL")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "UNKNOWN")
    }

    @Test
    fun `checkAdvice holds when the window contact cannot be read`() {
        // A dead contact battery says nothing about the window, and heating into an
        // open one is the mistake worth avoiding.
        mockItem(WINDOW_ID, "NULL")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "UNKNOWN")
    }

    @Test
    fun `checkAdvice says nothing when the panel's own state cannot be read`() {
        // The study panel is a Tuya device whose local protocol drops out -- the log
        // is full of decode failures -- and then its item reads NULL. isOn() reports
        // that as off, so without this the advisory would tell somebody to switch on
        // a panel that may well already be running.
        mockItem(PANEL_ID, "NULL")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "ON")
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice keeps the advice but holds the alert outside the advice hours`() {
        at(NIGHT)

        job().checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "ON")
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice raises nothing while the panel already matches the advice`() {
        mockItem(PANEL_ID, "ON")
        mockItem(ADVICE_ID, "ON")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `checkAdvice commands nothing when nothing has changed`() {
        mockItem(ADVICE_ID, "ON")
        mockItem(ALERT_ID, "ON")

        job().checkAdvice(oneColdRoom())

        verify(dataAccess, never()).commandItem(any(), any())
    }

    @Test
    fun `checkAdvice works in a room with no window contact`() {
        val config = config()
        config.windowItem = ""

        PanelAdviceJob(dataAccess, config, time).checkAdvice(oneColdRoom())

        verify(dataAccess).commandItem(ADVICE_ID, "ON")
    }

    private fun job(): PanelAdviceJob = PanelAdviceJob(dataAccess, config(), time)

    private fun oneColdRoom(minutesLeft: Int = PLENTY_OF_THE_DAY_LEFT): HouseDemand =
        HouseDemand(
            listOf(ColdRoom(ROOM_NAME, 1.6, true)),
            emptyList(),
            mapOf(ROOM_NAME to minutesLeft),
            wholeHouse = false
        )

    private fun coldHouse(): HouseDemand =
        HouseDemand(
            listOf(ColdRoom(ROOM_NAME, 1.6, true), ColdRoom("obyvak", 2.1, true)),
            emptyList(),
            mapOf(ROOM_NAME to PLENTY_OF_THE_DAY_LEFT, "obyvak" to PLENTY_OF_THE_DAY_LEFT),
            wholeHouse = true
        )

    private fun at(moment: LocalDateTime) {
        whenever(time.clock()).thenReturn(Clock.fixed(moment.atZone(ZONE).toInstant(), ZONE))
    }

    private fun mockItem(name: String, state: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, state))
    }

    private fun config(): PanelAdviceJobConfig {
        val config = PanelAdviceJobConfig()
        config.statusItem = STATUS_ID
        config.roomName = ROOM_NAME
        config.minimumRunMinutes = MINIMUM_RUN_MINUTES
        config.switchItem = PANEL_ID
        config.temperatureItem = TEMPERATURE_ID
        config.minItem = MIN_ID
        config.maxItem = MAX_ID
        config.windowItem = WINDOW_ID
        config.circuitPumpItem = PUMP_ID
        config.chargeItem = CHARGE_ID
        config.tankCoversAbovePercent = TANK_COVERS_ABOVE_PERCENT
        config.adviceItem = ADVICE_ID
        config.alertItem = ALERT_ID
        config.adviseFromHour = ADVISE_FROM_HOUR
        config.adviseToHour = ADVISE_TO_HOUR
        return config
    }
}
