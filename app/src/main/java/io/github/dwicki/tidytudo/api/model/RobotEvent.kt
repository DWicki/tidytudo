package io.github.dwicki.tidytudo.api.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** One entry of `GET /events`. The [kind] decides which interactions the UI offers. */
data class RobotEvent(
    val id: String,
    val kind: Kind,
    val timestamp: String,
    val processed: Boolean,
    /** Consumable type / sub type for [Kind.CONSUMABLE_DEPLETED]; otherwise null. */
    val consumableType: String? = null,
    val consumableSubType: String? = null,
    /** Free text from the robot for errors; shown as is. */
    val message: String? = null,
    /** The raw `__class`, shown for [Kind.OTHER] so new event types are still visible. */
    val className: String,
) {
    enum class Kind {
        CONSUMABLE_DEPLETED,
        DUST_BIN_FULL,
        ERROR_STATE,
        MISSING_RESOURCE,
        MOP_ATTACHMENT_REMINDER,
        PENDING_MAP_CHANGE,
        VALETUDO_UPDATED,
        RUNTIME_ERROR,
        OTHER,
    }

    /** Interactions accepted by `PUT /events/{id}/interact`, see docs/valetudo-api.md. */
    val interactions: List<String>
        get() = when (kind) {
            Kind.CONSUMABLE_DEPLETED -> listOf(INTERACTION_RESET, INTERACTION_OK)
            Kind.PENDING_MAP_CHANGE -> listOf(INTERACTION_YES, INTERACTION_NO)
            else -> listOf(INTERACTION_OK)
        }

    companion object {
        const val INTERACTION_OK = "ok"
        const val INTERACTION_RESET = "reset"
        const val INTERACTION_YES = "yes"
        const val INTERACTION_NO = "no"

        private val kindsByClass = mapOf(
            "ConsumableDepletedValetudoEvent" to Kind.CONSUMABLE_DEPLETED,
            "DustBinFullValetudoEvent" to Kind.DUST_BIN_FULL,
            "ErrorStateValetudoEvent" to Kind.ERROR_STATE,
            "MissingResourceValetudoEvent" to Kind.MISSING_RESOURCE,
            "MopAttachmentReminderValetudoEvent" to Kind.MOP_ATTACHMENT_REMINDER,
            "PendingMapChangeValetudoEvent" to Kind.PENDING_MAP_CHANGE,
            "ValetudoUpdatedValetudoEvent" to Kind.VALETUDO_UPDATED,
            "ValetudoRuntimeErrorValetudoEvent" to Kind.RUNTIME_ERROR,
        )

        fun parseList(json: JsonElement): List<RobotEvent> =
            (json as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull(::parse)

        private fun parse(item: JsonObject): RobotEvent? {
            val id = item.string("id") ?: return null
            val className = item.string("__class") ?: "ValetudoEvent"
            val kind = kindsByClass[className] ?: Kind.OTHER
            return RobotEvent(
                id = id,
                kind = kind,
                timestamp = item.string("timestamp").orEmpty(),
                processed = item.bool("processed") ?: false,
                consumableType = item.string("type").takeIf { kind == Kind.CONSUMABLE_DEPLETED },
                consumableSubType = item.string("subType").takeIf { kind == Kind.CONSUMABLE_DEPLETED },
                message = item.string("message"),
                className = className,
            )
        }
    }
}
