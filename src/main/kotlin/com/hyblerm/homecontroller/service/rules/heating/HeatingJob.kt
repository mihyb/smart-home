package com.hyblerm.homecontroller.service.rules.heating

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.JobBase
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class HeatingJob(val dataAccess: DataAccess) : JobBase(dataAccess) {

    val logger: Logger = LoggerFactory.getLogger(HeatingJob::class.java)

    val requestedDayTemp: String = "g2_house_temp"
    val requestedNightTemp: String = "g2_house_night_temp"
    val livingRoomDay: String = "lr_day"
    val heatingDelayMinutes: String = "g2_heating_delay_minutes"
    val kidsRoomDay: String = "kr_day"
    val livingRoomDev: String = "g2_living_room_temp_dev"
    val livingRoomTemp: String = "mqtt_topic_db0eec51_TASMOTA_4_TEMP"
    val workRoomDev: String = "g2_work_room_temp_dev"
    val workRoomTemp: String = "mqtt_topic_30ec045c_TASMOTA_SWITCH_5_TEMP"
    val kidsRoomDev: String = "g2_kids_room_temp_dev"
    val kidsRoomTemp: String = "broadlink_floureonthermostat_192_168_0_7_roomtemperature"
    val acLower: String = "acLower"
    val acUpper: String = "acUpper"
    val lrFloorHeatingLower: String = "lrFloorHeatingLower"
    val lrFloorHeatingUpper: String = "lrFloorHeatingUpper"
    val krFloorHeatingLower: String = "krFloorHeatingLower"
    val krFloorHeatingUpper: String = "krFloorHeatingUpper"
    val livingRoomAcSwitch: String = "g2_living_room_AC_switch"
    val livingRoomAcTemp: String = "g2_living_room_AC_temp"
    val livingRoomFloorSwitch: String = "g2_living_room_floor_switch"
    val livingRoomFloorTemp: String = "g2_living_room_floor_temp"
    val kidsRoomFloorSwitch: String = "broadlink_floureonthermostat_192_168_0_7_power"
    val kidsRoomFloorTemp: String = "broadlink_floureonthermostat_192_168_0_7_setpoint"
    val acMode: String = "g2_ac_mode"
    val heatingMainSwitch: String = "heating_main_switch"
    val jablotronStatus: String = "JablotronJA100_Dum"
    val solarBatterySoc: String = "pp_battery_soc"
    val solarPanelProduction: String = "pp_ppv"

    @Scheduled(fixedRate = 60, timeUnit = TimeUnit.SECONDS)
    fun keepTemperature() {
        logger.debug("heating jobs started")
        if (!item(heatingMainSwitch).isOn()) {
            logger.debug("heating is off")
            return
        }

        val dayTemperature = getDayTemperature()
        val nightTemperature = item(requestedNightTemp).getDouble()

        val heatingDelay = item(heatingDelayMinutes).getInt()
        val houseLocked = item(jablotronStatus).state == "ARMED"

        val livingRoom = Room(
            "Living room",
            item(livingRoomTemp).getDouble(),
            item(livingRoomDev).getInt(),
            if (isDay(heatingDelay, livingRoomDay, houseLocked)) dayTemperature else nightTemperature
        )
        val kidsRoom = Room(
            "Kids room",
            item(kidsRoomTemp).getDouble(),
            item(kidsRoomDev).getInt(),
            if (isDay(heatingDelay, kidsRoomDay, houseLocked)) dayTemperature else nightTemperature
        )
        val workRoom = Room(
            "Work room",
            item(workRoomTemp).getDouble(),
            item(workRoomDev).getInt(),
            if (isDay(heatingDelay, livingRoomDay, houseLocked)) dayTemperature else nightTemperature
        )

        val airConditioner = Appliance(
            "AC",
            listOf(livingRoom, workRoom),
            Appliance.Limits(item(acLower).getDouble(), item(acUpper).getDouble()),
            Appliance.SwitchId(livingRoomAcTemp, livingRoomAcSwitch),
            dataAccess,
            true
        )
        val livingRoomFloor = Appliance(
            "Floor heating living room",
            listOf(livingRoom),
            Appliance.Limits(item(lrFloorHeatingLower).getDouble(), item(lrFloorHeatingUpper).getDouble()),
            Appliance.SwitchId(livingRoomFloorTemp, livingRoomFloorSwitch),
            dataAccess,
            false
        )
        val kidsRoomFloor = Appliance(
            "Floor heating kids room",
            listOf(kidsRoom),
            Appliance.Limits(item(krFloorHeatingLower).getDouble(), item(krFloorHeatingUpper).getDouble()),
            Appliance.SwitchId(kidsRoomFloorTemp, kidsRoomFloorSwitch),
            dataAccess,
            false
        )

        HeatingSystem(listOf(airConditioner, livingRoomFloor, kidsRoomFloor)).keepTemperature(heatingMode())
    }

    fun getDayTemperature(): Double {
        val dayTemp = item(requestedDayTemp).getDouble()
        return if (isSolarActiveAndCharged()) {
            val fullSolarTemperatureBoost = 0 // TODO reflect heating/cooling to value
            logger.debug("Battery is charged. Increasing day temperature by $fullSolarTemperatureBoost")
            dayTemp + fullSolarTemperatureBoost
        } else {
            dayTemp
        }
    }

    fun isDay(delay: Int, dayItem: String, houseLocked: Boolean): Boolean {
        return !houseLocked && delay <= 0 && item(dayItem).isOn()
    }

    fun heatingMode(): Appliance.WorkingMode {
        return if (item(acMode).isOn()) Appliance.WorkingMode.HEAT else Appliance.WorkingMode.COOL
    }

    fun isSolarActiveAndCharged(): Boolean {
        val batterySoc = item(solarBatterySoc).getInt()
        val solarProduction = item(solarPanelProduction).getInt()
        return batterySoc > 95 && solarProduction > 3000
    }
}
