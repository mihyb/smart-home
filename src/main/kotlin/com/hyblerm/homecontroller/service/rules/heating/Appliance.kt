package com.hyblerm.homecontroller.service.rules.heating

import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.items.GenericItem
import com.hyblerm.homecontroller.service.rules.items.Switch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.abs

class Appliance(
    private val name: String,
    private val rooms: List<Room>,
    private val limits: Limits,
    switchId: SwitchId,
    val dataAccess: DataAccess,
    val supportsCooling: Boolean
) {

    private val logger: Logger = LoggerFactory.getLogger(Appliance::class.java)

    private val switchItem: Switch = Switch(switchId.commandId, dataAccess)
    private val tempItem: GenericItem = GenericItem(switchId.tempId, dataAccess)

    fun keepTemperature(mode: WorkingMode) {
        val hotRoom = rooms.asSequence()
            .filter { room -> room.getRequestedAction() == Room.Mode.COOL }
            .maxByOrNull { room -> room.getRequestedTemp() }

        val coldRoom = rooms.asSequence()
            .filter { room -> room.getRequestedAction() == Room.Mode.HEAT }
            .maxByOrNull { room -> room.getRequestedTemp() }
        if (hasTemperatureConflict(coldRoom, hotRoom)) {
            logger.debug("$name: Both cold ${coldRoom!!.name} and hot room ${hotRoom!!.name} are present, Heating is not being changed.")
            return
        }
        if (mode == WorkingMode.HEAT) {
            heat(hotRoom, coldRoom)
        } else {
            cool(hotRoom, coldRoom)
        }
    }

    private fun hasTemperatureConflict(
        coldRoom: Room?,
        hotRoom: Room?
    ) = coldRoom != null && hotRoom != null

    private fun cool(hotRoom: Room?, coldRoom: Room?) {
        if (!supportsCooling) {
            logger.debug("$name: Appliance doesn't support cooling.")
            return
        }
        if (hotRoom != null && !switchItem.isOn()) {
            val tempDeviance = abs(hotRoom.getRequestedTemp() - hotRoom.temperature)
            if (tempDeviance > limits.lowerLimit) {
                tempItem.command(hotRoom.getRequestedTemp().toString())
                switchItem.turnOn()
                logger.info("$name: Turned on because of ${hotRoom.name} is too hot. Requested temp ${hotRoom.getRequestedTemp()} and actual temp ${hotRoom.temperature}")
            } else {
                logger.debug("$name: Appliance already on.")
            }
        } else if (coldRoom != null && switchItem.isOn()) {
            val tempDeviance = abs(coldRoom.getRequestedTemp() - coldRoom.temperature)
            if (tempDeviance > limits.upperLimit) {
                switchItem.turnOff()
                logger.info("$name: Turned off because of ${coldRoom.name} is too cold. Requested temp ${coldRoom.getRequestedTemp()} and actual temp ${coldRoom.temperature}")
            } else {
                logger.debug("$name: Appliance already off.")
            }
        }
    }

    private fun heat(hotRoom: Room?, coldRoom: Room?) {
        if (coldRoom != null && !switchItem.isOn()) {
            val tempDeviance = abs(coldRoom.getRequestedTemp() - coldRoom.temperature)
            if (tempDeviance > limits.lowerLimit) {
                tempItem.command(coldRoom.getRequestedTemp().toString())
                switchItem.turnOn()
                logger.info("$name: Turned on because of ${coldRoom.name} is too cold. Requested temp ${coldRoom.getRequestedTemp()} and actual temp ${coldRoom.temperature}")
            } else {
                logger.debug("$name: Appliance already on. Cold room ${coldRoom.name} is too cold. Requested temp ${coldRoom.getRequestedTemp()} and actual temp ${coldRoom.temperature}")
            }
        } else if (hotRoom != null && switchItem.isOn()) {
            val tempDeviance = abs(hotRoom.getRequestedTemp() - hotRoom.temperature)
            if (tempDeviance > limits.upperLimit) {
                switchItem.turnOff()
                logger.info("$name: Turned off because of ${hotRoom.name} is too hot. Requested temp ${hotRoom.getRequestedTemp()} and actual temp ${hotRoom.temperature}")
            } else {
                logger.debug("$name: Appliance already off. Hot room: ${hotRoom.name} is too hot. Requested temp ${hotRoom.getRequestedTemp()} and actual temp ${hotRoom.temperature}")
            }
        } else {
            logger.debug("$name: Neither cold or hot room present.")
        }
    }

    data class Limits(val lowerLimit: Double, val upperLimit: Double)
    data class SwitchId(val tempId: String, val commandId: String)

    enum class WorkingMode {
        COOL,
        HEAT
    }
}
