# Changelog

## Unreleased

Home screen:

- One main button: "Clean all", or "Clean n rooms" as soon as rooms are selected; the second start button in the room card is gone
- Elapsed time and cleaned area while the robot works
- What the dock is doing (emptying, washing, drying) after a run
- Mode, suction and water options wrap instead of scrolling sideways; the modes sit right below the rooms
- A short hint explains tapping and long-pressing rooms; "Clear selection" undoes a selection
- Without presets, a slim row explains them instead of a large empty tile

Found while testing on a real robot:

- A status kept from an earlier connection is marked "Updating…" until the robot answers, instead of looking current (a finished robot could still read "Cleaning" after reopening the app)
- Renamed rooms show their new name at once on the home screen
- Dialogs with a text field move above the keyboard; before, the keyboard hid Save and Cancel
- "Resume" no longer wraps onto two lines; while a job runs, the dock button is round
- Locate confirms that the robot plays a sound
- Debug builds install next to the release app as "Tidytudo Debug"

## 1.0.0-beta.1

First public beta. Reading data is verified on a real robot; commands that move the robot are so
far verified against the mock server only (see docs/requirements/req42.md).

- Find Valetudo robots on the network (mDNS, subnet scan as fallback) or add them by address; basic auth and self-signed certificates with fingerprint confirmation
- Live status over server-sent events: status, battery, errors in plain language, dock and attachments
- Start, pause, stop, dock, locate; fan speed, water usage and operation mode
- Map with rooms, path, robot, dock, restrictions and obstacles; pinch-zoom; room, zone and go-to cleaning; edit no-go areas, no-mop areas and virtual walls; rename rooms
- Presets for rooms and modes, also as launcher shortcuts
- Cleaning history with map, statistics, rooms and obstacle photos, recorded live or afterwards from what the robot still holds
- Consumables, robot messages, schedules in local time, quiet hours, volume and the robot's on/off settings
- Home-screen widget, quick-settings tile, cleaning notification, optional background alerts
- Several robots; English and German; light and dark theme
