# Valetudo API: what Tidytudo relies on

This document describes, in our own words, the parts of the Valetudo HTTP API that Tidytudo uses.
It is not a copy of the upstream documentation. The source of truth is the OpenAPI document that
every Valetudo instance embeds in its Swagger UI (`/swagger/`), plus the backend source on GitHub
(`Hypfer/Valetudo`, `backend/lib`).

## How this was verified

| What | Source | Version |
|---|---|---|
| All endpoint paths and request bodies | OpenAPI document embedded in `/swagger/swagger-ui-init.js` of a live robot (140 paths) | Valetudo 2026.08.0 |
| Response shapes | Read-only `GET` requests against a live robot (Dreame X40 Ultra) | Valetudo 2026.08.0 |
| SSE event names, keep-alive, client limits | `backend/lib/webserver/RobotRouter.js`, `middlewares/sse/*` | `master`, November 2026 |
| mDNS service types and TXT records | `backend/lib/NetworkAdvertisementManager.js` | `master`, November 2026 |
| Map pixel compression | `backend/lib/entities/map/MapLayer.js` + live map | 2026.08.0 |
| Enum values (status, dock, attachments, errors, map entities) | `backend/lib/entities/**` | `master`, November 2026 |

Nothing was sent to the robot that changes its state during this research. Write endpoints are
described from the OpenAPI schema and are marked 🔬 in the requirements until verified on hardware.

Corrections to the assumptions in the original task description:

