package io.github.dwicki.tidytudo.map

/**
 * A parsed Valetudo map. Layers are in *pixel* coordinates, entities in *centimetres*
 * (pixel × [pixelSize]); see docs/valetudo-api.md §Map.
 */
data class RobotMap(
    val pixelSize: Int,
    val floor: PixelLayer?,
    val walls: PixelLayer?,
    val segments: List<SegmentLayer>,
    val robot: Pose?,
    val charger: Pose?,
    val goToTarget: CmPoint?,
    val paths: List<List<CmPoint>>,
    val predictedPath: List<CmPoint>,
    val virtualWalls: List<CmLine>,
    val noGoAreas: List<List<CmPoint>>,
    val noMopAreas: List<List<CmPoint>>,
    val activeZones: List<List<CmPoint>>,
    val carpets: List<List<CmPoint>>,
    val obstacles: List<Obstacle>,
) {
    /** Bounding box of all pixel layers, in pixels; null for an empty map (e.g. before the first mapping run). */
    val bounds: PixelBounds? by lazy {
        val layers = listOfNotNull(floor, walls) + segments.map { it.pixels }
        layers.filter { it.count > 0 }.map { it.bounds }.reduceOrNull(PixelBounds::union)
    }

    val isEmpty: Boolean get() = bounds == null

    fun segmentAt(pixelX: Int, pixelY: Int): SegmentLayer? = segments.firstOrNull { it.pixels.contains(pixelX, pixelY) }
}

data class CmPoint(val x: Float, val y: Float)

data class CmLine(val start: CmPoint, val end: CmPoint)

/**
 * Something the robot's camera recognised. [id] fetches the photo via `ObstacleImagesCapability`;
 * [label] is the robot's text, e.g. "Fabric (70%)". Both are absent on robots without a camera.
 */
data class Obstacle(val position: CmPoint, val id: String?, val label: String?)

/** Position in cm plus heading in degrees (0 = up on the map, clockwise, as in Valetudo's web UI). */
data class Pose(val position: CmPoint, val angle: Float)

data class SegmentLayer(
    val id: String,
    val name: String?,
    val active: Boolean,
    val material: String?,
    val pixels: PixelLayer,
)

data class PixelBounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
    val width: Int get() = maxX - minX + 1
    val height: Int get() = maxY - minY + 1

    fun union(other: PixelBounds) = PixelBounds(
        minOf(minX, other.minX), minOf(minY, other.minY), maxOf(maxX, other.maxX), maxOf(maxY, other.maxY),
    )
}

/**
 * Pixels stored as horizontal runs, which is how Valetudo sends them and keeps memory low:
 * run i covers x in `[runX[i], runX[i] + runLength[i])` on row `runY[i]`.
 */
class PixelLayer(
    private val runX: IntArray,
    private val runY: IntArray,
    private val runLength: IntArray,
) {
    val runCount: Int get() = runX.size
    val count: Int = runLength.sum()

    val bounds: PixelBounds by lazy {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (index in runX.indices) {
            minX = minOf(minX, runX[index])
            maxX = maxOf(maxX, runX[index] + runLength[index] - 1)
            minY = minOf(minY, runY[index])
            maxY = maxOf(maxY, runY[index])
        }
        PixelBounds(minX, minY, maxX, maxY)
    }

    /** Centre of mass in pixels, used to place room labels. */
    val centroid: Pair<Float, Float> by lazy {
        var sumX = 0.0
        var sumY = 0.0
        for (index in runX.indices) {
            val length = runLength[index]
            sumX += length * (runX[index] + (length - 1) / 2.0)
            sumY += length.toDouble() * runY[index]
        }
        val total = count.coerceAtLeast(1)
        (sumX / total).toFloat() to (sumY / total).toFloat()
    }

    fun forEachRun(action: (x: Int, y: Int, length: Int) -> Unit) {
        for (index in runX.indices) action(runX[index], runY[index], runLength[index])
    }

    fun contains(pixelX: Int, pixelY: Int): Boolean {
        for (index in runX.indices) {
            if (runY[index] == pixelY && pixelX >= runX[index] && pixelX < runX[index] + runLength[index]) return true
        }
        return false
    }
}
