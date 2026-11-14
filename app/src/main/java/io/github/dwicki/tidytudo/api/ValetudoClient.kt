package io.github.dwicki.tidytudo.api

import io.github.dwicki.tidytudo.api.model.CarpetSensorMode
import io.github.dwicki.tidytudo.api.model.CarpetSensorModeProperties
import io.github.dwicki.tidytudo.api.model.Consumable
import io.github.dwicki.tidytudo.api.model.ConsumableProperties
import io.github.dwicki.tidytudo.api.model.DataPoint
import io.github.dwicki.tidytudo.api.model.DoNotDisturb
import io.github.dwicki.tidytudo.api.model.HostInfo
import io.github.dwicki.tidytudo.api.model.MapSegment
import io.github.dwicki.tidytudo.api.model.PendingMapChange
import io.github.dwicki.tidytudo.api.model.Point
import io.github.dwicki.tidytudo.api.model.RobotEvent
import io.github.dwicki.tidytudo.api.model.RobotInfo
import io.github.dwicki.tidytudo.api.model.RobotProperties
import io.github.dwicki.tidytudo.api.model.RobotState
import io.github.dwicki.tidytudo.api.model.RestrictionProperties
import io.github.dwicki.tidytudo.api.model.SegmentCleaningProperties
import io.github.dwicki.tidytudo.api.model.SpeakerVolume
import io.github.dwicki.tidytudo.api.model.StateParser
import io.github.dwicki.tidytudo.api.model.TimerProperties
import io.github.dwicki.tidytudo.api.model.Toggle
import io.github.dwicki.tidytudo.api.model.ValetudoInfo
import io.github.dwicki.tidytudo.api.model.ValetudoTimer
import io.github.dwicki.tidytudo.api.model.ValetudoVersion
import io.github.dwicki.tidytudo.api.model.VirtualRestrictions
import io.github.dwicki.tidytudo.api.model.VoicePackState
import io.github.dwicki.tidytudo.api.model.ZoneCleaningProperties
import io.github.dwicki.tidytudo.api.model.ZonePoints
import io.github.dwicki.tidytudo.map.MapParser
import io.github.dwicki.tidytudo.map.RobotMap
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

/**
 * Typed access to one robot's `/api/v2`. Every method is a thin mapping of one endpoint listed in
 * docs/valetudo-api.md; transport, status codes and errors are handled by [HttpTransport].
 */
class ValetudoClient(baseUrl: String, httpClient: OkHttpClient) {

    private val transport = HttpTransport(baseUrl, httpClient)
    val sse = SseConnector(transport)

    // Identity

    suspend fun version(): ApiResult<ValetudoVersion> = transport.get("valetudo/version").decode()
    suspend fun valetudoInfo(): ApiResult<ValetudoInfo> = transport.get("valetudo").decode()
    suspend fun robotInfo(): ApiResult<RobotInfo> = transport.get("robot").decode()
    suspend fun robotProperties(): ApiResult<RobotProperties> = transport.get("robot/properties").decode()
    suspend fun hostInfo(): ApiResult<HostInfo> = transport.get("system/host/info").decode()
    suspend fun capabilities(): ApiResult<List<String>> = transport.get("robot/capabilities").decode()

    // State and map

    suspend fun state(): ApiResult<RobotState> = transport.get("robot/state/attributes").map(StateParser::parse)
    suspend fun map(): ApiResult<RobotMap> = transport.get("robot/state/map").parse(MapParser::parse)

    /** The map as sent by the robot, for storing it with a cleaning session. */
    suspend fun mapJson(): ApiResult<JsonElement> = transport.get("robot/state/map")

    suspend fun obstacleImage(obstacleId: String): ApiResult<ByteArray> =
        transport.getBytes("${capabilityPath(Capabilities.OBSTACLE_IMAGES)}/img/$obstacleId")

    // Basic control

    suspend fun basicControl(action: String) = putAction(Capabilities.BASIC_CONTROL, action)
    suspend fun locate() = putAction(Capabilities.LOCATE, "locate")
    suspend fun trigger(capability: String) = putAction(capability, "trigger")

