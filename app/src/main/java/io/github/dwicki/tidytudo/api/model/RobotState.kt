package io.github.dwicki.tidytudo.api.model

/** Snapshot of a robot's state attributes, decoded tolerant of unknown values. */
data class RobotState(
    val status: Status = Status(),
    val battery: Battery? = null,
    /** Keyed by preset type: `fan_speed`, `water_grade`, `operation_mode`. */
    val presets: Map<String, String> = emptyMap(),
    val attachments: List<Attachment> = emptyList(),
    val dockStatus: String? = null,
    val dockComponents: List<DockComponent> = emptyList(),
) {
    val fanSpeed: String? get() = presets[PRESET_FAN_SPEED]
    val waterUsage: String? get() = presets[PRESET_WATER_GRADE]
    val operationMode: String? get() = presets[PRESET_OPERATION_MODE]

    companion object {
        const val PRESET_FAN_SPEED = "fan_speed"
        const val PRESET_WATER_GRADE = "water_grade"
        const val PRESET_OPERATION_MODE = "operation_mode"
    }
}

data class Status(
    val value: StatusValue = StatusValue.UNKNOWN,
    /** `none`, `zone`, `segment`, `spot`, `target`, `resumable`, `mapping`, or something newer. */
    val flag: String = "none",
    val error: RobotError? = null,
) {
    val isResumable: Boolean get() = flag == "resumable"
}

enum class StatusValue(val apiName: String) {
    ERROR("error"),
    DOCKED("docked"),
    IDLE("idle"),
    RETURNING("returning"),
    CLEANING("cleaning"),
    PAUSED("paused"),
    MANUAL_CONTROL("manual_control"),
    MOVING("moving"),
    UNKNOWN("unknown");

    /** States in which the robot is doing something the user may want to pause or stop. */
    val isActive: Boolean get() = this == CLEANING || this == RETURNING || this == MOVING || this == MANUAL_CONTROL

    companion object {
        fun fromApi(value: String?): StatusValue = entries.firstOrNull { it.apiName == value } ?: UNKNOWN
    }
}

data class RobotError(
    val message: String,
    val subsystem: String = "unknown",
    val severityLevel: String = "unknown",
    val vendorErrorCode: String? = null,
)

data class Battery(val level: Int, val flag: String = "none") {
    val isCharging: Boolean get() = flag == "charging"
}

data class Attachment(val type: String, val attached: Boolean)

data class DockComponent(val type: String, val value: String) {
    /** Anything except `ok` (and `unknown`, which docks report when they lack a sensor) needs attention. */
    val needsAttention: Boolean get() = value != "ok" && value != "unknown"
}
