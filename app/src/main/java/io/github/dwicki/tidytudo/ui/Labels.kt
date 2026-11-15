package io.github.dwicki.tidytudo.ui

import android.content.Context
import androidx.annotation.StringRes
import io.github.dwicki.tidytudo.R
import io.github.dwicki.tidytudo.api.ApiError
import io.github.dwicki.tidytudo.api.model.RobotEvent
import io.github.dwicki.tidytudo.api.model.StatusValue

/**
 * Turns Valetudo's machine values into words. Known values have translations; unknown ones
 * (newer Valetudo versions) are humanised, never hidden (Q-7).
 */
object Labels {

    @StringRes
    fun status(value: StatusValue): Int = when (value) {
        StatusValue.ERROR -> R.string.status_error
        StatusValue.DOCKED -> R.string.status_docked
        StatusValue.IDLE -> R.string.status_idle
        StatusValue.RETURNING -> R.string.status_returning
        StatusValue.CLEANING -> R.string.status_cleaning
        StatusValue.PAUSED -> R.string.status_paused
        StatusValue.MANUAL_CONTROL -> R.string.status_manual_control
        StatusValue.MOVING -> R.string.status_moving
        StatusValue.UNKNOWN -> R.string.status_unknown
    }

    /** Short addition such as "Rooms" for `segment`; null when the flag adds nothing. */
    @StringRes
    fun statusFlag(flag: String): Int? = when (flag) {
        "zone" -> R.string.flag_zone
        "segment" -> R.string.flag_segment
        "spot" -> R.string.flag_spot
        "target" -> R.string.flag_target
        "resumable" -> R.string.flag_resumable
        "mapping" -> R.string.flag_mapping
        else -> null
    }

    @StringRes
    fun errorSubsystem(subsystem: String?): Int = when (subsystem) {
        "core" -> R.string.subsystem_core
        "power" -> R.string.subsystem_power
        "sensors" -> R.string.subsystem_sensors
        "motors" -> R.string.subsystem_motors
        "navigation" -> R.string.subsystem_navigation
        "attachments" -> R.string.subsystem_attachments
        "dock" -> R.string.subsystem_dock
        else -> R.string.subsystem_unknown
    }

    fun preset(context: Context, value: String): String = when (value) {
        "off" -> context.getString(R.string.preset_off)
        "min" -> context.getString(R.string.preset_min)
        "low" -> context.getString(R.string.preset_low)
        "medium" -> context.getString(R.string.preset_medium)
        "high" -> context.getString(R.string.preset_high)
        "max" -> context.getString(R.string.preset_max)
        "turbo" -> context.getString(R.string.preset_turbo)
        "custom" -> context.getString(R.string.preset_custom)
        "vacuum" -> context.getString(R.string.mode_vacuum)
        "mop" -> context.getString(R.string.mode_mop)
        "vacuum_and_mop" -> context.getString(R.string.mode_vacuum_and_mop)
        "vacuum_then_mop" -> context.getString(R.string.mode_vacuum_then_mop)
        else -> humanize(value)
    }

    fun consumable(context: Context, type: String, subType: String?): String {
        val main = when (type) {
            "brush" -> when (subType) {
                "main" -> R.string.consumable_main_brush
                "side_left", "side_right", "side" -> R.string.consumable_side_brush
                else -> R.string.consumable_brush
            }
            "filter" -> R.string.consumable_filter
            "cleaning" -> when (subType) {
                "sensor" -> R.string.consumable_sensor_cleaning
                "wheel" -> R.string.consumable_wheel_cleaning
                else -> R.string.consumable_cleaning
            }
            "mop" -> R.string.consumable_mop
            "detergent" -> R.string.consumable_detergent
            "bin" -> R.string.consumable_bin
            else -> null
        }
        val sideSuffix = when (subType) {
            "side_left" -> " (${context.getString(R.string.side_left)})"
            "side_right" -> " (${context.getString(R.string.side_right)})"
            else -> ""
        }
        return (main?.let(context::getString) ?: humanize(listOfNotNull(type, subType).joinToString(" "))) + sideSuffix
    }