    suspend fun presets(capability: String): ApiResult<List<String>> =
        transport.get("${capabilityPath(capability)}/presets").decode()

    suspend fun setPreset(capability: String, name: String): ApiResult<Unit> =
        transport.put("${capabilityPath(capability)}/preset", buildJsonObject { put("name", name) })

    // Rooms, zones, go-to

    suspend fun segments(): ApiResult<List<MapSegment>> = getCapability(Capabilities.SEGMENT_CLEANING)
    suspend fun segmentProperties(): ApiResult<SegmentCleaningProperties> =
        getProperties(Capabilities.SEGMENT_CLEANING)

    suspend fun cleanSegments(ids: List<String>, iterations: Int, customOrder: Boolean): ApiResult<Unit> =
        putCapability(Capabilities.SEGMENT_CLEANING, buildJsonObject {
            put("action", "start_segment_action")
            putJsonArray("segment_ids") { ids.forEach { add(it) } }
            put("iterations", iterations)
            put("customOrder", customOrder)
        })

    suspend fun renameSegment(id: String, name: String): ApiResult<Unit> =
        putCapability(Capabilities.SEGMENT_RENAME, buildJsonObject {
            put("action", "rename_segment")
            put("segment_id", id)
            put("name", name)
        })

    suspend fun zoneProperties(): ApiResult<ZoneCleaningProperties> = getProperties(Capabilities.ZONE_CLEANING)

    suspend fun cleanZones(zones: List<ZonePoints>, iterations: Int): ApiResult<Unit> =
        putCapability(Capabilities.ZONE_CLEANING, buildJsonObject {
            put("action", "clean")
            put("zones", JsonCodec.json.encodeToJsonElement(zones.map { mapOf("points" to it) }))
            put("iterations", iterations)
        })

    suspend fun goTo(point: Point): ApiResult<Unit> =
        putCapability(Capabilities.GO_TO_LOCATION, buildJsonObject {
            put("action", "goto")
            putJsonObject("coordinates") {
                put("x", point.x)
                put("y", point.y)
            }
        })

    suspend fun virtualRestrictions(): ApiResult<VirtualRestrictions> = getCapability(Capabilities.VIRTUAL_RESTRICTIONS)
    suspend fun restrictionProperties(): ApiResult<RestrictionProperties> =
        getProperties(Capabilities.VIRTUAL_RESTRICTIONS)

    suspend fun setVirtualRestrictions(restrictions: VirtualRestrictions): ApiResult<Unit> =
        putCapability(Capabilities.VIRTUAL_RESTRICTIONS, JsonCodec.json.encodeToJsonElement(restrictions).jsonObject)

    // Maintenance

    suspend fun consumables(): ApiResult<List<Consumable>> = getCapability(Capabilities.CONSUMABLES)
    suspend fun consumableProperties(): ApiResult<ConsumableProperties> = getProperties(Capabilities.CONSUMABLES)

    suspend fun resetConsumable(type: String, subType: String?): ApiResult<Unit> {
        val suffix = if (subType.isNullOrEmpty() || subType == "none") type else "$type/$subType"
        return transport.put("${capabilityPath(Capabilities.CONSUMABLES)}/$suffix", action("reset"))
    }

    suspend fun totalStatistics(): ApiResult<List<DataPoint>> = getCapability(Capabilities.TOTAL_STATISTICS)
    suspend fun currentStatistics(): ApiResult<List<DataPoint>> = getCapability(Capabilities.CURRENT_STATISTICS)

    suspend fun pendingMapChange(): ApiResult<PendingMapChange> = getCapability(Capabilities.PENDING_MAP_CHANGE)
    suspend fun answerPendingMapChange(accept: Boolean) =
        putAction(Capabilities.PENDING_MAP_CHANGE, if (accept) "accept" else "reject")

    suspend fun events(): ApiResult<List<RobotEvent>> = transport.get("events").map(RobotEvent::parseList)

