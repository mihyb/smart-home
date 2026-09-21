package com.hyblerm.homecontroller.repository.entity

private const val UNIT_SEPARATOR = ' '

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

        /**
         * The number in the state, ignoring any unit, or null when there is none.
         *
         * A `Number:Temperature` item comes back from the REST API as its
         * rendered quantity -- "86.5 °C" -- so [getDoubleOrNull] returns null for
         * every reading one of them ever produces. This reads the number and
         * drops the unit, which is only safe where the item's unit is known and
         * fixed; it converts nothing. See the unit traps in the repository
         * CLAUDE.md before using it on anything dimensionless.
         */
        fun getQuantityOrNull(): Double? {
            return state.substringBefore(UNIT_SEPARATOR).toDoubleOrNull()
        }

        fun getPercent(): Int {
            return state.replace("%", "").replace(" ", "").toInt()
        }
    }
}