    fun dockComponent(context: Context, type: String): String = when (type) {
        "water_tank_clean" -> context.getString(R.string.dock_clean_water)
        "water_tank_dirty" -> context.getString(R.string.dock_dirty_water)
        "dustbag" -> context.getString(R.string.dock_dust_bag)
        "detergent" -> context.getString(R.string.dock_detergent)
        else -> humanize(type)
    }

    fun dockComponentValue(context: Context, value: String): String = when (value) {
        "ok" -> context.getString(R.string.dock_value_ok)
        "missing" -> context.getString(R.string.dock_value_missing)
        "empty" -> context.getString(R.string.dock_value_empty)
        "full" -> context.getString(R.string.dock_value_full)
        else -> context.getString(R.string.dock_value_unknown)
    }

    fun dockStatus(context: Context, value: String): String = when (value) {
        "idle" -> context.getString(R.string.dock_status_idle)
        "pause" -> context.getString(R.string.dock_status_pause)
        "emptying" -> context.getString(R.string.dock_status_emptying)
        "cleaning" -> context.getString(R.string.dock_status_cleaning)
        "drying" -> context.getString(R.string.dock_status_drying)
        "error" -> context.getString(R.string.dock_status_error)
        else -> humanize(value)
    }

    fun attachment(context: Context, type: String): String = when (type) {
        "dustbin" -> context.getString(R.string.attachment_dustbin)
        "watertank" -> context.getString(R.string.attachment_watertank)
        "mop" -> context.getString(R.string.attachment_mop)
        else -> humanize(type)
    }

    @StringRes
    fun eventTitle(kind: RobotEvent.Kind): Int = when (kind) {
        RobotEvent.Kind.CONSUMABLE_DEPLETED -> R.string.event_consumable_depleted
        RobotEvent.Kind.DUST_BIN_FULL -> R.string.event_dust_bin_full
        RobotEvent.Kind.ERROR_STATE -> R.string.event_error
        RobotEvent.Kind.MISSING_RESOURCE -> R.string.event_missing_resource
        RobotEvent.Kind.MOP_ATTACHMENT_REMINDER -> R.string.event_mop_reminder
        RobotEvent.Kind.PENDING_MAP_CHANGE -> R.string.event_pending_map_change
        RobotEvent.Kind.VALETUDO_UPDATED -> R.string.event_valetudo_updated
        RobotEvent.Kind.RUNTIME_ERROR -> R.string.event_runtime_error
        RobotEvent.Kind.OTHER -> R.string.event_other
    }

    fun eventDescription(context: Context, event: RobotEvent): String = when (event.kind) {
        RobotEvent.Kind.CONSUMABLE_DEPLETED -> context.getString(
            R.string.event_consumable_depleted_text,
            consumable(context, event.consumableType.orEmpty(), event.consumableSubType),
        )
        RobotEvent.Kind.DUST_BIN_FULL -> context.getString(R.string.event_dust_bin_full_text)
        RobotEvent.Kind.MOP_ATTACHMENT_REMINDER -> context.getString(R.string.event_mop_reminder_text)
        RobotEvent.Kind.PENDING_MAP_CHANGE -> context.getString(R.string.event_pending_map_change_text)
        RobotEvent.Kind.MISSING_RESOURCE -> event.message ?: context.getString(R.string.event_missing_resource_text)
        RobotEvent.Kind.OTHER -> event.message ?: humanize(event.className.removeSuffix("ValetudoEvent"))
        else -> event.message.orEmpty()
    }

    @StringRes
    fun interaction(interaction: String): Int = when (interaction) {
        RobotEvent.INTERACTION_RESET -> R.string.event_action_reset
        RobotEvent.INTERACTION_YES -> R.string.event_action_accept
        RobotEvent.INTERACTION_NO -> R.string.event_action_reject
        else -> R.string.event_action_dismiss
    }

