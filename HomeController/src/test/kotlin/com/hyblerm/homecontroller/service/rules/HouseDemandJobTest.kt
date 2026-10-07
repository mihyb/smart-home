package com.hyblerm.homecontroller.service.rules

import com.hyblerm.homecontroller.config.ConfigurationProperties.DayMode
import com.hyblerm.homecontroller.config.ConfigurationProperties.HouseDemandJobConfig
import com.hyblerm.homecontroller.config.ConfigurationProperties.OffWindow
import com.hyblerm.homecontroller.config.ConfigurationProperties.RoomConfig
import com.hyblerm.homecontroller.repository.entity.OpenHabModel
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.job.HouseDemandJob
import com.hyblerm.homecontroller.service.util.Time
import org.assertj.core.api.Assertions.assertThat
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
private const val CHARGE_ID = "Atmos_Buffer_Charge"
private const val BURNING_ID = "Atmos_Exhaust_Fan"
private const val COLD_ROOMS_ID = "heating_cold_rooms"
private const val COLDEST_ROOM_ID = "heating_coldest_room"
private const val COLDEST_GAP_ID = "heating_coldest_gap"
private const val ALERT_ID = "heating_house_alert"

// The three rooms that are setpoints rather than only monitored, with the bands
// they are actually run at.
private const val LIVING_TEMPERATURE_ID = "obyvak_temp_temperature"
private const val LIVING_MIN_ID = "room_min_obyvak"
private const val LIVING_HEATER_ID = "obyvak_topeni_switch"
private const val LIVING_MIN = "20.0"

private const val KIDS_TEMPERATURE_ID = "pokojicek_temp_temperature"
private const val KIDS_MIN_ID = "room_min_pokojicek"
private const val KIDS_WINDOW_ID = "pokoj_okno_pokoj_okno_switch"
private const val KIDS_MIN = "19.0"

private const val WORK_TEMPERATURE_ID = "pracovna_teplota_temp"
private const val WORK_MIN_ID = "room_min_pracovna"
private const val WORK_HEATER_ID = "Infrared_heating_panel_switch"
private const val WORK_WINDOW_ID = "pracovna_okno_OnOff_Switch"
private const val WORK_MIN = "18.0"

private const val LIVING_ROOM_NAME = "obyvak"
private const val KIDS_ROOM_NAME = "pokojicek"
private const val WORK_ROOM_NAME = "pracovna"

private const val TOLERANCE_KELVIN = 0.3
private const val FIRE_ABOVE_COLD_ROOMS = 2
private const val TANK_COVERS_ABOVE_PERCENT = 40.0

private const val FLAT_TANK = "10"
private const val CHARGED_TANK = "55"

private const val LIVING_WARM = "21.0 °C"
private const val LIVING_COLD = "18.5 °C"
private const val KIDS_WARM = "20.2 °C"
private const val KIDS_COLD = "17.0 °C"
private const val WORK_WARM = "19.4 °C"
private const val WORK_COLD = "16.4 °C"

private val ZONE: ZoneId = ZoneId.of("Europe/Prague")

// 15 January 2026 is a Thursday, 17 January a Saturday.
private val WEEKDAY_MIDDAY: LocalDateTime = LocalDateTime.of(2026, 1, 15, 12, 0)
private val WEEKDAY_EVENING: LocalDateTime = LocalDateTime.of(2026, 1, 15, 20, 0)
private val WEEKEND_MIDDAY: LocalDateTime = LocalDateTime.of(2026, 1, 17, 12, 0)

class HouseDemandJobTest {

    private val dataAccess = mock(DataAccess::class.java)
    private val time = mock(Time::class.java)

    @BeforeEach
    fun `every room inside its band, a flat tank and no fire`() {
        mockItem(STATUS_ID, "ON")
        mockItem(CHARGE_ID, FLAT_TANK)
        mockItem(BURNING_ID, "OFF")
        mockItem(LIVING_TEMPERATURE_ID, LIVING_WARM)
        mockItem(LIVING_MIN_ID, LIVING_MIN)
        mockItem(KIDS_TEMPERATURE_ID, KIDS_WARM)
        mockItem(KIDS_MIN_ID, KIDS_MIN)
        mockItem(KIDS_WINDOW_ID, "OFF")
        mockItem(WORK_TEMPERATURE_ID, WORK_WARM)
        mockItem(WORK_MIN_ID, WORK_MIN)
        mockItem(WORK_WINDOW_ID, "OFF")
        mockItem(COLD_ROOMS_ID, "0")
        mockItem(COLDEST_ROOM_ID, "-")
        mockItem(COLDEST_GAP_ID, "0.0")
        mockItem(ALERT_ID, "OFF")
        at(WEEKDAY_MIDDAY)
    }

