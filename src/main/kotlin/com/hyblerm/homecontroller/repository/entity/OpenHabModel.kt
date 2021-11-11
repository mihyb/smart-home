package com.hyblerm.homecontroller.repository.entity

class OpenHabModel {

    data class Item(
            val link:String,
            val name:String,
            val state:String
    ) {
        fun isOn(): Boolean {
            return state == "ON"
        }

        fun getInt(): Int {
            return state.toInt()
        }

        fun getDouble(): Double {
            return state.toDouble()
        }
    }
}