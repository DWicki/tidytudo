package io.github.dwicki.tidytudo.ui.schedules

import io.github.dwicki.tidytudo.api.model.ValetudoTimer
import io.github.dwicki.tidytudo.util.LocalSchedule
import io.github.dwicki.tidytudo.util.TimeFormat
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId

/** What the timer editor edits, in local time. Converted from and to Valetudo's UTC timer only at the edges. */
data class TimerDraft(
    val time: LocalTime,
    val days: Set<DayOfWeek>,
    val label: String,
    val rooms: List<String>,
    /** Pre-action type → preset value, e.g. `fan_speed_control` → `max`. */
    val preActions: Map<String, String>,
) {
    /** The edited [base] timer; keeps its id and enabled state. No rooms means a full cleanup. */
    fun applyTo(base: ValetudoTimer, zone: ZoneId = ZoneId.systemDefault()): ValetudoTimer {
        val (utcDays, hour, minute) = TimeFormat.toUtc(LocalSchedule(days, time), zone)
        val action = if (rooms.isEmpty()) {
            ValetudoTimer.Action(ValetudoTimer.FULL_CLEANUP)
        } else {
            ValetudoTimer.Action(ValetudoTimer.SEGMENT_CLEANUP, ValetudoTimer.Params(segmentIds = rooms, iterations = 1))
        }
        return base.copy(
            label = label.ifBlank { null },
            dow = utcDays,
            hour = hour,
            minute = minute,
            action = action,
            preActions = preActions.map { (type, value) -> ValetudoTimer.PreAction(type, ValetudoTimer.PreActionParams(value)) },
        )
    }

    companion object {
        private const val DEFAULT_HOUR = 9

        /** A new timer starts at 09:00 on every day. */
        fun from(timer: ValetudoTimer, zone: ZoneId = ZoneId.systemDefault()): TimerDraft {
            val local = if (timer.id.isEmpty()) {
                LocalSchedule(DayOfWeek.entries.toSet(), LocalTime.of(DEFAULT_HOUR, 0))
            } else {
                TimeFormat.toLocal(timer.dow, timer.hour, timer.minute, zone)
            }
            return TimerDraft(
                time = local.time,
                days = local.days,
                label = timer.label.orEmpty(),
                rooms = timer.action.params.segmentIds.orEmpty(),
                preActions = timer.preActions.associate { it.type to it.params.value },
            )
        }
    }
}