    @Test
    fun `assess claims nothing when no rooms are configured`() {
        val demand = HouseDemandJob(dataAccess, HouseDemandJobConfig(), time).assess()

        assertThat(demand.wholeHouse).isFalse()
        assertThat(demand.coldRooms).isEmpty()
        verify(dataAccess, never()).commandItem(any(), any())
    }

    @Test
    fun `assess claims nothing when the advisory is switched off`() {
        mockItem(STATUS_ID, "OFF")

        val demand = job().assess()

        assertThat(demand.wholeHouse).isFalse()
        verify(dataAccess, never()).commandItem(any(), any())
    }

    @Test
    fun `assess counts no cold rooms when the house is inside its bands`() {
        val demand = job().assess()

        assertThat(demand.coldRooms).isEmpty()
        assertThat(demand.wholeHouse).isFalse()
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `assess leaves one cold room with a panel of its own to that panel`() {
        mockItem(WORK_TEMPERATURE_ID, WORK_COLD)

        val demand = job().assess()

        assertThat(demand.coldRooms.map { it.name }).containsExactly(WORK_ROOM_NAME)
        assertThat(demand.wholeHouse).isFalse()
        verify(dataAccess).commandItem(COLD_ROOMS_ID, "1")
        verify(dataAccess).commandItem(COLDEST_ROOM_ID, WORK_ROOM_NAME)
        verify(dataAccess).commandItem(COLDEST_GAP_ID, "1.6")
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `assess makes two cold rooms the boiler's problem`() {
        mockItem(WORK_TEMPERATURE_ID, WORK_COLD)
        mockItem(LIVING_TEMPERATURE_ID, LIVING_COLD)

        val demand = job().assess()

        assertThat(demand.wholeHouse).isTrue()
        assertThat(demand.coldest?.name).isEqualTo(WORK_ROOM_NAME)
        verify(dataAccess).commandItem(COLD_ROOMS_ID, "2")
        verify(dataAccess).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `assess makes one cold room with no panel the boiler's problem on its own`() {
        // The kids' room has a dehumidifier and no heating of its own, so there is
        // nothing local that could answer for it.
        mockItem(KIDS_TEMPERATURE_ID, KIDS_COLD)

        val demand = job().assess()

        assertThat(demand.coldRooms.map { it.name }).containsExactly(KIDS_ROOM_NAME)
        assertThat(demand.wholeHouse).isTrue()
        verify(dataAccess).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `assess gives no vote to a room whose band has never been set`() {
        // How the rooms that are only monitored stay out of it without being a
        // special case: nobody has said what they should be.
        mockItem(WORK_TEMPERATURE_ID, WORK_COLD)
        mockItem(WORK_MIN_ID, "NULL")

        val demand = job().assess()

        assertThat(demand.coldRooms).isEmpty()
        assertThat(demand.unreadableRooms).containsExactly(WORK_ROOM_NAME)
    }

    @Test
    fun `assess gives no vote to a room that is airing`() {
        // A room with a window open is cold for a reason, and it is not a reason to
        // light a boiler.
        mockItem(KIDS_TEMPERATURE_ID, KIDS_COLD)
        mockItem(KIDS_WINDOW_ID, "ON")

        val demand = job().assess()

        assertThat(demand.coldRooms).isEmpty()
        assertThat(demand.wholeHouse).isFalse()
    }

    @Test
    fun `assess gives no vote to a room whose sensor has dropped out`() {
        mockItem(KIDS_TEMPERATURE_ID, "NULL")

        val demand = job().assess()

        assertThat(demand.unreadableRooms).containsExactly(KIDS_ROOM_NAME)
        assertThat(demand.wholeHouse).isFalse()
    }

    @Test
    fun `assess does not count a room resting on its setpoint as cold`() {
        // Two tenths under is where a thermostat lives, not a cold room.
        mockItem(WORK_TEMPERATURE_ID, "17.8 °C")

        val demand = job().assess()

        assertThat(demand.coldRooms).isEmpty()
    }

    @Test
    fun `assess gives no vote to any room overnight`() {
        // The house is empty between 21 and 6 and nobody lights a boiler at three in the
        // morning. A house left to coast until six is the point of the setback.
        at(LocalDateTime.of(2026, 1, 15, 23, 0))
        mockItem(WORK_TEMPERATURE_ID, WORK_COLD)
        mockItem(LIVING_TEMPERATURE_ID, LIVING_COLD)

        val demand = job().assess()

        assertThat(demand.coldRooms).isEmpty()
        assertThat(demand.emptyRooms).contains(WORK_ROOM_NAME, LIVING_ROOM_NAME, KIDS_ROOM_NAME)
        assertThat(demand.wholeHouse).isFalse()
    }

    @Test
    fun `assess gives no vote to the study once it is empty for the evening`() {
        // Off from 15, so a cold study at 8 in the evening is nothing to act on.
        mockItem(WORK_TEMPERATURE_ID, WORK_COLD)
        at(WEEKDAY_EVENING)

        val demand = withOffWindows(WORK_ROOM_NAME, offFrom(15, 22, DayMode.ALL)).assess()

        assertThat(demand.coldRooms).isEmpty()
        assertThat(demand.emptyRooms).contains(WORK_ROOM_NAME)
    }

    @Test
    fun `assess counts the study while it is still in use`() {
        mockItem(WORK_TEMPERATURE_ID, WORK_COLD)
        at(WEEKDAY_MIDDAY)

        val demand = withOffWindows(WORK_ROOM_NAME, offFrom(15, 22, DayMode.ALL)).assess()

        assertThat(demand.coldRooms.map { it.name }).containsExactly(WORK_ROOM_NAME)
    }

    @Test
    fun `assess sends the kid to school on a weekday and not at the weekend`() {
        mockItem(KIDS_TEMPERATURE_ID, KIDS_COLD)
        val school = offFrom(7, 14, DayMode.WEEKDAY)

        at(WEEKDAY_MIDDAY)
        assertThat(withOffWindows(KIDS_ROOM_NAME, school).assess().emptyRooms).contains(KIDS_ROOM_NAME)

        at(WEEKEND_MIDDAY)
        assertThat(withOffWindows(KIDS_ROOM_NAME, school).assess().coldRooms.map { it.name })
            .containsExactly(KIDS_ROOM_NAME)
    }

    @Test
    fun `assess counts a room with no off window of its own, outside the night`() {
        // The living room has only the night rule, and a room nobody has written hours for
        // must ask for heat rather than quietly go without.
        at(WEEKDAY_MIDDAY)
        mockItem(LIVING_TEMPERATURE_ID, LIVING_COLD)

        assertThat(job().assess().coldRooms.map { it.name }).containsExactly(LIVING_ROOM_NAME)
    }

    @Test
    fun `assess reports how much of the room's day is left`() {
        // What stops a panel being switched on minutes before the room empties. Five past
        // two, off at three: fifty-five minutes, to the five minute step it walks in.
        at(LocalDateTime.of(2026, 1, 15, 14, 5))
        mockItem(WORK_TEMPERATURE_ID, WORK_COLD)

        val demand = withOffWindows(WORK_ROOM_NAME, offFrom(15, 22, DayMode.ALL)).assess()

        assertThat(demand.minutesLeft(WORK_ROOM_NAME)).isBetween(50, 60)
    }

    @Test
    fun `assess reports nothing left for a room that is already empty`() {
        at(WEEKDAY_EVENING)

        val demand = withOffWindows(WORK_ROOM_NAME, offFrom(15, 22, DayMode.ALL)).assess()

        assertThat(demand.minutesLeft(WORK_ROOM_NAME)).isZero()
    }

    @Test
    fun `assess does not ask for a fire while the tank still holds heat`() {
        // That heat is already on its way into the rooms.
        mockItem(WORK_TEMPERATURE_ID, WORK_COLD)
        mockItem(LIVING_TEMPERATURE_ID, LIVING_COLD)
        mockItem(CHARGE_ID, CHARGED_TANK)

        val demand = job().assess()

        assertThat(demand.wholeHouse).isTrue()
        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `assess does not ask for a fire that is already burning`() {
        // A boiler lit ten minutes ago has not charged the tank yet, and would
        // otherwise read as a house nobody has done anything about.
        mockItem(WORK_TEMPERATURE_ID, WORK_COLD)
        mockItem(LIVING_TEMPERATURE_ID, LIVING_COLD)
        mockItem(BURNING_ID, "ON")

        job().assess()

        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `assess does not ask for a fire when it cannot tell whether one is burning`() {
        mockItem(WORK_TEMPERATURE_ID, WORK_COLD)
        mockItem(LIVING_TEMPERATURE_ID, LIVING_COLD)
        mockItem(BURNING_ID, "NULL")

        job().assess()

        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `assess does not ask for a fire when the tank cannot be read`() {
        mockItem(WORK_TEMPERATURE_ID, WORK_COLD)
        mockItem(LIVING_TEMPERATURE_ID, LIVING_COLD)
        mockItem(CHARGE_ID, "NULL")

        job().assess()

        verify(dataAccess, never()).commandItem(ALERT_ID, "ON")
    }

    @Test
    fun `assess withdraws the alert once the house is back inside its bands`() {
        mockItem(ALERT_ID, "ON")

        job().assess()

        verify(dataAccess).commandItem(ALERT_ID, "OFF")
    }

    @Test
    fun `assess commands nothing when nothing has changed`() {
        mockItem(WORK_TEMPERATURE_ID, WORK_COLD)
        mockItem(COLD_ROOMS_ID, "1")
        mockItem(COLDEST_ROOM_ID, WORK_ROOM_NAME)
        mockItem(COLDEST_GAP_ID, "1.6")

        job().assess()

        verify(dataAccess, never()).commandItem(any(), any())
    }

    private fun job(): HouseDemandJob = HouseDemandJob(dataAccess, config(), time)

    private fun at(moment: LocalDateTime) {
        whenever(time.clock()).thenReturn(Clock.fixed(moment.atZone(ZONE).toInstant(), ZONE))
    }

    private fun mockItem(name: String, state: String) {
        whenever(dataAccess.getItem(name)).thenReturn(OpenHabModel.Item("link", name, state))
    }

    private fun offFrom(fromHour: Int, toHour: Int, mode: DayMode): OffWindow {
        val window = OffWindow()
        window.fromHour = fromHour
        window.toHour = toHour
        window.mode = mode
        return window
    }

    private fun withOffWindows(room: String, vararg windows: OffWindow): HouseDemandJob {
        val config = config()
        config.rooms = config.rooms.map { candidate ->
            if (candidate.name == room) candidate.also { it.offWindows = windows.toList() } else candidate
        }
        return HouseDemandJob(dataAccess, config, time)
    }

    private fun config(): HouseDemandJobConfig {
        val config = HouseDemandJobConfig()
        config.statusItem = STATUS_ID
        config.chargeItem = CHARGE_ID
        config.burningItem = BURNING_ID
        config.tankCoversAbovePercent = TANK_COVERS_ABOVE_PERCENT
        config.fireAboveColdRooms = FIRE_ABOVE_COLD_ROOMS
        config.toleranceKelvin = TOLERANCE_KELVIN
        config.coldRoomsItem = COLD_ROOMS_ID
        config.coldestRoomItem = COLDEST_ROOM_ID
        config.coldestGapItem = COLDEST_GAP_ID
        config.alertItem = ALERT_ID
        config.rooms = listOf(
            room(LIVING_ROOM_NAME, LIVING_TEMPERATURE_ID, LIVING_MIN_ID, LIVING_HEATER_ID, ""),
            room(KIDS_ROOM_NAME, KIDS_TEMPERATURE_ID, KIDS_MIN_ID, "", KIDS_WINDOW_ID),
            room(WORK_ROOM_NAME, WORK_TEMPERATURE_ID, WORK_MIN_ID, WORK_HEATER_ID, WORK_WINDOW_ID)
        )
        return config
    }

    private fun room(
        name: String,
        temperature: String,
        minimum: String,
        heater: String,
        window: String,
        offWindows: List<OffWindow> = emptyList()
    ): RoomConfig {
        val room = RoomConfig()
        room.name = name
        room.temperatureItem = temperature
        room.minItem = minimum
        room.localHeaterItem = heater
        room.windowItem = window
        room.offWindows = offWindows
        return room
    }
}