    suspend fun interact(eventId: String, interaction: String): ApiResult<Unit> =
        transport.put("events/$eventId/interact", buildJsonObject { put("interaction", interaction) })

    // Settings

    suspend fun doNotDisturb(): ApiResult<DoNotDisturb> = getCapability(Capabilities.DO_NOT_DISTURB)
    suspend fun setDoNotDisturb(value: DoNotDisturb): ApiResult<Unit> =
        putCapability(Capabilities.DO_NOT_DISTURB, JsonCodec.json.encodeToJsonElement(value).jsonObject)

    suspend fun volume(): ApiResult<SpeakerVolume> = getCapability(Capabilities.SPEAKER_VOLUME)
    suspend fun setVolume(percent: Int): ApiResult<Unit> =
        putCapability(Capabilities.SPEAKER_VOLUME, buildJsonObject {
            put("action", "set_volume")
            put("value", percent)
        })

    suspend fun playTestSound() = putAction(Capabilities.SPEAKER_TEST, "play_test_sound")
    suspend fun voicePack(): ApiResult<VoicePackState> = getCapability(Capabilities.VOICE_PACK)

    suspend fun toggle(capability: String): ApiResult<Toggle> = getCapability(capability)
    suspend fun setToggle(capability: String, enabled: Boolean) =
        putAction(capability, if (enabled) "enable" else "disable")

    suspend fun carpetSensorMode(): ApiResult<CarpetSensorMode> = getCapability(Capabilities.CARPET_SENSOR_MODE)
    suspend fun carpetSensorModeProperties(): ApiResult<CarpetSensorModeProperties> =
        getProperties(Capabilities.CARPET_SENSOR_MODE)

    suspend fun setCarpetSensorMode(mode: String): ApiResult<Unit> =
        putCapability(Capabilities.CARPET_SENSOR_MODE, buildJsonObject { put("mode", mode) })

    // Timers

    suspend fun timers(): ApiResult<List<ValetudoTimer>> =
        transport.get("timers").decode<Map<String, ValetudoTimer>>().map { it.values.toList() }

    suspend fun timerProperties(): ApiResult<TimerProperties> = transport.get("timers/properties").decode()

    suspend fun saveTimer(timer: ValetudoTimer): ApiResult<Unit> {
        val body = JsonCodec.json.encodeToJsonElement(timer).jsonObject
        return if (timer.id.isEmpty()) transport.post("timers", body) else transport.put("timers/${timer.id}", body)
    }

    suspend fun deleteTimer(id: String): ApiResult<Unit> = transport.delete("timers/$id")
    suspend fun runTimerNow(id: String): ApiResult<Unit> = transport.put("timers/$id/action", action("execute_now"))

    // Helpers

    private suspend inline fun <reified T> getCapability(capability: String): ApiResult<T> =
        transport.get(capabilityPath(capability)).decode()

    private suspend inline fun <reified T> getProperties(capability: String): ApiResult<T> =
        transport.get("${capabilityPath(capability)}/properties").decode()

    private suspend fun putCapability(capability: String, body: JsonObject): ApiResult<Unit> =
        transport.put(capabilityPath(capability), body)

    private suspend fun putAction(capability: String, action: String): ApiResult<Unit> =
        putCapability(capability, action(action))

    private fun action(name: String) = buildJsonObject { put("action", name) }

    private fun capabilityPath(capability: String) = "robot/capabilities/$capability"
}

private inline fun <reified T> ApiResult<JsonElement>.decode(): ApiResult<T> =
    parse { JsonCodec.json.decodeFromJsonElement<T>(it) }

private inline fun <T> ApiResult<JsonElement>.parse(block: (JsonElement) -> T): ApiResult<T> = when (this) {
    is ApiResult.Success -> runCatching { block(value) }.fold(
        onSuccess = { ApiResult.Success(it) },
        onFailure = { ApiResult.Failure(ApiError.UnexpectedResponse(it)) },
    )
    is ApiResult.Failure -> this
}
