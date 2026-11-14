package io.github.dwicki.tidytudo.map

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Turns a `ValetudoMap` JSON document into a [RobotMap].
 *
 * Tolerant by design (Q-7): unknown layer or entity types are skipped, a broken entity does not
 * spoil the rest, and both pixel encodings are accepted (current `compressedPixels` runs and the
 * older flat `pixels` list).
 */
object MapParser {

    fun parse(json: JsonElement): RobotMap {
        val root = json as? JsonObject ?: throw IllegalArgumentException("Map is not a JSON object")
        val pixelSize = (root["pixelSize"] as? JsonPrimitive)?.intOrNull ?: DEFAULT_PIXEL_SIZE
        val layers = (root["layers"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val entities = (root["entities"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val builder = EntityCollector()
        entities.forEach { entity -> runCatching { builder.add(entity) } }

        return RobotMap(
            pixelSize = pixelSize,
            floor = layers.firstOrNull { it.text("type") == "floor" }?.let(::parsePixels),
            walls = layers.firstOrNull { it.text("type") == "wall" }?.let(::parsePixels),
            segments = layers.filter { it.text("type") == "segment" }.mapNotNull(::parseSegment),
            robot = builder.robot,
            charger = builder.charger,
            goToTarget = builder.goToTarget,
            paths = builder.paths,
            predictedPath = builder.predictedPath,
            virtualWalls = builder.virtualWalls,
            noGoAreas = builder.noGoAreas,
            noMopAreas = builder.noMopAreas,
            activeZones = builder.activeZones,
            carpets = builder.carpets,
            obstacles = builder.obstacles,
        )
    }

    private fun parseSegment(layer: JsonObject): SegmentLayer? {
        val meta = layer["metaData"] as? JsonObject ?: return null
        val id = meta.text("segmentId") ?: return null
        return SegmentLayer(
            id = id,
            name = meta.text("name")?.takeIf { it.isNotBlank() },
            active = (meta["active"] as? JsonPrimitive)?.booleanOrNull ?: false,
            material = meta.text("material"),
            pixels = parsePixels(layer),
        )
    }

    internal fun parsePixels(layer: JsonObject): PixelLayer {
        val compressed = (layer["compressedPixels"] as? JsonArray)?.toIntArray()
        if (compressed != null && compressed.size >= RUN_FIELDS) return decodeRuns(compressed)
        val plain = (layer["pixels"] as? JsonArray)?.toIntArray() ?: IntArray(0)
        return decodePlain(plain)
    }

    /** `[xStart, y, count, …]`; a trailing incomplete triple is ignored. */
    private fun decodeRuns(values: IntArray): PixelLayer {
        val runs = values.size / RUN_FIELDS
        val xs = IntArray(runs) { values[it * RUN_FIELDS] }
        val ys = IntArray(runs) { values[it * RUN_FIELDS + 1] }
        val lengths = IntArray(runs) { values[it * RUN_FIELDS + 2].coerceAtLeast(0) }
        return PixelLayer(xs, ys, lengths)
    }

    /** `[x, y, x, y, …]`, each pixel becomes a run of one; neighbours are merged to save memory. */
    private fun decodePlain(values: IntArray): PixelLayer {
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        val lengths = ArrayList<Int>()
        for (index in 0 until values.size / 2) {
            val x = values[index * 2]
            val y = values[index * 2 + 1]
            val last = xs.lastIndex
            if (last >= 0 && ys[last] == y && xs[last] + lengths[last] == x) {
                lengths[last] = lengths[last] + 1
            } else {
                xs += x
                ys += y
                lengths += 1
            }
        }
        return PixelLayer(xs.toIntArray(), ys.toIntArray(), lengths.toIntArray())
    }

    private class EntityCollector {
        var robot: Pose? = null
        var charger: Pose? = null
        var goToTarget: CmPoint? = null
        val paths = mutableListOf<List<CmPoint>>()
        var predictedPath: List<CmPoint> = emptyList()
        val virtualWalls = mutableListOf<CmLine>()
        val noGoAreas = mutableListOf<List<CmPoint>>()
        val noMopAreas = mutableListOf<List<CmPoint>>()
        val activeZones = mutableListOf<List<CmPoint>>()
        val carpets = mutableListOf<List<CmPoint>>()
        val obstacles = mutableListOf<Obstacle>()

        fun add(entity: JsonObject) {
            val points = (entity["points"] as? JsonArray)?.toFloatArray()?.toPoints() ?: return
            if (points.isEmpty()) return
            when (entity.text("type")) {
                "robot_position" -> robot = Pose(points.first(), angleOf(entity))
                "charger_location" -> charger = Pose(points.first(), angleOf(entity))
                "go_to_target" -> goToTarget = points.first()
                "obstacle" -> obstacles += obstacleOf(entity, points.first())
                "path" -> paths += points
                "predicted_path" -> predictedPath = points
                "virtual_wall" -> if (points.size >= 2) virtualWalls += CmLine(points[0], points[1])
                "no_go_area" -> noGoAreas += points
                "no_mop_area" -> noMopAreas += points
                "active_zone" -> activeZones += points
                "carpet" -> carpets += points
            }
        }

        private fun obstacleOf(entity: JsonObject, position: CmPoint): Obstacle {
            val meta = entity["metaData"] as? JsonObject
            return Obstacle(position, meta?.text("id"), meta?.text("label"))
        }

        private fun angleOf(entity: JsonObject): Float =
            ((entity["metaData"] as? JsonObject)?.get("angle") as? JsonPrimitive)?.floatOrNull ?: 0f
    }

    private const val RUN_FIELDS = 3
    private const val DEFAULT_PIXEL_SIZE = 5
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonArray.toIntArray(): IntArray = IntArray(size) { (this[it] as? JsonPrimitive)?.intOrNull ?: 0 }

private fun JsonArray.toFloatArray(): FloatArray = FloatArray(size) { (this[it] as? JsonPrimitive)?.floatOrNull ?: 0f }

private fun FloatArray.toPoints(): List<CmPoint> = List(size / 2) { CmPoint(this[it * 2], this[it * 2 + 1]) }
