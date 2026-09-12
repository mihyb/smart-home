package com.hyblerm.homecontroller.repository.entity

class OpenHabModel {

    data class Item(
        val link: String,
        val name: String,
        val state: String
    ) {
        fun isOn(): Boolean {
            return state == "ON"
        }

        fun getInt(): Int {
            return state.toDouble().toInt()
        }

        fun getDouble(): Double {
            return state.toDouble()
        }

        /**
         * The value, or null when openHAB has no state for the item.
         *
         * An item reads "NULL" before its first update and whenever its thing is
         * offline, which [getDouble] turns into a NumberFormatException. A rule
         * that reads a sensor has to expect that, so it can skip rather than die.
         */
        fun getDoubleOrNull(): Double? {
            return state.toDoubleOrNull()
        }

        fun getPercent(): Int {
            return state.replace("%", "").replace(" ", "").toInt()
        }
    }
}
