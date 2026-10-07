package com.hyblerm.homecontroller.service.rules.common.job

/** One room below the bottom of its band, and whether anything local can fix it. */
data class ColdRoom(val name: String, val gapKelvin: Double, val hasLocalHeater: Boolean)

/**
 * How much of the house is below the band it is meant to be in.
 *
 * The distinction this exists to carry: one cold room is work for that room's own
 * electric panel, several cold rooms are work for the boiler. Firing the whole
 * system because the study is a degree down wastes a burn; running panels in three
 * rooms because nobody lit the boiler wastes money, since wood is the cheaper heat.
 *
 * [wholeHouse] is that verdict, settled once where the threshold is configured
 * rather than again in every job that needs it. A cold room with no panel of its
 * own settles it too, whatever the count -- the boiler is the only thing that can
 * answer for that room.
 *
 * A room whose sensor or band cannot be read appears in neither list. A room with
 * no minimum set is not a cold room; it is a room nobody has said anything about.
 */
data class HouseDemand(
    val coldRooms: List<ColdRoom>,
    val unreadableRooms: List<String>,
    /**
     * How much longer each room stays in use, in minutes; 0 means nobody is in it now.
     *
     * A room absent from the map is one this verdict says nothing about -- an unconfigured
     * house, or the advisory switched off -- and a job reading it must fall back to its
     * own judgement rather than treat silence as an empty room.
     */
    val occupiedMinutesLeft: Map<String, Int>,
    val wholeHouse: Boolean
) {

    val coldest: ColdRoom? get() = coldRooms.maxByOrNull { room -> room.gapKelvin }

    val emptyRooms: List<String>
        get() = occupiedMinutesLeft.filterValues { minutes -> minutes <= 0 }.keys.sorted()

    /** Minutes of use left in that room, or null when nothing is known about it. */
    fun minutesLeft(room: String): Int? = occupiedMinutesLeft[room]

    companion object {

        /** Nothing is known, so nothing is claimed: each job falls back to its own reading. */
        fun unknown(): HouseDemand = HouseDemand(emptyList(), emptyList(), emptyMap(), false)
    }
}
