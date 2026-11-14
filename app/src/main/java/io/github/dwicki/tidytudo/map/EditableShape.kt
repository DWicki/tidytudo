package io.github.dwicki.tidytudo.map

import io.github.dwicki.tidytudo.api.model.Point
import io.github.dwicki.tidytudo.api.model.ZonePoints
import kotlin.math.max
import kotlin.math.min

/** Shapes the user draws on the map: cleaning zones, restricted areas and virtual walls (F-4.2, F-4.4). */
sealed interface EditableShape {
    val id: Int
    val kind: Kind

    enum class Kind { ZONE, NO_GO, NO_MOP, WALL }

    /** Axis-aligned rectangle in centimetres. */
    data class Rect(
        override val id: Int,
        override val kind: Kind,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) : EditableShape {
        fun normalized() = copy(left = min(left, right), right = max(left, right), top = min(top, bottom), bottom = max(top, bottom))

        fun moved(dx: Float, dy: Float) = copy(left = left + dx, right = right + dx, top = top + dy, bottom = bottom + dy)

        /** Clockwise corners from top left, as the API expects. */
        fun toZonePoints(): ZonePoints {
            val rect = normalized()
            return ZonePoints(
                pA = Point(rect.left.toInt(), rect.top.toInt()),
                pB = Point(rect.right.toInt(), rect.top.toInt()),
                pC = Point(rect.right.toInt(), rect.bottom.toInt()),
                pD = Point(rect.left.toInt(), rect.bottom.toInt()),
            )
        }

        companion object {
            /** Bounding rectangle of a polygon from the map or the API (they are always rectangles in Valetudo). */
            fun fromPolygon(id: Int, kind: Kind, points: List<CmPoint>) = Rect(
                id = id,
                kind = kind,
                left = points.minOf { it.x },
                top = points.minOf { it.y },
                right = points.maxOf { it.x },
                bottom = points.maxOf { it.y },
            )
        }
    }

    /** Virtual wall between two points in centimetres. */
    data class Line(override val id: Int, val start: CmPoint, val end: CmPoint) : EditableShape {
        override val kind: Kind get() = Kind.WALL
    }
}
