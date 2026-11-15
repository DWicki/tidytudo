package io.github.dwicki.tidytudo.ui.map

import androidx.lifecycle.viewModelScope
import io.github.dwicki.tidytudo.AppContainer
import io.github.dwicki.tidytudo.api.ApiResult
import io.github.dwicki.tidytudo.api.Capabilities
import io.github.dwicki.tidytudo.api.LiveState
import io.github.dwicki.tidytudo.api.getOrNull
import io.github.dwicki.tidytudo.api.model.Point
import io.github.dwicki.tidytudo.api.model.RestrictedZone
import io.github.dwicki.tidytudo.api.model.SegmentCleaningProperties
import io.github.dwicki.tidytudo.api.model.VirtualRestrictions
import io.github.dwicki.tidytudo.api.model.VirtualWall
import io.github.dwicki.tidytudo.api.model.ZoneCleaningProperties
import io.github.dwicki.tidytudo.data.Robot
import io.github.dwicki.tidytudo.map.CmPoint
import io.github.dwicki.tidytudo.map.EditableShape
import io.github.dwicki.tidytudo.map.RobotMap
import io.github.dwicki.tidytudo.ui.CommandViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MapMode { VIEW, ROOMS, ZONES, GO_TO, RESTRICTIONS }

data class MapUiState(
    val mode: MapMode = MapMode.VIEW,
    val selectedSegments: List<String> = emptyList(),
    val zones: List<EditableShape.Rect> = emptyList(),
    val restrictions: List<EditableShape> = emptyList(),
    val restrictionsDirty: Boolean = false,
    val goToTarget: CmPoint? = null,
    val iterations: Int = 1,
    val zoneProperties: ZoneCleaningProperties = ZoneCleaningProperties(),
    val segmentProperties: SegmentCleaningProperties = SegmentCleaningProperties(),
    val supportedRestrictionTypes: List<String> = listOf("regular"),
    val sending: Boolean = false,
    val canRename: Boolean = false,
)

/** Map interactions (F-3, F-4.1 – F-4.4): selection and drawing state, plus the commands that use them. */
class MapViewModel(private val container: AppContainer, private val robot: Robot) : CommandViewModel() {

    private val session = container.session(robot)
    val map: StateFlow<RobotMap?> = session.map
    val live: StateFlow<LiveState> = session.live

    private val state = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = state.asStateFlow()

    private var nextShapeId = 1

    fun availableModes(capabilities: Set<String>): List<MapMode> = buildList {
        add(MapMode.VIEW)
        if (Capabilities.SEGMENT_CLEANING in capabilities) add(MapMode.ROOMS)
        if (Capabilities.ZONE_CLEANING in capabilities) add(MapMode.ZONES)
        if (Capabilities.GO_TO_LOCATION in capabilities) add(MapMode.GO_TO)
        if (Capabilities.VIRTUAL_RESTRICTIONS in capabilities) add(MapMode.RESTRICTIONS)
    }

    fun setMode(mode: MapMode) {
        state.update { it.copy(mode = mode) }
        when (mode) {
            MapMode.ROOMS -> {
                loadSegmentProperties()
                state.update { it.copy(canRename = Capabilities.SEGMENT_RENAME in live.value.capabilities) }
            }
            MapMode.ZONES -> loadZoneProperties()
            MapMode.RESTRICTIONS -> loadRestrictions()
            else -> Unit
        }
    }

    fun onTap(point: CmPoint, segmentId: String?) {
        when (state.value.mode) {
            MapMode.ROOMS -> segmentId?.let(::toggleSegment)
            MapMode.GO_TO -> state.update { it.copy(goToTarget = point) }
            else -> Unit
        }
    }

    private fun toggleSegment(id: String) = state.update { current ->
        val selected = if (id in current.selectedSegments) current.selectedSegments - id else current.selectedSegments + id
        current.copy(selectedSegments = selected)
    }

    fun setIterations(count: Int) = state.update { it.copy(iterations = count) }

    /** Adds a 1 m × 1 m shape at [center] (the visible map centre), where the user then drags it. */
    fun addShape(kind: EditableShape.Kind, center: CmPoint) {
        val id = nextShapeId++
        val half = DEFAULT_SHAPE_CM / 2
        if (kind == EditableShape.Kind.WALL) {
            val wall = EditableShape.Line(id, CmPoint(center.x - half, center.y), CmPoint(center.x + half, center.y))
            state.update { it.copy(restrictions = it.restrictions + wall, restrictionsDirty = true) }
            return
        }
        val rect = EditableShape.Rect(id, kind, center.x - half, center.y - half, center.x + half, center.y + half)
        state.update { current ->
            if (kind == EditableShape.Kind.ZONE) {
                if (current.zones.size >= current.zoneProperties.zoneCount.max) current else current.copy(zones = current.zones + rect)
            } else {
                current.copy(restrictions = current.restrictions + rect, restrictionsDirty = true)
            }
        }
    }

