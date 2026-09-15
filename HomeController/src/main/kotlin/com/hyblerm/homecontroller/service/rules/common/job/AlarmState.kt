package com.hyblerm.homecontroller.service.rules.common.job

import java.time.Instant

/**
 * Where one alarm stands between silent and having said its piece.
 *
 * It lives in memory in [com.hyblerm.homecontroller.service.rules.common.AlarmJobRunner]
 * and is deliberately not persisted: after a restart every alarm starts CLEAR,
 * so a condition that is still true is announced once more. Repeating a live
 * alarm after a restart is the harmless direction to be wrong in.
 */
data class AlarmState(
    val phase: Phase = Phase.CLEAR,
    val trueSince: Instant? = null,
    val falseSince: Instant? = null,
    val sent: Int = 0,
    val lastSent: Instant? = null
) {
    enum class Phase {
        CLEAR, PENDING, FIRING, SILENT
    }
}
