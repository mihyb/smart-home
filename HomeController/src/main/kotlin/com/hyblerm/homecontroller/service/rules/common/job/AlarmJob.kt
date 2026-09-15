package com.hyblerm.homecontroller.service.rules.common.job

import com.hyblerm.homecontroller.config.ConfigurationProperties.AlarmJobConfig
import com.hyblerm.homecontroller.config.ConfigurationProperties.Alarms
import com.hyblerm.homecontroller.config.ConfigurationProperties.Severity
import com.hyblerm.homecontroller.service.repository.DataAccess
import com.hyblerm.homecontroller.service.rules.common.items.GenericItem
import com.hyblerm.homecontroller.service.rules.common.job.AlarmState.Phase
import com.hyblerm.homecontroller.service.util.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

private const val CLEAR_SEVERITY = "CLEAR"
private const val CLEARED_ID_SUFFIX = "-ok"
private const val FIELD_SEPARATOR = "|"
private val PLACEHOLDER = Regex("\\{([A-Za-z0-9_]+)}")

/**
 * Watches one set of conditions and decides whether to interrupt you.
 *
 * The job owns no state of its own: it is handed the alarm's [AlarmState], is
 * built fresh on every cycle like every other job here, and returns the state
 * the runner should keep. What it emits is a command on the notification item,
 * never a push -- sendNotification exists only inside openHAB.
 */
open class AlarmJob(
    repository: DataAccess,
    private val config: AlarmJobConfig,
    private val alarms: Alarms,
    private val time: Time
) : JobBase(repository) {

    private val logger: Logger = LoggerFactory.getLogger("${AlarmJob::class.java.name}#${config.id}")

    fun check(state: AlarmState): AlarmState {
        val now = Instant.now(time.clock())
        return when (evaluate()) {
            true -> whileTrue(state, now)
            false -> whileFalse(state, now)
            // No reading at all. Holding the machine still means a live alarm is
            // neither repeated nor withdrawn while the sensor is unreachable.
            null -> state.also { logger.debug("no reading, holding {}", it.phase) }
        }
    }

    private fun evaluate(): Boolean? {
        if (config.conditions.isEmpty()) {
            logger.warn("no conditions configured, alarm can never fire")
            return false
        }
        var unknown = false
        config.conditions.forEach { condition ->
            when (ConditionEvaluator.matches(item(condition.item).state, condition)) {
                // A condition that is definitely false settles the conjunction
                // even when another one cannot be read.
                false -> return false
                null -> unknown = true
                true -> Unit
            }
        }
        return if (unknown) null else true
    }

    private fun whileTrue(state: AlarmState, now: Instant): AlarmState {
        val holding = state.copy(falseSince = null)
        return when (holding.phase) {
            Phase.CLEAR -> sendWhenDue(holding.copy(phase = Phase.PENDING, trueSince = now), now)
            Phase.PENDING -> sendWhenDue(holding, now)
            Phase.FIRING -> if (dueAgain(holding, now)) send(holding, now) else holding
            Phase.SILENT -> holding
        }
    }

    private fun whileFalse(state: AlarmState, now: Instant): AlarmState {
        if (state.phase == Phase.CLEAR) {
            return state
        }
        val falseSince = state.falseSince ?: now
        if (now < falseSince.plus(Duration.ofMinutes(config.clearMinutes))) {
            return state.copy(falseSince = falseSince)
        }
        if (state.sent > 0) {
            withdraw()
        }
        logger.info("cleared after {} message(s)", state.sent)
        return AlarmState()
    }

    private fun sendWhenDue(state: AlarmState, now: Instant): AlarmState {
        val due = state.trueSince!!.plus(Duration.ofMinutes(config.delayMinutes))
        return if (now < due) state else send(state, now)
    }

    private fun dueAgain(state: AlarmState, now: Instant): Boolean {
        return now >= state.lastSent!!.plus(Duration.ofMinutes(config.intervalMinutes))
    }

    private fun send(state: AlarmState, now: Instant): AlarmState {
        if (heldByQuietHours(now)) {
            // Held, not dropped: the condition is still true, so the first cycle
            // after quiet hours end sends it.
            logger.debug("quiet hours, holding message {}", state.sent + 1)
            return state
        }
        command("${config.severity}$FIELD_SEPARATOR${config.id}$FIELD_SEPARATOR${render(config.message)}")
        val sent = state.sent + 1
        logger.info("sent message {} of {}", sent, config.repeat)
        return state.copy(
            phase = if (config.repeat > 0 && sent >= config.repeat) Phase.SILENT else Phase.FIRING,
            sent = sent,
            lastSent = now
        )
    }

    private fun withdraw() {
        // Takes the notification off the phone. Without this an alarm you have
        // already dealt with sits in the notification list looking current.
        command("$CLEAR_SEVERITY$FIELD_SEPARATOR${config.id}$FIELD_SEPARATOR")
        if (config.clearMessage.isBlank()) {
            return
        }
        if (heldByQuietHours(Instant.now(time.clock()))) {
            // Unlike an alarm this one is not worth keeping for the morning: by
            // then the notification is gone and the fault is over.
            logger.debug("quiet hours, dropping the all-clear")
            return
        }
        command(
            "${Severity.INFO}$FIELD_SEPARATOR${config.id}$CLEARED_ID_SUFFIX" +
                "$FIELD_SEPARATOR${render(config.clearMessage)}"
        )
    }

    private fun heldByQuietHours(now: Instant): Boolean {
        if (config.severity == Severity.ALARM) {
            return false
        }
        val hour = now.atZone(time.clock().zone).hour
        return if (alarms.quietFromHour <= alarms.quietToHour) {
            hour >= alarms.quietFromHour && hour < alarms.quietToHour
        } else {
            hour >= alarms.quietFromHour || hour < alarms.quietToHour
        }
    }

    /** Replaces `{item_name}` with what the item currently reads. */
    private fun render(text: String): String {
        return PLACEHOLDER.replace(text) { match ->
            val name = match.groupValues[1]
            try {
                item(name).state
            } catch (e: RuntimeException) {
                logger.warn("cannot read {} for the message text", name, e)
                match.value
            }
        }
    }

    private fun command(payload: String) {
        GenericItem(alarms.messageItem, repository).command(payload)
    }
}