- There is **no `/openapi` endpoint**. The spec is only embedded in the Swagger UI script.
- Valetudo advertises **two** mDNS services, not one (see [Discovery](#discovery)).
- There are **three** SSE streams; the map stream sends the **complete** map on every change, not a diff.
- Statistics, consumables, schedules and settings are not under one "settings" resource; each is
  its own capability or router.

## Basics

- Base path: `/api/v2`. All bodies are JSON (`application/json`).
- Every response carries `X-Valetudo-Version` and `X-Valetudo-Commit-Id` headers. The app reads
  the version from `GET /api/v2/valetudo/version` → `{"release": "2026.08.0", "commit": "…"}`.
- Write requests use `PUT` (and `POST`/`DELETE` for timers). Success is `200 OK` with body
  `OK` or JSON; validation errors are `400`; an unsupported capability path is `404`.
- Objects carry a `__class` discriminator and a free-form `metaData` object. The app uses
  `__class` to decode polymorphic lists (state attributes, map entities, events) and ignores
  unknown classes instead of failing.

### Authentication

Optional HTTP basic auth, configured by the user in Valetudo. When enabled, every route except
the internal `/_killswitch` requires it. A missing or wrong header yields `401` with the body
`No credentials provided` or `Invalid credentials`. The app treats `401` as "credentials needed"
and asks the user. The config endpoint `/api/v2/valetudo/config/interfaces/http/auth/basic`
exists, but the app never reads or writes it (it would expose the username to the app for no gain).

## Discovery

`NetworkAdvertisementManager` publishes, via Bonjour/mDNS, when network advertisement is enabled:

| Service type | Instance name | Purpose |
|---|---|---|
| `_valetudo._tcp` | `Valetudo <SystemId>` | What the app browses for |
| `_http._tcp` | `Valetudo <SystemId> Web` | Generic browsers; ignored by the app |

- Host: `valetudo-<systemid lowercased>.local`, port: the web server port (normally 80).
- TXT records: `id` (human-readable system id, e.g. three CamelCase words), `model`,
  `manufacturer`, `version`, and `name` if the user set a friendly name.
- `GET /api/v2/networkadvertisement/properties` → `{"port": 80, "zeroconfHostname": "…local"}`.

The app uses Android's `NsdManager` (no extra library). Because mDNS is often blocked on guest
networks or across VLANs, manual entry of an address is always offered.

## Robot identity

| Endpoint | Example response (shortened) |
|---|---|
| `GET /robot` | `{"manufacturer":"Dreame","modelName":"X40 Ultra","modelDetails":{"supportedAttachments":["mop"]},"implementation":"…ValetudoRobot"}` |
| `GET /robot/properties` | `{"firmwareVersion":"1760"}` |
| `GET /valetudo` | `{"embedded":true,"systemId":"…","welcomeDialogDismissed":true}` |
| `GET /valetudo/version` | `{"release":"2026.08.0","commit":"…"}` |
| `GET /system/host/info` | hostname, arch, uptime, memory, load (shown on the info screen) |

`systemId` is stable per robot and is what the app uses to recognise a robot whose IP changed.

## Capabilities

`GET /robot/capabilities` returns a flat list of class names, for example
`["BasicControlCapability", "FanSpeedControlCapability", …]`. Each capability has its own
routes under `/robot/capabilities/<Name>` and usually `/<Name>/properties` describing limits.
The app keeps a registry that maps known names to features; unknown names are listed on the
robot-info screen as "supported by the robot, not yet by this app".

Capabilities the app uses, with the exact request shapes:

| Capability | Read | Write |
|---|---|---|
| `BasicControlCapability` | – | `PUT {"action": "start" \| "stop" \| "pause" \| "home"}` |
| `LocateCapability` | – | `PUT {"action": "locate"}` |
| `FanSpeedControlCapability` | `GET …/presets` → `["low","medium","high","max"]` | `PUT …/preset {"name": "high"}` |
| `WaterUsageControlCapability` | `GET …/presets` | `PUT …/preset {"name": "…"}` |
| `OperationModeControlCapability` | `GET …/presets` → e.g. `vacuum`, `mop`, `vacuum_and_mop`, `vacuum_then_mop` | `PUT …/preset {"name": "…"}` |
| `MapSegmentationCapability` | `GET` → `[{"id":"1","name":"…","material":"tile"}]`; `…/properties` → `{"iterationCount":{"min":1,"max":4},"customOrderSupport":true}` | `PUT {"action":"start_segment_action","segment_ids":["1","4"],"iterations":1,"customOrder":false}` |
| `MapSegmentRenameCapability` | – | `PUT {"action":"rename_segment","segment_id":"1","name":"…"}` |
| `ZoneCleaningCapability` | `…/properties` → `{"zoneCount":{"min":1,"max":4},"iterationCount":{…}}` | `PUT {"action":"clean","zones":[{"points":{"pA":{x,y},"pB":…,"pC":…,"pD":…}}],"iterations":1}` |
| `GoToLocationCapability` | – | `PUT {"action":"goto","coordinates":{"x":…,"y":…}}` |
| `CombinedVirtualRestrictionsCapability` | `GET` → `{"virtualWalls":[…],"restrictedZones":[…]}`; `…/properties` → `{"supportedRestrictedZoneTypes":["regular","mop"]}` | `PUT` the full object (replaces all walls and zones) |
| `ConsumableMonitoringCapability` | `GET` → `[{"type":"brush","subType":"main","remaining":{"value":3840,"unit":"minutes"}}]`; `…/properties` → `availableConsumables` with `maxValue` | `PUT …/<type>/<subType> {"action":"reset"}` (or `…/<type>` when there is no sub type) |
| `DoNotDisturbCapability` | `GET` → `{"enabled":true,"start":{"hour":20,"minute":15},"end":{…}}` | `PUT` same shape |
| `SpeakerVolumeControlCapability` | `GET` → `{"volume":24}` | `PUT {"action":"set_volume","value":40}` |
| `SpeakerTestCapability` | – | `PUT {"action":"play_test_sound"}` |
| `VoicePackManagementCapability` | `GET` → `{"currentLanguage":"en","operationStatus":{"type":"idle"}}` | `PUT {"action":"download","url":…,"language":…,"hash":…}` |
| `TotalStatisticsCapability`, `CurrentStatisticsCapability` | `GET` → data points `{"type":"time"\|"area"\|"count","value":…}`; time in seconds, area in cm² | – |
| `ObstacleImagesCapability` | `GET` → `{"enabled":bool}`; `…/properties` → `{"fileFormat":"jpg","dimensions":{"width":672,"height":504}}`; `GET …/img/<obstacle id>` → the photo (`image/jpeg` or `image/png`) | `PUT {"action":"enable" \| "disable"}` |
| `PendingMapChangeHandlingCapability` | `GET` → `{"pending":false}` | `PUT {"action":"accept" \| "reject"}` |
| `KeyLockCapability`, `CarpetModeControlCapability` and every other `…ControlCapability` that is a simple switch | `GET` → `{"enabled":bool}` | `PUT {"action":"enable" \| "disable"}` |
| `AutoEmptyDockManualTriggerCapability`, `MopDockCleanManualTriggerCapability`, `MopDockDryManualTriggerCapability` | – | `PUT {"action":"trigger"}` |

Times in the DND configuration and in timers are **UTC**. The app converts to and from local time.

## State

`GET /robot/state/attributes` returns a list of attributes; the important ones:

| `__class` | Fields | Values |
|---|---|---|
| `StatusStateAttribute` | `value`, `flag`, `error` (only when `value == "error"`) | value: `error`, `docked`, `idle`, `returning`, `cleaning`, `paused`, `manual_control`, `moving`; flag: `none`, `zone`, `segment`, `spot`, `target`, `resumable`, `mapping` |
| `BatteryStateAttribute` | `level` (0–100), `flag` | `none`, `charging`, `discharging`, `charged` |
| `PresetSelectionStateAttribute` | `type`, `value` | type: `fan_speed`, `water_grade`, `operation_mode` |
| `AttachmentStateAttribute` | `type`, `attached` | type: `dustbin`, `watertank`, `mop` |
| `DockStatusStateAttribute` | `value` | `error`, `idle`, `pause`, `emptying`, `cleaning`, `drying` |
| `DockComponentStateAttribute` | `type`, `value` | type: `water_tank_clean`, `water_tank_dirty`, `dustbag`, `detergent`; value: `ok`, `missing`, `empty`, `full`, `unknown` |

The `error` object of the status has `severity.kind` (`transient`, `permanent`, …),
`severity.level` (`info`, `warning`, `error`, `catastrophic`, …), `subsystem` (`core`, `power`,
`sensors`, `motors`, `navigation`, `attachments`, `dock`, …), a human-readable `message` and a
`vendorErrorCode`. The app shows the message as is and adds a translated subsystem label.

`GET /robot/state` returns the attributes **and** the map in one object; the app does not use it
because it is large.

## Live updates (server-sent events)

| Stream | Event name | Data |
|---|---|---|
| `GET /robot/state/attributes/sse` | `StateAttributesUpdated` | full attribute list (as above) |
| `GET /robot/state/map/sse` | `MapUpdated` | full map JSON |
| `GET /robot/state/sse` | `StateUpdated` | full state (attributes + map); not used |

- Each stream sends `: sse-keep-alive` comment lines periodically, so a read timeout of about
  a minute detects dead connections.
- Each hub allows **at most 5 clients**; a sixth connection closes the oldest. The app therefore
  keeps one attribute stream per robot and opens the map stream only while the map is visible.
- The attribute stream delivered the current state right after connecting in our test, but this is
  not guaranteed by the code; the app always does one `GET` first.

## Map

`GET /robot/state/map` (about 100 kB for a flat with 11 rooms) returns a `ValetudoMap`:

```text
{ "__class": "ValetudoMap",
  "metaData": { "version": 2, "nonce": "…", "totalLayerArea": … },
  "size": { "x": 6554, "y": 6554 },       // in cm
  "pixelSize": 5,                          // cm per pixel
  "layers": [ MapLayer … ],
  "entities": [ MapEntity … ] }
```

**Layers** (`type`: `floor`, `wall`, `segment`) are pixel sets in *pixel* coordinates:

- `compressedPixels` is a run-length list of triples `[xStart, y, count, xStart, y, count, …]`,
  each meaning `count` consecutive pixels from `xStart` along the row `y`. We verified that the sum
  of the counts equals `dimensions.pixelCount`.
- Older Valetudo versions send a plain `pixels` list `[x, y, x, y, …]` instead. The parser accepts
  both and prefers `compressedPixels` when it is not empty.
- `dimensions` gives the bounding box (`x.min/max`, `y.min/max`) and `pixelCount`.
- Segment layers have `metaData.segmentId`, `name`, `active`, `material` (`generic`, `tile`,
  `wood`, `wood_horizontal`, `wood_vertical`, `carpet`, …) and `area` (cm²).

**Entities** use *centimetre* coordinates (pixel × `pixelSize`), as flat lists `[x, y, x, y, …]`:

| `__class` | `type` values |
|---|---|
| `PointMapEntity` | `robot_position`, `charger_location` (with `metaData.angle` in degrees, 0 = up, clockwise), `go_to_target`, `obstacle` (with `metaData.id`, `metaData.label` such as `"Fabric (70%)"` and the robot-internal `metaData.image` path) |
| `PathMapEntity` | `path`, `predicted_path` |
| `LineMapEntity` | `virtual_wall`, `threshold`, `curtain` |
| `PolygonMapEntity` | `active_zone`, `no_go_area`, `no_mop_area`, `carpet`, `ramp` |

Commands that take coordinates (zones, go-to, virtual restrictions) also use centimetres.

## Events

`GET /events` lists `ValetudoEvent`s (`__class`, `id`, `timestamp`, `processed`, plus type-specific
fields). Known classes: `ConsumableDepletedValetudoEvent` (`type`, `subType`),
`DustBinFullValetudoEvent`, `ErrorStateValetudoEvent` (`message`), `MissingResourceValetudoEvent`,
`MopAttachmentReminderValetudoEvent`, `PendingMapChangeValetudoEvent`,
`ValetudoUpdatedValetudoEvent`, `ValetudoRuntimeErrorValetudoEvent`.

`PUT /events/<id>/interact {"interaction": "ok" | "yes" | "no" | "reset"}`: `ok` dismisses,
`reset` resets the depleted consumable, `yes`/`no` answer a pending map change.

Events are only available while polling; there is no events SSE stream. The app polls them
together with the attribute stream reconnects and on screen resume.

## Timers

- `GET /timers` → object keyed by id. `GET /timers/properties` →
  `{"supportedActions":["full_cleanup","segment_cleanup"],"supportedPreActions":["fan_speed_control","water_usage_control","operation_mode_control"]}`.
- `POST /timers` creates, `PUT /timers/<id>` replaces, `DELETE /timers/<id>` removes,
  `PUT /timers/<id>/action {"action":"execute_now"}` runs one now.
- Timer body: `enabled`, optional `label` (≤ 24 chars), `dow` (0 = Sunday … 6), `hour`, `minute`
  (**UTC**), `action {type, params {segment_ids, iterations, custom_order}}`, `pre_actions`
  (`[{type, params {value}}]`).

## Version support

| Feature | Requirement |
|---|---|
| Everything above | Tested with 2026.08.0 |
| Map with `compressedPixels` | Current releases; older ones send `pixels`, which the parser also reads (not tested on an old robot) |
| Timer `pre_actions`, `label` | Recent releases; the app hides these fields when the robot omits them |
| `OperationModeControlCapability` | Only robots that report it |

The app requires `/api/v2` (Valetudo 2021 and later). If `GET /api/v2/valetudo/version` fails
with `404`, the robot is shown as "Valetudo version too old". If a request for a known
capability returns `400` with a schema error, the app shows "this robot's Valetudo version
handles this differently" instead of a generic failure.

## History

Valetudo has no endpoint for past cleaning runs. Statistics (`CurrentStatisticsCapability`)
describe only the last run, and the map holds only the current state. Tidytudo therefore records
runs itself (see arc42 §6.4): status changes from the attribute stream mark start and end, and at
the end it saves the statistics, the map JSON and the obstacle photos from
`ObstacleImagesCapability/img/<id>`. On the tested robot the map listed 45 obstacles, each with an
id, a label with confidence and a photo of 672 × 504 pixels.

## Not used

MQTT, the updater, Wi-Fi configuration, the log endpoints, duststreaming, manual control,
NTP and the Valetudo customisation settings are out of scope. The web UI covers them.
