package com.hyblerm.homecontroller.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.io.ClassPathResource

private const val APPLICATION_YAML = "application.yaml"
private const val PREFIX = "app"

private const val LIVING_ROOM = "obyvak"
private const val KIDS_ROOM = "pokojicek"
private const val WORK_ROOM = "pracovna"

private const val HOUSE_ALERT_ITEM = "heating_house_alert"
private const val BOILER_ALERT_ITEM = "boiler_advice_alert"
private const val ADVICE_ITEM = "boiler_advice"
private const val ADVICE_BLIND_ALARM = "advice-blind"

private const val EXPECTED_PANELS = 2
private const val NIGHT_FROM_HOUR = 21
private const val NIGHT_TO_HOUR = 6
private const val LEAST_DELAY_BEFORE_ASKING_FOR_A_FIRE = 30L
private const val LEAST_INTERVAL_BETWEEN_ASKING_AGAIN = 60L

/**
 * Binds the real application.yaml and checks the advisories came out of it wired to
 * each other.
 *
 * The failure this exists for is silent twice over. Spring's relaxed binding does not
 * complain about a key it does not recognise, so a misspelled `panelAdviceJobs` leaves
 * the list empty and the advisory simply never runs -- no error, no log line that
 * stands out. And nothing anywhere checks that the alarm table watches the same item a
 * job writes: `../scripts/check-item-contract.sh` proves both names exist in openHAB,
 * not that they are the same name. Either mistake gives a house that quietly never
 * tells you anything.
 */
class AdviceConfigurationTest {

    private val properties: ConfigurationProperties = bind()

    @Test
    fun `the three rooms with a comfort band are configured`() {
        assertThat(properties.houseDemand.rooms.map { room -> room.name })
            .containsExactly(LIVING_ROOM, KIDS_ROOM, WORK_ROOM)
        properties.houseDemand.rooms.forEach { room ->
            assertThat(room.temperatureItem).isNotBlank()
            assertThat(room.minItem).isNotBlank()
            assertThat(room.maxItem).isNotBlank()
        }
    }

    @Test
    fun `the kids room has no local heater, which is what makes it escalate alone`() {
        // It has a dehumidifier and no heating of its own, so the boiler is the only
        // thing that can answer for it. Give it a localHeaterItem and one cold kids'
        // room stops asking for a fire.
        val kids = properties.houseDemand.rooms.single { room -> room.name == KIDS_ROOM }
        assertThat(kids.localHeaterItem).isEmpty()

        val withPanels = properties.houseDemand.rooms.filter { room -> room.localHeaterItem.isNotEmpty() }
        assertThat(withPanels.map { room -> room.name }).containsExactly(LIVING_ROOM, WORK_ROOM)
    }

    @Test
    fun `every room with a panel advice job also votes in the house demand`() {
        // Otherwise a room could be advised a panel while taking no part in the
        // decision about whether the whole house needs a fire instead.
        val voting = properties.houseDemand.rooms.map { room -> room.temperatureItem }
        assertThat(properties.panelAdviceJobs).hasSize(EXPECTED_PANELS)
        properties.panelAdviceJobs.forEach { panel ->
            assertThat(voting).contains(panel.temperatureItem)
        }
    }

    @Test
    fun `a panel advice job shares the band with the room it advises`() {
        // One band per room, set in one place. Two copies would drift.
        properties.panelAdviceJobs.forEach { panel ->
            val room = properties.houseDemand.rooms.single { it.temperatureItem == panel.temperatureItem }
            assertThat(panel.minItem).isEqualTo(room.minItem)
            assertThat(panel.maxItem).isEqualTo(room.maxItem)
        }
    }

    @Test
    fun `a panel advice job names a room that exists`() {
        // The panel reads its room's occupancy out of the house verdict by this name. Get
        // it wrong and the room is simply never in the map, so the panel quietly falls
        // back to deciding alone and the off-hours stop applying to it.
        val names = properties.houseDemand.rooms.map { room -> room.name }
        properties.panelAdviceJobs.forEach { panel ->
            assertThat(panel.roomName).isIn(names)
            assertThat(panel.minimumRunMinutes).isGreaterThan(0)
        }
    }