    fun updateShape(shape: EditableShape) = state.update { current ->
        when {
            shape is EditableShape.Rect && shape.kind == EditableShape.Kind.ZONE ->
                current.copy(zones = current.zones.map { if (it.id == shape.id) shape else it })
            else -> current.copy(
                restrictions = current.restrictions.map { if (it.id == shape.id) shape else it },
                restrictionsDirty = true,
            )
        }
    }

    fun removeLastShape() = state.update { current ->
        if (current.mode == MapMode.ZONES) {
            current.copy(zones = current.zones.dropLast(1))
        } else {
            current.copy(restrictions = current.restrictions.dropLast(1), restrictionsDirty = true)
        }
    }

    fun cleanSelectedRooms() {
        val current = state.value
        if (current.selectedSegments.isEmpty()) return
        send(clear = { it.copy(selectedSegments = emptyList()) }) {
            session.client.cleanSegments(current.selectedSegments, current.iterations, current.segmentProperties.customOrderSupport)
        }
    }

    /** Renames the one selected room; the next map update shows the new name. */
    fun renameSelectedRoom(name: String) {
        val id = state.value.selectedSegments.singleOrNull() ?: return
        send(startsJob = false, clear = { it }) { session.client.renameSegment(id, name.trim()) }
    }

    fun cleanZones() {
        val current = state.value
        if (current.zones.isEmpty()) return
        send(clear = { it.copy(zones = emptyList()) }) {
            session.client.cleanZones(current.zones.map { it.toZonePoints() }, current.iterations)
        }
    }

    fun goToTarget() {
        val target = state.value.goToTarget ?: return
        send(clear = { it.copy(goToTarget = null) }) { session.client.goTo(Point(target.x.toInt(), target.y.toInt())) }
    }

    fun saveRestrictions() {
        val restrictions = state.value.restrictions
        val body = VirtualRestrictions(
            virtualWalls = restrictions.filterIsInstance<EditableShape.Line>().map { wall ->
                VirtualWall(VirtualWall.WallPoints(wall.start.toPoint(), wall.end.toPoint()))
            },
            restrictedZones = restrictions.filterIsInstance<EditableShape.Rect>().map { rect ->
                RestrictedZone(type = if (rect.kind == EditableShape.Kind.NO_MOP) "mop" else "regular", points = rect.toZonePoints())
            },
        )
        send(startsJob = false, clear = { it.copy(restrictionsDirty = false) }) { session.client.setVirtualRestrictions(body) }
    }

    private fun send(
        startsJob: Boolean = true,
        clear: (MapUiState) -> MapUiState,
        block: suspend () -> ApiResult<Unit>,
    ) {
        state.update { it.copy(sending = true) }
        launchCommand(
            block = { block().also { state.update { it.copy(sending = false) } } },
            onSuccess = {
                state.update(clear)
                if (startsJob) container.followJob(robot)
            },
        )
    }

    private fun loadSegmentProperties() {
        viewModelScope.launch {
            session.client.segmentProperties().getOrNull()?.let { props -> state.update { it.copy(segmentProperties = props) } }
        }
    }

    private fun loadZoneProperties() {
        viewModelScope.launch {
            session.client.zoneProperties().getOrNull()?.let { props -> state.update { it.copy(zoneProperties = props) } }
        }
    }

    private fun loadRestrictions() {
        if (state.value.restrictionsDirty) return
        viewModelScope.launch {
            val types = session.client.restrictionProperties().getOrNull()?.supportedRestrictedZoneTypes
            val current = session.client.virtualRestrictions().getOrNull() ?: return@launch
            val shapes = current.virtualWalls.map { wall ->
                EditableShape.Line(nextShapeId++, wall.points.pA.toCm(), wall.points.pB.toCm())
            } + current.restrictedZones.map { zone ->
                val kind = if (zone.type == "mop") EditableShape.Kind.NO_MOP else EditableShape.Kind.NO_GO
                val points = listOf(zone.points.pA, zone.points.pB, zone.points.pC, zone.points.pD).map { it.toCm() }
                EditableShape.Rect.fromPolygon(nextShapeId++, kind, points)
            }
            state.update { it.copy(restrictions = shapes, restrictionsDirty = false, supportedRestrictionTypes = types ?: it.supportedRestrictionTypes) }
        }
    }

    private companion object {
        const val DEFAULT_SHAPE_CM = 100f
    }
}

private fun CmPoint.toPoint() = Point(x.toInt(), y.toInt())

private fun Point.toCm() = CmPoint(x.toFloat(), y.toFloat())
