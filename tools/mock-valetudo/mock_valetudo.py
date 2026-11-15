#!/usr/bin/env python3
"""
Mock Valetudo server for developing and taking screenshots of Tidytudo without a real robot.

It implements the parts of the Valetudo 2026.08 API that the app uses (see docs/valetudo-api.md)
with a fictional flat, simulates cleaning runs (moving robot, path, battery) and pushes
updates over server-sent events like the real thing. Standard library only.

    python3 tools/mock-valetudo/mock_valetudo.py                 # http://0.0.0.0:8080
    python3 tools/mock-valetudo/mock_valetudo.py --auth demo:demo
    python3 tools/mock-valetudo/mock_valetudo.py --profile basic   # fewer capabilities
    python3 tools/mock-valetudo/mock_valetudo.py --dump-map app/src/test/resources/demo_map.json

The phone reaches the mock via the PC's LAN address, or via `adb reverse tcp:8080 tcp:8080`
and the address `127.0.0.1:8080` in the app.
"""

import argparse
import base64
import json
import struct
import zlib
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PIXEL_SIZE = 5  # cm per map pixel, as on most real robots
PNG_SIGNATURE = bytes([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])

# Fictional flat in map pixels: (name, x0, y0, x1, y1), inclusive.
ROOMS = [
    ("Living room", 500, 500, 619, 589),
    ("Kitchen", 620, 500, 699, 549),
    ("Hallway", 620, 550, 699, 589),
    ("Bedroom", 500, 590, 579, 669),
    ("Bathroom", 580, 590, 629, 669),
    ("Office", 630, 590, 699, 669),
]
# Door gaps in partition walls: (axis, fixed, from, to)
DOORS = [("x", 620, 560, 575), ("y", 590, 535, 550), ("y", 590, 595, 605), ("y", 590, 650, 665), ("y", 550, 650, 665)]
CHARGER = (505, 510)

BASIC_CAPABILITIES = [
    "BasicControlCapability", "LocateCapability", "FanSpeedControlCapability",
    "ConsumableMonitoringCapability", "SpeakerVolumeControlCapability", "DoNotDisturbCapability",
]
FULL_CAPABILITIES = BASIC_CAPABILITIES + [
    "WaterUsageControlCapability", "OperationModeControlCapability", "MapSegmentationCapability",
    "MapSegmentRenameCapability", "ZoneCleaningCapability", "GoToLocationCapability",
    "CombinedVirtualRestrictionsCapability", "SpeakerTestCapability", "VoicePackManagementCapability",
    "TotalStatisticsCapability", "CurrentStatisticsCapability", "PendingMapChangeHandlingCapability",
    "KeyLockCapability", "CarpetModeControlCapability", "ObstacleAvoidanceControlCapability",
    "CarpetSensorModeControlCapability", "AutoEmptyDockManualTriggerCapability",
    "MopDockCleanManualTriggerCapability", "MopDockDryManualTriggerCapability",
    "ObstacleImagesCapability",
    "MopDockMopWashTemperatureControlCapability", "AutoEmptyDockAutoEmptyIntervalControlCapability",
    "MopDockMopAutoDryingControlCapability", "MopExtensionControlCapability", "MopTwistControlCapability",
    "MopExtensionFurnitureLegHandlingControlCapability",
    "MapSnapshotCapability",  # deliberately unknown to the app
]

# Fictional detections in cm, with the colour of their generated "photo".
OBSTACLES = [
    ("a1f0c2de-0001", "Cable (82%)", (3300, 3150), (70, 70, 80)),
    ("a1f0c2de-0002", "Sock (77%)", (2700, 3150), (200, 120, 140)),
    ("a1f0c2de-0003", "Shoe (91%)", (3420, 2620), (150, 100, 60)),
]


def obstacle_png(color, width=320, height=240):
    """A small PNG standing in for a camera photo: a soft floor gradient with a coloured blob."""
    rows = []
    for y in range(height):
        row = bytearray([0])
        for x in range(width):
            in_blob = (x - width / 2) ** 2 / 70 ** 2 + (y - height / 2) ** 2 / 45 ** 2 < 1
            if in_blob:
                row += bytes(color)
            else:
                shade = 150 + (y * 60 // height)
                row += bytes((shade, shade - 10, shade - 25))
        rows.append(bytes(row))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return PNG_SIGNATURE + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b"")