    @Test
    fun `the house is off overnight and the school day is a weekday rule`() {
        assertThat(properties.houseDemand.nightFromHour).isEqualTo(NIGHT_FROM_HOUR)
        assertThat(properties.houseDemand.nightToHour).isEqualTo(NIGHT_TO_HOUR)

        val kids = properties.houseDemand.rooms.single { room -> room.name == KIDS_ROOM }
        assertThat(kids.offWindows).hasSize(1)
        assertThat(kids.offWindows.single().mode).isEqualTo(ConfigurationProperties.DayMode.WEEKDAY)

        // The living room is in use all day; only the night takes it out.
        val living = properties.houseDemand.rooms.single { room -> room.name == LIVING_ROOM }
        assertThat(living.offWindows).isEmpty()
    }

    @Test
    fun `every alert a job raises is watched by an alarm`() {
        val watched = properties.alarmJobs.flatMap { alarm -> alarm.conditions.map { it.item } }

        assertThat(watched).contains(properties.houseDemand.alertItem)
        properties.boilerAdviceJobs.forEach { boiler -> assertThat(watched).contains(boiler.alertItem) }
        properties.panelAdviceJobs.forEach { panel -> assertThat(watched).contains(panel.alertItem) }
    }

    @Test
    fun `the advisory going blind is itself watched`() {
        // Every one of these needs the forecast, and an advisory that has stopped
        // working looks exactly like a house that is fine.
        val blind = properties.alarmJobs.single { alarm -> alarm.id == ADVICE_BLIND_ALARM }
        assertThat(blind.conditions.map { it.item }).containsExactly(ADVICE_ITEM)
        assertThat(properties.boilerAdviceJobs.map { it.adviceItem }).contains(ADVICE_ITEM)
    }

    @Test
    fun `the alerts that ask for a fire are not eager`() {
        // A fire takes an hour to act on and hours to undo.
        val house = properties.alarmJobs.single { alarm -> alarm.conditions.any { it.item == HOUSE_ALERT_ITEM } }
        assertThat(house.delayMinutes).isGreaterThanOrEqualTo(LEAST_DELAY_BEFORE_ASKING_FOR_A_FIRE)
        assertThat(house.intervalMinutes).isGreaterThanOrEqualTo(LEAST_INTERVAL_BETWEEN_ASKING_AGAIN)

        val tomorrow = properties.alarmJobs.single { alarm -> alarm.conditions.any { it.item == BOILER_ALERT_ITEM } }
        assertThat(tomorrow.delayMinutes).isGreaterThan(0)
    }

    @Test
    fun `the sun credit is off until somebody has measured it`() {
        // A credit that is guessed makes the advisory ask for too little wood.
        properties.boilerAdviceJobs.forEach { boiler ->
            assertThat(boiler.chargePercentPerMegajoule).isZero()
            assertThat(boiler.chargePercentPerDegreeDay).isGreaterThan(0.0)
        }
    }

    @Test
    fun `one switch turns every advisory off`() {
        val statuses = properties.panelAdviceJobs.map { it.statusItem } +
            properties.boilerAdviceJobs.map { it.statusItem } +
            properties.houseDemand.statusItem
        assertThat(statuses.distinct()).hasSize(1)
    }

    private fun bind(): ConfigurationProperties {
        val sources = MutablePropertySources()
        YamlPropertySourceLoader()
            .load(APPLICATION_YAML, ClassPathResource(APPLICATION_YAML))
            .forEach { source -> sources.addLast(source) }
        return Binder(ConfigurationPropertySources.from(sources))
            .bind(PREFIX, Bindable.of(ConfigurationProperties::class.java))
            .get()
    }
}
