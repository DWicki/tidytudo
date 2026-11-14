package io.github.dwicki.tidytudo.api.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decodes the polymorphic state attribute list (`GET /robot/state/attributes` and the
 * `StateAttributesUpdated` SSE event). Attributes are told apart by `__class`; unknown classes
 * and malformed entries are skipped instead of failing the whole state.
 */
object StateParser {

    fun parse(attributes: JsonElement): RobotState {
        val list = attributes as? JsonArray ?: return RobotState()
        return list.filterIsInstance<JsonObject>().fold(RobotState()) { state, attribute ->
            runCatching { apply(state, attribute) }.getOrDefault(state)
        }
    }

    private fun apply(state: RobotState, attribute: JsonObject): RobotState = when (attribute.string("__class")) {
        "StatusStateAttribute" -> state.copy(status = parseStatus(attribute))
        "BatteryStateAttribute" -> state.copy(
            battery = Battery(level = attribute.int("level") ?: 0, flag = attribute.string("flag") ?: "none"),
        )
        "PresetSelectionStateAttribute" -> parsePreset(state, attribute)
        "AttachmentStateAttribute" -> state.copy(
            attachments = state.attachments + Attachment(
                type = attribute.string("type") ?: "unknown",
                attached = attribute.bool("attached") ?: false,
            ),
        )
        "DockStatusStateAttribute" -> state.copy(dockStatus = attribute.string("value"))
        "DockComponentStateAttribute" -> state.copy(
            dockComponents = state.dockComponents + DockComponent(
                type = attribute.string("type") ?: "unknown",
                value = attribute.string("value") ?: "unknown",
            ),
        )
        else -> state
    }

    private fun parseStatus(attribute: JsonObject): Status {
        val value = StatusValue.fromApi(attribute.string("value"))
        val errorObject = attribute["error"] as? JsonObject
        val error = errorObject?.let {
            val severity = it["severity"] as? JsonObject
            RobotError(
                message = it.string("message").orEmpty(),
                subsystem = it.string("subsystem") ?: "unknown",
                severityLevel = severity?.string("level") ?: "unknown",
                vendorErrorCode = it.string("vendorErrorCode"),
            )
        }
        return Status(value = value, flag = attribute.string("flag") ?: "none", error = error)
    }

    private fun parsePreset(state: RobotState, attribute: JsonObject): RobotState {
        val type = attribute.string("type") ?: return state
        val value = attribute.string("value") ?: return state
        return state.copy(presets = state.presets + (type to value))
    }
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.int(key: String): Int? = runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()

internal fun JsonObject.bool(key: String): Boolean? = runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()
