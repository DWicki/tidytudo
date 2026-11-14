package io.github.dwicki.tidytudo.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Typed views of Valetudo responses. Only fields the app uses are declared; the JSON decoder
// ignores the rest, so new fields in future Valetudo versions do no harm (Q-7).

@Serializable
data class ValetudoVersion(val release: String, val commit: String = "")

@Serializable
data class ValetudoInfo(val systemId: String = "", val embedded: Boolean = true)

@Serializable
data class RobotInfo(
    val manufacturer: String = "",
    val modelName: String = "",
    val implementation: String = "",
)

@Serializable
data class RobotProperties(val firmwareVersion: String? = null)

@Serializable
data class HostInfo(
    val hostname: String = "",
    val arch: String = "",
    val uptime: Long = 0,
    val mem: Memory = Memory(),
) {
    @Serializable
    data class Memory(val total: Long = 0, val free: Long = 0)
}

@Serializable
data class Point(val x: Int, val y: Int)

@Serializable
data class CountRange(val min: Int = 1, val max: Int = 1)

@Serializable
data class Consumable(
    val type: String,
    val subType: String? = null,
    val remaining: Remaining = Remaining(),
) {
    @Serializable
    data class Remaining(val value: Int = 0, val unit: String = "minutes")
}

@Serializable
data class ConsumableProperties(val availableConsumables: List<ConsumableSpec> = emptyList())

@Serializable
data class ConsumableSpec(
    val type: String,
    val subType: String? = null,
    val unit: String = "minutes",
    val maxValue: Int? = null,
)

@Serializable
data class MapSegment(val id: String, val name: String? = null, val material: String? = null)

@Serializable
data class SegmentCleaningProperties(
    val iterationCount: CountRange = CountRange(),
    val customOrderSupport: Boolean = false,
)

@Serializable
data class ZoneCleaningProperties(
    val zoneCount: CountRange = CountRange(1, 1),
    val iterationCount: CountRange = CountRange(),
)

@Serializable
data class TimeOfDay(val hour: Int, val minute: Int)

@Serializable
data class DoNotDisturb(
    val enabled: Boolean,
    val start: TimeOfDay,
    val end: TimeOfDay,
)

@Serializable
data class SpeakerVolume(val volume: Int)

@Serializable
data class VoicePackState(
    val currentLanguage: String = "",
    val operationStatus: OperationStatus = OperationStatus(),
) {
    @Serializable
    data class OperationStatus(val type: String = "idle", val progress: Int? = null)
}

@Serializable
data class DataPoint(val type: String, val value: Long)

@Serializable
data class Toggle(val enabled: Boolean)

@Serializable
data class PendingMapChange(val pending: Boolean)

@Serializable
data class CarpetSensorMode(val mode: String)

@Serializable
data class CarpetSensorModeProperties(val supportedModes: List<String> = emptyList())

@Serializable
data class VirtualRestrictions(
    val virtualWalls: List<VirtualWall> = emptyList(),
    val restrictedZones: List<RestrictedZone> = emptyList(),
)

@Serializable
data class VirtualWall(val points: WallPoints) {
    @Serializable
    data class WallPoints(val pA: Point, val pB: Point)
}

@Serializable
data class RestrictedZone(val type: String = "regular", val points: ZonePoints)

/** Four corners, clockwise from top left; used by zones and restricted zones alike. */
@Serializable
data class ZonePoints(val pA: Point, val pB: Point, val pC: Point, val pD: Point)

@Serializable
data class RestrictionProperties(val supportedRestrictedZoneTypes: List<String> = listOf("regular"))

@Serializable
data class ValetudoTimer(
    val id: String = "",
    val enabled: Boolean = true,
    val label: String? = null,
    /** 0 = Sunday … 6 = Saturday, in UTC like [hour] and [minute]. */
    val dow: List<Int> = emptyList(),
    val hour: Int = 0,
    val minute: Int = 0,
    val action: Action = Action(),
    @SerialName("pre_actions") val preActions: List<PreAction> = emptyList(),
) {
    @Serializable
    data class Action(val type: String = FULL_CLEANUP, val params: Params = Params())

    @Serializable
    data class Params(
        @SerialName("segment_ids") val segmentIds: List<String>? = null,
        val iterations: Int? = null,
        @SerialName("custom_order") val customOrder: Boolean? = null,
    )

    @Serializable
    data class PreAction(val type: String, val params: PreActionParams)

    @Serializable
    data class PreActionParams(val value: String)

    companion object {
        const val FULL_CLEANUP = "full_cleanup"
        const val SEGMENT_CLEANUP = "segment_cleanup"
        const val PRE_FAN_SPEED = "fan_speed_control"
        const val PRE_WATER_USAGE = "water_usage_control"
        const val PRE_OPERATION_MODE = "operation_mode_control"
    }
}

@Serializable
data class TimerProperties(
    val supportedActions: List<String> = listOf(ValetudoTimer.FULL_CLEANUP),
    val supportedPreActions: List<String> = emptyList(),
)