# ---------------------------------------------------------------------------------------------
# Map geometry


def is_partition_wall(x, y):
    """Walls between rooms, with door gaps."""
    for _, x0, y0, x1, y1 in ROOMS:
        on_left = x == x0 and x0 != 500
        on_top = y == y0 and y0 != 500
        if (on_left and y0 <= y <= y1) or (on_top and x0 <= x <= x1):
            for axis, fixed, start, end in DOORS:
                if axis == "x" and x == fixed and start <= y <= end:
                    return False
                if axis == "y" and y == fixed and start <= x <= end:
                    return False
            return True
    return False


def compress(pixels):
    """[(x, y)] sorted by row → Valetudo's [xStart, y, count, …] runs."""
    runs = []
    for x, y in sorted(pixels, key=lambda p: (p[1], p[0])):
        if runs and runs[-1][1] == y and runs[-1][0] + runs[-1][2] == x:
            runs[-1][2] += 1
        else:
            runs.append([x, y, 1])
    return [value for run in runs for value in run]


def layer(kind, pixels, meta):
    xs = [p[0] for p in pixels]
    ys = [p[1] for p in pixels]
    return {
        "__class": "MapLayer",
        "metaData": meta,
        "type": kind,
        "pixels": [],
        "dimensions": {
            "x": {"min": min(xs), "max": max(xs), "mid": (min(xs) + max(xs)) // 2, "avg": sum(xs) // len(xs)},
            "y": {"min": min(ys), "max": max(ys), "mid": (min(ys) + max(ys)) // 2, "avg": sum(ys) // len(ys)},
            "pixelCount": len(pixels),
        },
        "compressedPixels": compress(pixels),
    }


def build_layers(segment_names, active_ids):
    layers = []
    walls = set()
    for x in range(499, 701):
        walls.add((x, 499))
        walls.add((x, 670))
    for y in range(499, 671):
        walls.add((499, y))
        walls.add((700, y))
    for index, (_, x0, y0, x1, y1) in enumerate(ROOMS):
        segment_id = str(index + 1)
        pixels = []
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if is_partition_wall(x, y):
                    walls.add((x, y))
                else:
                    pixels.append((x, y))
        layers.append(layer("segment", pixels, {
            "segmentId": segment_id,
            "active": segment_id in active_ids,
            "source": "regular",
            "name": segment_names[segment_id],
            "material": "tile" if index in (1, 4) else "wood_horizontal",
            "area": len(pixels) * PIXEL_SIZE * PIXEL_SIZE,
        }))
    layers.append(layer("wall", sorted(walls), {"area": len(walls) * PIXEL_SIZE * PIXEL_SIZE}))
    return layers


def cm(point):
    return [point[0] * PIXEL_SIZE, point[1] * PIXEL_SIZE]


def lawnmower(room, step=8):
    """A back-and-forth path through a room, in map pixels."""
    _, x0, y0, x1, y1 = room
    points = []
    for row, y in enumerate(range(y0 + 4, y1 - 3, step)):
        xs = (x0 + 4, x1 - 4) if row % 2 == 0 else (x1 - 4, x0 + 4)
        points += [(xs[0], y), (xs[1], y)]
    # Interpolate so the robot moves smoothly.
    smooth = []
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        steps = max(abs(bx - ax), abs(by - ay)) // 3 or 1
        smooth += [(ax + (bx - ax) * i // steps, ay + (by - ay) * i // steps) for i in range(steps)]
    return smooth


# ---------------------------------------------------------------------------------------------
# Simulated robot


class Robot:
    def __init__(self, profile):
        self.lock = threading.Condition()
        self.capabilities = BASIC_CAPABILITIES if profile == "basic" else FULL_CAPABILITIES
        self.status = "docked"
        self.flag = "none"
        self.battery = 87
        self.presets = {"fan_speed": "medium", "water_grade": "low", "operation_mode": "vacuum"}
        self.position = CHARGER
        self.angle = 0
        self.path = []
        self.route = []
        self.active_segments = set()
        self.segment_names = {str(i + 1): room[0] for i, room in enumerate(ROOMS)}
        self.restrictions = {
            "virtualWalls": [{"points": {"pA": {"x": 3250, "y": 2560}, "pB": {"x": 3450, "y": 2560}}}],
            "restrictedZones": [{"type": "regular", "points": {
                "pA": {"x": 2900, "y": 2800}, "pB": {"x": 3050, "y": 2800},
                "pC": {"x": 3050, "y": 2930}, "pD": {"x": 2900, "y": 2930}}}],
        }
        self.consumables = {("brush", "main"): 14200, ("brush", "side_right"): 6100, ("filter", "main"): 830,
                            ("cleaning", "sensor"): 0}
        self.consumable_max = {("brush", "main"): 18000, ("brush", "side_right"): 12000, ("filter", "main"): 9000,
                               ("cleaning", "sensor"): 1800}
        self.events = [{
            "__class": "ConsumableDepletedValetudoEvent", "metaData": {}, "id": "consumable_depleted_cleaning_sensor",
            "timestamp": "2026-11-14T08:12:00.000Z", "processed": False, "type": "cleaning", "subType": "sensor",
        }]
        self.timers = {}
        for dow, hour in (([1, 2, 3, 4, 5], 8), ([6], 9)):
            timer_id = str(uuid.uuid4())
            self.timers[timer_id] = {
                "__class": "ValetudoTimer", "metaData": {}, "id": timer_id, "enabled": True, "label": "",
                "dow": dow, "hour": hour, "minute": 30, "action": {"type": "full_cleanup", "params": {}},
                "pre_actions": [{"type": "fan_speed_control", "params": {"value": "high"}}],
            }
        self.dnd = {"enabled": True, "start": {"hour": 21, "minute": 0}, "end": {"hour": 6, "minute": 0}}
        self.volume = 60
        self.switches = {"KeyLockCapability": False, "CarpetModeControlCapability": True,
                         "ObstacleAvoidanceControlCapability": True, "ObstacleImagesCapability": True,
                         "MopDockMopAutoDryingControlCapability": True, "MopExtensionControlCapability": True,
                         "MopTwistControlCapability": False, "MopExtensionFurnitureLegHandlingControlCapability": False}
        self.carpet_sensor = "detach"
        self.wash_temperature = "warm"
        self.auto_empty_interval = "normal"
        self.version = 1  # bumped on every change, wakes SSE writers

    # -- state -------------------------------------------------------------------------------

    def changed(self):
        self.version += 1
        self.lock.notify_all()

    def attributes(self):
        battery_flag = "charging" if self.status == "docked" and self.battery < 100 else (
            "charged" if self.status == "docked" else "discharging")
        attrs = [
            {"__class": "StatusStateAttribute", "metaData": {}, "value": self.status, "flag": self.flag},
            {"__class": "BatteryStateAttribute", "metaData": {}, "level": self.battery, "flag": battery_flag},
            {"__class": "PresetSelectionStateAttribute", "metaData": {}, "type": "fan_speed", "value": self.presets["fan_speed"]},
        ]
        if "WaterUsageControlCapability" in self.capabilities:
            attrs += [
                {"__class": "PresetSelectionStateAttribute", "metaData": {}, "type": "water_grade", "value": self.presets["water_grade"]},
                {"__class": "PresetSelectionStateAttribute", "metaData": {}, "type": "operation_mode", "value": self.presets["operation_mode"]},
                {"__class": "AttachmentStateAttribute", "metaData": {}, "type": "mop", "attached": True},
                {"__class": "DockStatusStateAttribute", "metaData": {}, "value": "idle"},
                {"__class": "DockComponentStateAttribute", "metaData": {}, "type": "water_tank_clean", "value": "ok"},
                {"__class": "DockComponentStateAttribute", "metaData": {}, "type": "water_tank_dirty", "value": "ok"},
                {"__class": "DockComponentStateAttribute", "metaData": {}, "type": "dustbag", "value": "ok"},
            ]
        return attrs

    def map(self):
        entities = [
            {"__class": "PointMapEntity", "metaData": {"angle": 0}, "type": "charger_location", "points": cm(CHARGER)},
            {"__class": "PointMapEntity", "metaData": {"angle": self.angle}, "type": "robot_position", "points": cm(self.position)},
            {"__class": "PolygonMapEntity", "metaData": {}, "type": "carpet",
             "points": [2550, 2650, 2900, 2650, 2900, 2850, 2550, 2850]},
        ]
        entities += [
            {"__class": "PointMapEntity", "metaData": {"id": oid, "label": label, "image": f"/data/{oid}.jpg"},
             "type": "obstacle", "points": list(point)}
            for oid, label, point, _ in OBSTACLES
        ]
        if self.path:
            entities.append({"__class": "PathMapEntity", "metaData": {}, "type": "path",
                             "points": [value for point in self.path for value in cm(point)]})
        for wall in self.restrictions["virtualWalls"]:
            points = wall["points"]
            entities.append({"__class": "LineMapEntity", "metaData": {}, "type": "virtual_wall",
                             "points": [points["pA"]["x"], points["pA"]["y"], points["pB"]["x"], points["pB"]["y"]]})
        for zone in self.restrictions["restrictedZones"]:
            points = zone["points"]
            entities.append({"__class": "PolygonMapEntity", "metaData": {},
                             "type": "no_mop_area" if zone["type"] == "mop" else "no_go_area",
                             "points": [v for key in ("pA", "pB", "pC", "pD") for v in (points[key]["x"], points[key]["y"])]})
        return {
            "__class": "ValetudoMap",
            "metaData": {"vendorMapId": 1, "version": 2, "nonce": "00000000-0000-0000-0000-000000000000", "totalLayerArea": 0},
            "size": {"x": 6554, "y": 6554},
            "pixelSize": PIXEL_SIZE,
            "layers": build_layers(self.segment_names, self.active_segments),
            "entities": entities,
        }

    # -- simulation --------------------------------------------------------------------------

    def start(self, segment_ids=None, flag="none"):
        rooms = [ROOMS[int(i) - 1] for i in segment_ids] if segment_ids else ROOMS
        if self.status == "paused" and not segment_ids:
            self.status = "cleaning"
        else:
            self.route = [p for room in rooms for p in lawnmower(room)]
            self.path = []
            self.active_segments = set(segment_ids or [])
            self.status = "cleaning"
            self.flag = flag
        self.changed()

    def tick(self):
        with self.lock:
            if self.status in ("cleaning", "returning", "moving") and self.route:
                for _ in range(3):
                    if not self.route:
                        break
                    nxt = self.route.pop(0)
                    dx, dy = nxt[0] - self.position[0], nxt[1] - self.position[1]
                    if dx or dy:
                        self.angle = {(1, 0): 90, (-1, 0): 270, (0, 1): 180, (0, -1): 0}.get(
                            ((dx > 0) - (dx < 0), (dy > 0) - (dy < 0)), self.angle)
                    self.position = nxt
                    if self.status == "cleaning":
                        self.path.append(nxt)
                if int(time.time()) % 8 == 0:
                    self.battery = max(5, self.battery - 1)
                if not self.route:
                    self.finish_leg()
                self.changed()
            elif self.status == "docked" and self.battery < 100 and int(time.time()) % 3 == 0:
                self.battery += 1
                self.changed()

    def finish_leg(self):
        if self.status == "cleaning":
            self.status, self.flag = "returning", "none"
            self.route = self.straight(self.position, CHARGER)
        elif self.status == "returning":
            self.status, self.flag = "docked", "none"
            self.active_segments = set()
        elif self.status == "moving":
            self.status, self.flag = "idle", "none"

    @staticmethod
    def straight(start, end):
        steps = max(abs(end[0] - start[0]), abs(end[1] - start[1])) // 3 or 1
        return [(start[0] + (end[0] - start[0]) * i // steps, start[1] + (end[1] - start[1]) * i // steps)
                for i in range(1, steps + 1)]


# ---------------------------------------------------------------------------------------------
# HTTP


class Handler(BaseHTTPRequestHandler):
    robot: Robot = None
    auth = None
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("%s %s" % (self.command, self.path))

    # -- helpers -----------------------------------------------------------------------------

    def send_json(self, value, status=200):
        body = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("X-Valetudo-Version", "2026.08.0")
        self.end_headers()
        self.wfile.write(body)

    def send_image(self, obstacle_id):
        color = next((c for oid, _, _, c in OBSTACLES if oid == obstacle_id), None)
        if color is None:
            return self.send_status(404)
        body = obstacle_png(color)
        self.send_response(200)
        self.send_header("Content-Type", "image/png")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_ok(self):
        body = b"OK"
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_status(self, status, text=""):
        body = text.encode()
        self.send_response(status)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def body(self):
        length = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(length) or b"{}")

    def authorized(self):
        if not self.auth:
            return True
        expected = "Basic " + base64.b64encode(self.auth.encode()).decode()
        if self.headers.get("Authorization") == expected:
            return True
        self.send_status(401, "Invalid credentials" if self.headers.get("Authorization") else "No credentials provided")
        return False

    # -- routing -----------------------------------------------------------------------------

    def do_GET(self):
        if not self.authorized():
            return
        path = self.path.split("?")[0].removeprefix("/api/v2/")
        robot = self.robot
        if path in ("robot/state/attributes/sse", "robot/state/map/sse"):
            return self.stream(path)
        with robot.lock:
            routes = {
                "valetudo/version": lambda: {"release": "2026.08.0", "commit": "mock"},
                "valetudo": lambda: {"embedded": True, "systemId": "DemoTidyRobot", "welcomeDialogDismissed": True},
                "robot": lambda: {"manufacturer": "Demo", "modelName": "Tidy 3000", "implementation": "MockValetudoRobot"},
                "robot/properties": lambda: {"firmwareVersion": "1.0-mock"},
                "system/host/info": lambda: {"hostname": "demo", "arch": "arm64", "uptime": 123456,
                                             "mem": {"total": 512 * 1024 * 1024, "free": 300 * 1024 * 1024}},
                "robot/capabilities": lambda: robot.capabilities,
                "robot/state/attributes": robot.attributes,
                "robot/state/map": robot.map,
                "events": lambda: robot.events,
                "timers": lambda: robot.timers,
                "timers/properties": lambda: {"supportedActions": ["full_cleanup", "segment_cleanup"],
                                              "supportedPreActions": ["fan_speed_control", "water_usage_control", "operation_mode_control"]},
            }
            if path in routes:
                return self.send_json(routes[path]())
            if path.startswith("robot/capabilities/"):
                return self.capability_get(path.removeprefix("robot/capabilities/"))
        self.send_status(404)

    def capability_get(self, path):
        robot = self.robot
        name, _, rest = path.partition("/")
        if name not in robot.capabilities:
            return self.send_status(404)
        presets = {
            "FanSpeedControlCapability": ["low", "medium", "high", "max"],
            "WaterUsageControlCapability": ["low", "medium", "high"],
            "OperationModeControlCapability": ["vacuum", "mop", "vacuum_and_mop", "vacuum_then_mop"],
        }
        values = {
            ("MapSegmentationCapability", ""): lambda: [{"__class": "ValetudoMapSegment", "metaData": {}, "id": k, "name": v}
                                                         for k, v in robot.segment_names.items()],
            ("MapSegmentationCapability", "properties"): lambda: {"iterationCount": {"min": 1, "max": 3}, "customOrderSupport": True},
            ("ZoneCleaningCapability", "properties"): lambda: {"zoneCount": {"min": 1, "max": 5}, "iterationCount": {"min": 1, "max": 2}},
            ("CombinedVirtualRestrictionsCapability", ""): lambda: robot.restrictions,
            ("CombinedVirtualRestrictionsCapability", "properties"): lambda: {"supportedRestrictedZoneTypes": ["regular", "mop"]},
            ("ConsumableMonitoringCapability", ""): lambda: [
                {"__class": "ValetudoConsumable", "metaData": {}, "type": t, "subType": s, "remaining": {"value": v, "unit": "minutes"}}
                for (t, s), v in robot.consumables.items()],
            ("ConsumableMonitoringCapability", "properties"): lambda: {"availableConsumables": [
                {"type": t, "subType": s, "unit": "minutes", "maxValue": m} for (t, s), m in robot.consumable_max.items()]},
            ("DoNotDisturbCapability", ""): lambda: {"__class": "ValetudoDNDConfiguration", "metaData": {}, **robot.dnd},
            ("SpeakerVolumeControlCapability", ""): lambda: {"volume": robot.volume},
            ("VoicePackManagementCapability", ""): lambda: {"currentLanguage": "en", "operationStatus": {"type": "idle"}},
            ("TotalStatisticsCapability", ""): lambda: [{"type": "time", "value": 512_000}, {"type": "count", "value": 87},
                                                         {"type": "area", "value": 61_500_000}],
            ("CurrentStatisticsCapability", ""): lambda: [{"type": "time", "value": 2_460}, {"type": "area", "value": 540_000}],
            ("PendingMapChangeHandlingCapability", ""): lambda: {"pending": False},
            ("CarpetSensorModeControlCapability", ""): lambda: {"mode": robot.carpet_sensor},
            ("CarpetSensorModeControlCapability", "properties"): lambda: {"supportedModes": ["avoid", "off", "lift", "detach"]},
            ("MopDockMopWashTemperatureControlCapability", ""): lambda: {"temperature": robot.wash_temperature},
            ("MopDockMopWashTemperatureControlCapability", "properties"): lambda: {"supportedTemperatures": ["cold", "warm", "hot"]},
            ("AutoEmptyDockAutoEmptyIntervalControlCapability", ""): lambda: {"interval": robot.auto_empty_interval},
            ("AutoEmptyDockAutoEmptyIntervalControlCapability", "properties"): lambda: {
                "supportedIntervals": ["off", "infrequent", "normal", "frequent"]},
        }
        if name == "ObstacleImagesCapability" and rest.startswith("img/"):
            return self.send_image(rest.removeprefix("img/"))
        if rest == "presets" and name in presets:
            return self.send_json(presets[name])
        if name in robot.switches and rest == "":
            return self.send_json({"enabled": robot.switches[name]})
        if (name, rest) in values:
            return self.send_json(values[(name, rest)]())
        if rest == "properties":
            return self.send_json({})
        self.send_status(404)

    def do_PUT(self):
        if not self.authorized():
            return
        path = self.path.removeprefix("/api/v2/")
        body = self.body()
        robot = self.robot
        with robot.lock:
            if path.startswith("robot/capabilities/"):
                result = self.capability_put(path.removeprefix("robot/capabilities/"), body)
            elif path.startswith("timers/"):
                result = self.timer_put(path.removeprefix("timers/"), body)
            elif path.startswith("events/") and path.endswith("/interact"):
                event_id = path.removeprefix("events/").removesuffix("/interact")
                for event in robot.events:
                    if event["id"] == event_id:
                        event["processed"] = True
                        if body.get("interaction") == "reset":
                            robot.consumables[(event["type"], event["subType"])] = robot.consumable_max[(event["type"], event["subType"])]
                result = True
            else:
                result = None
            if result is None:
                return self.send_status(404)
            if result is False:
                return self.send_status(400, "Invalid request")
            robot.changed()
        self.send_ok()

    def capability_put(self, path, body):
        robot = self.robot
        name, _, rest = path.partition("/")
        if name not in robot.capabilities:
            return None
        action = body.get("action")
        if name == "BasicControlCapability":
            if action == "start":
                robot.start()
            elif action == "pause" and robot.status in ("cleaning", "returning", "moving"):
                robot.status = "paused"
            elif action == "stop":
                robot.status, robot.flag, robot.route = "idle", "none", []
            elif action == "home":
                robot.status, robot.flag = "returning", "none"
                robot.route = robot.straight(robot.position, CHARGER)
            return True
        if rest == "preset":
            key = {"FanSpeedControlCapability": "fan_speed", "WaterUsageControlCapability": "water_grade",
                   "OperationModeControlCapability": "operation_mode"}[name]
            robot.presets[key] = body["name"]
            return True
        if name == "MapSegmentationCapability":
            robot.start(body["segment_ids"], flag="segment")
            return True
        if name == "MapSegmentRenameCapability":
            robot.segment_names[body["segment_id"]] = body["name"]
            return True
        if name == "ZoneCleaningCapability":
            zone = body["zones"][0]["points"]
            room = ("zone", zone["pA"]["x"] // PIXEL_SIZE, zone["pA"]["y"] // PIXEL_SIZE,
                    zone["pC"]["x"] // PIXEL_SIZE, zone["pC"]["y"] // PIXEL_SIZE)
            robot.route, robot.path, robot.status, robot.flag = lawnmower(room, step=4), [], "cleaning", "zone"
            return True
        if name == "GoToLocationCapability":
            target = (body["coordinates"]["x"] // PIXEL_SIZE, body["coordinates"]["y"] // PIXEL_SIZE)
            robot.route, robot.status, robot.flag = robot.straight(robot.position, target), "moving", "target"
            return True
        if name == "CombinedVirtualRestrictionsCapability":
            robot.restrictions = body
            return True
        if name == "ConsumableMonitoringCapability":
            consumable_type, _, sub_type = rest.partition("/")
            robot.consumables[(consumable_type, sub_type)] = robot.consumable_max[(consumable_type, sub_type)]
            return True
        if name == "DoNotDisturbCapability":
            robot.dnd = {key: body[key] for key in ("enabled", "start", "end")}
            return True
        if name == "SpeakerVolumeControlCapability":
            robot.volume = int(body["value"])
            return True
        if name in robot.switches:
            robot.switches[name] = action == "enable"
            return True
        if name == "CarpetSensorModeControlCapability":
            robot.carpet_sensor = body["mode"]
            return True
        if name == "MopDockMopWashTemperatureControlCapability":
            robot.wash_temperature = body["temperature"]
            return True
        if name == "AutoEmptyDockAutoEmptyIntervalControlCapability":
            robot.auto_empty_interval = body["interval"]
            return True
        return action in ("locate", "trigger", "play_test_sound", "accept", "reject")

    def timer_put(self, path, body):
        robot = self.robot
        timer_id, _, rest = path.partition("/")
        if timer_id not in robot.timers:
            return None
        if rest == "action":
            robot.start()
        else:
            robot.timers[timer_id] = {**robot.timers[timer_id], **body, "id": timer_id}
        return True

    def do_POST(self):
        if not self.authorized():
            return
        if self.path.removeprefix("/api/v2/") != "timers":
            return self.send_status(404)
        body = self.body()
        with self.robot.lock:
            timer_id = str(uuid.uuid4())
            self.robot.timers[timer_id] = {"__class": "ValetudoTimer", "metaData": {}, **body, "id": timer_id}
            self.robot.changed()
        self.send_ok()

    def do_DELETE(self):
        if not self.authorized():
            return
        timer_id = self.path.removeprefix("/api/v2/timers/")
        with self.robot.lock:
            if self.robot.timers.pop(timer_id, None) is None:
                return self.send_status(404)
            self.robot.changed()
        self.send_ok()

    # -- server-sent events --------------------------------------------------------------------

    def stream(self, path):
        is_map = path.endswith("map/sse")
        event = "MapUpdated" if is_map else "StateAttributesUpdated"
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        robot = self.robot
        seen = robot.version
        last_write = time.time()
        try:
            while True:
                with robot.lock:
                    robot.lock.wait_for(lambda: robot.version != seen, timeout=15)
                    changed = robot.version != seen
                    seen = robot.version
                    payload = json.dumps(robot.map() if is_map else robot.attributes()) if changed else None
                if payload is not None:
                    self.wfile.write(f"event: {event}\ndata: {payload}\n\n".encode())
                    last_write = time.time()
                elif time.time() - last_write >= 15:
                    self.wfile.write(b": sse-keep-alive\n")
                    last_write = time.time()
                self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--auth", help="enable basic auth, e.g. demo:demo")
    parser.add_argument("--profile", choices=["full", "basic"], default="full")
    parser.add_argument("--dump-map", metavar="PATH", help="write the demo map as JSON and exit")
    args = parser.parse_args()

    robot = Robot(args.profile)
    if args.dump_map:
        with open(args.dump_map, "w", encoding="utf-8") as out:
            json.dump(robot.map(), out, separators=(",", ":"))
        return

    Handler.robot = robot
    Handler.auth = args.auth

    def simulate():
        while True:
            time.sleep(1)
            robot.tick()

    threading.Thread(target=simulate, daemon=True).start()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    server.daemon_threads = True
    print(f"Mock Valetudo on http://{args.host}:{args.port} (profile {args.profile}, auth {'on' if args.auth else 'off'})")
    server.serve_forever()


if __name__ == "__main__":
    main()
