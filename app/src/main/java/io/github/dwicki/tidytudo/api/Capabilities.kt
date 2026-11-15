package io.github.dwicki.tidytudo.api

/**
 * Capability names the app understands. The robot reports plain class names; anything not in
 * this registry is shown as "not supported by this app yet" instead of being ignored silently.
 */
object Capabilities {
    const val BASIC_CONTROL = "BasicControlCapability"
    const val LOCATE = "LocateCapability"
    const val FAN_SPEED = "FanSpeedControlCapability"
    const val WATER_USAGE = "WaterUsageControlCapability"
    const val OPERATION_MODE = "OperationModeControlCapability"
    const val SEGMENT_CLEANING = "MapSegmentationCapability"
    const val SEGMENT_RENAME = "MapSegmentRenameCapability"
    const val ZONE_CLEANING = "ZoneCleaningCapability"
    const val GO_TO_LOCATION = "GoToLocationCapability"
    const val VIRTUAL_RESTRICTIONS = "CombinedVirtualRestrictionsCapability"
    const val CONSUMABLES = "ConsumableMonitoringCapability"
    const val DO_NOT_DISTURB = "DoNotDisturbCapability"
    const val SPEAKER_VOLUME = "SpeakerVolumeControlCapability"
    const val SPEAKER_TEST = "SpeakerTestCapability"
    const val VOICE_PACK = "VoicePackManagementCapability"
    const val TOTAL_STATISTICS = "TotalStatisticsCapability"
    const val CURRENT_STATISTICS = "CurrentStatisticsCapability"
    const val PENDING_MAP_CHANGE = "PendingMapChangeHandlingCapability"
    const val CARPET_SENSOR_MODE = "CarpetSensorModeControlCapability"
    const val AUTO_EMPTY_TRIGGER = "AutoEmptyDockManualTriggerCapability"
    const val MOP_CLEAN_TRIGGER = "MopDockCleanManualTriggerCapability"
    const val MOP_DRY_TRIGGER = "MopDockDryManualTriggerCapability"
    const val OBSTACLE_IMAGES = "ObstacleImagesCapability"
    const val WASH_TEMPERATURE = "MopDockMopWashTemperatureControlCapability"
    const val AUTO_EMPTY_INTERVAL = "AutoEmptyDockAutoEmptyIntervalControlCapability"

    /** Dock actions that all take `{"action":"trigger"}`. */
    val dockTriggers = listOf(AUTO_EMPTY_TRIGGER, MOP_CLEAN_TRIGGER, MOP_DRY_TRIGGER)

    /**
     * Simple on/off settings: `GET` → `{"enabled": …}`, `PUT {"action": "enable" | "disable"}`.
     * Verified against the OpenAPI document of Valetudo 2026.08.0. New switches still need an
     * entry here, because some `…ControlCapability` classes use other shapes (presets, modes).
     */
    val switches = listOf(
        "KeyLockCapability",
        "CarpetModeControlCapability",
        "ObstacleAvoidanceControlCapability",
        "PetObstacleAvoidanceControlCapability",
        "CollisionAvoidantNavigationControlCapability",
        OBSTACLE_IMAGES,
        "CameraLightControlCapability",
        "MopExtensionControlCapability",
        "MopTwistControlCapability",
        "MopExtensionFurnitureLegHandlingControlCapability",
        "MopDockMopAutoDryingControlCapability",
        "FloorMaterialDirectionAwareNavigationControlCapability",
    )

    private val known: Set<String> = setOf(
        BASIC_CONTROL, LOCATE, FAN_SPEED, WATER_USAGE, OPERATION_MODE, SEGMENT_CLEANING, SEGMENT_RENAME,
        ZONE_CLEANING, GO_TO_LOCATION, VIRTUAL_RESTRICTIONS, CONSUMABLES, DO_NOT_DISTURB, SPEAKER_VOLUME,
        SPEAKER_TEST, VOICE_PACK, TOTAL_STATISTICS, CURRENT_STATISTICS, PENDING_MAP_CHANGE, CARPET_SENSOR_MODE,
        WASH_TEMPERATURE, AUTO_EMPTY_INTERVAL,
    ) + dockTriggers + switches

    fun isKnown(name: String): Boolean = name in known

    fun unknownOf(reported: Collection<String>): List<String> = reported.filterNot(::isKnown).sorted()
}