    /** Names for the simple on/off capabilities in [io.github.dwicki.tidytudo.api.Capabilities.switches]. */
    fun switchName(context: Context, capability: String): String = when (capability) {
        "KeyLockCapability" -> context.getString(R.string.switch_key_lock)
        "CarpetModeControlCapability" -> context.getString(R.string.switch_carpet_mode)
        "ObstacleAvoidanceControlCapability" -> context.getString(R.string.switch_obstacle_avoidance)
        "PetObstacleAvoidanceControlCapability" -> context.getString(R.string.switch_pet_obstacle_avoidance)
        "CollisionAvoidantNavigationControlCapability" -> context.getString(R.string.switch_collision_avoidance)
        "ObstacleImagesCapability" -> context.getString(R.string.switch_obstacle_images)
        "CameraLightControlCapability" -> context.getString(R.string.switch_camera_light)
        "MopExtensionControlCapability" -> context.getString(R.string.switch_mop_extension)
        "MopTwistControlCapability" -> context.getString(R.string.switch_mop_twist)
        "MopExtensionFurnitureLegHandlingControlCapability" -> context.getString(R.string.switch_furniture_legs)
        "MopDockMopAutoDryingControlCapability" -> context.getString(R.string.switch_mop_auto_drying)
        "FloorMaterialDirectionAwareNavigationControlCapability" -> context.getString(R.string.switch_floor_direction)
        else -> capabilityName(capability)
    }

    fun switchDescription(context: Context, capability: String): String? = when (capability) {
        "KeyLockCapability" -> context.getString(R.string.switch_key_lock_text)
        "CarpetModeControlCapability" -> context.getString(R.string.switch_carpet_mode_text)
        "CollisionAvoidantNavigationControlCapability" -> context.getString(R.string.switch_collision_avoidance_text)
        "MopTwistControlCapability" -> context.getString(R.string.switch_mop_twist_text)
        "FloorMaterialDirectionAwareNavigationControlCapability" -> context.getString(R.string.switch_floor_direction_text)
        else -> null
    }

    fun washTemperature(context: Context, value: String): String = when (value) {
        "cold" -> context.getString(R.string.wash_temperature_cold)
        "warm" -> context.getString(R.string.wash_temperature_warm)
        "hot" -> context.getString(R.string.wash_temperature_hot)
        "scalding" -> context.getString(R.string.wash_temperature_scalding)
        "boiling" -> context.getString(R.string.wash_temperature_boiling)
        else -> humanize(value)
    }

    fun autoEmptyInterval(context: Context, value: String): String = when (value) {
        "off" -> context.getString(R.string.auto_empty_off)
        "infrequent" -> context.getString(R.string.auto_empty_infrequent)
        "normal" -> context.getString(R.string.auto_empty_normal)
        "frequent" -> context.getString(R.string.auto_empty_frequent)
        else -> humanize(value)
    }

    fun carpetSensorMode(context: Context, mode: String): String = when (mode) {
        "off" -> context.getString(R.string.carpet_sensor_off)
        "avoid" -> context.getString(R.string.carpet_sensor_avoid)
        "lift" -> context.getString(R.string.carpet_sensor_lift)
        "detach" -> context.getString(R.string.carpet_sensor_detach)
        else -> humanize(mode)
    }

    @StringRes
    fun dockTrigger(capability: String): Int = when (capability) {
        "AutoEmptyDockManualTriggerCapability" -> R.string.dock_action_empty
        "MopDockCleanManualTriggerCapability" -> R.string.dock_action_clean_mop
        else -> R.string.dock_action_dry_mop
    }

    fun error(context: Context, error: ApiError): String = when (error) {
        ApiError.AuthRequired -> context.getString(R.string.error_auth_required)
        ApiError.CleartextNotAllowed -> context.getString(R.string.error_cleartext)
        ApiError.NotSupported -> context.getString(R.string.error_not_supported)
        is ApiError.UntrustedCertificate -> context.getString(R.string.error_untrusted_certificate)
        is ApiError.Network -> context.getString(R.string.error_network)
        is ApiError.UnexpectedResponse -> context.getString(R.string.error_unexpected)
        is ApiError.Http -> if (error.code == 400) {
            context.getString(R.string.error_version_mismatch)
        } else {
            context.getString(R.string.error_http, error.code)
        }
    }

    /** "MopDockMopWashTemperatureControlCapability" → "Mop dock mop wash temperature control". */
    fun capabilityName(capability: String): String =
        humanize(capability.removeSuffix("Capability").replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase())

    /** "water_tank_clean" → "Water tank clean". */
    fun humanize(value: String): String =
        value.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }
}
