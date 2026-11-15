# Changelog

## Unreleased

Home screen redesign:

- Status and controls in one card whose colour follows the state (docked, cleaning, paused, error), with a new robot drawing and one summary line: battery, what the robot or dock is doing, elapsed time and area
- One main button: "Clean all", or "Clean n rooms" as soon as rooms are selected
- Filled room chips with clearer selection; while rooms are selected the card says how many and offers to clear them; passes are a segmented control
- Mode, suction and water as a settings list (name left, value right) that opens a picker, so long values such as "Vacuum and mop" fit; settings that do nothing in the current mode are hidden
- Presets as pills right below the status, hidden while there are none
- The dock collapses to one line while all is well and opens by itself when something needs attention
- The robot name in the top bar is a pill that opens the switcher; the bar tints when content scrolls under it
- Calmer type scale, consistent corners and spacing; the hint for room chips disappears after first use
- The Tidytudo colours are the default; wallpaper colours can be turned on in App settings

Found while testing on a real robot:

- A status kept from an earlier connection is marked "Updating…" until the robot answers, instead of looking current (a finished robot could still read "Cleaning" after reopening the app)
- Renamed rooms show their new name at once on the home screen
- Dialogs with a text field move above the keyboard; before, the keyboard hid Save and Cancel
- When adding a robot by address, the Connect button stays above the keyboard on phones whose keyboard opens slowly
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
