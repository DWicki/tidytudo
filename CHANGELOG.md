# Changelog

## 1.0.0-beta.3

A small fix on top of beta.2.

- "When a carpet is detected": with four options, the chosen one ("Detach mop") no longer sticks out of the row

## 1.0.0-beta.2

Second beta: a map-first app with one place for the cleaning mode, a new home screen and the robot's state in the top bar.

Cleaning mode and map, after a UX review against the Dreamehome app:

- Mode, suction, water and the mop switches (extend to edges, furniture legs, twist) live in one sheet, opened from a single row that shows the current mode: right under the main button on Home, and on the map. Modes are big tiles that keep their icon when chosen; settings the mode does not use stay visible, greyed, with the reason beside their heading, so the sheet keeps its height and does not jump while switching modes
- Robots with more than four suction levels get wrapping chips instead of a cramped segmented row; "custom" is not offered
- Map: the map gets the screen. Instead of a panel, small round buttons float over it: fit (or clear, passes and rename while choosing rooms) on the left, one play button in the middle (with the number of picked rooms; pause, dock and stop while a job runs), the cleaning mode on the right; time and area show in a small label while cleaning. The state is in the top bar
- Map: picked rooms show their place in the cleaning order on the map; the map is centred and moves aside smoothly when the panel grows; neighbouring rooms never share a colour; the old path fades while choosing or drawing; restriction tools wrap
- Dock buttons (wash, dry, empty) are always visible on Home
- The app opens on the map; App settings has a Start screen choice (Map or Home)
- The robot pill in the top bar shows the state under the name on every tab: an icon and word for what the robot does, and the battery; it tints while cleaning and turns red on an error
- Robot settings grouped into Navigation, Camera, Dock and Robot; new dock settings: mop wash water temperature and how often the bin is emptied into the dock; mop drying moved to Dock

Whole app:

- Every screen uses the home screen's language: round tonal icons, rows on one keyline, list cards, one style for section headings, medium title bars
- Robot tab: badges for open messages and worn parts
- Robot settings: quiet hours as a switch with two time tiles, volume with its value, voice language by name, carpet choice as a segmented control
- Consumables: icon per part, slimmer bars, Reset in a menu; messages: handled ones dimmed; schedules: weekdays as circles, Run now and Delete in a menu, disabled schedules dimmed
- Robots: tapping a robot opens its menu instead of switching on the spot; the active robot is labelled
- Map: neutral rooms until selected, then clearly highlighted; calm room colours; path, obstacles and labels sized for every screen; no "View" chip (tap a room to start choosing); chips scroll to the edge; the panel names the selected rooms
- Home and map share one room selection; the map button on the room card opens the room mode
- Stop and "Reset counter" ask first; Back clears a selection or leaves a map tool instead of closing the app; leaving unsaved no-go changes asks to save or discard
- Presets: long press offers run, rename, replace with the current selection and delete
- "Turn on" in the history turns background recording on; colour roles: mint marks presets only

Home screen redesign:

- Status and controls in one card whose colour follows the state (docked, cleaning, paused, error), with a new robot drawing and one summary line: battery, what the robot or dock is doing, elapsed time and area
- One main button: "Clean all", or "Clean n rooms" as soon as rooms are selected
- Filled room chips with clearer selection; while rooms are selected the card says how many and offers to clear them; passes are a segmented control
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
