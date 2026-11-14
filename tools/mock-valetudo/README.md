# Mock Valetudo

A small stand-in for a Valetudo robot, for development, UI tests by hand and screenshots.
It needs only Python 3.9+ and serves a fictional flat, so no real floor plan ends up in the repo.

```bash
python3 tools/mock-valetudo/mock_valetudo.py              # port 8080, all capabilities
python3 tools/mock-valetudo/mock_valetudo.py --auth demo:demo
python3 tools/mock-valetudo/mock_valetudo.py --profile basic --port 8081   # a second, simpler robot
```

Reach it from the phone either via the PC's LAN address (`192.168.x.y:8080` in "Enter address"),
or over USB with `adb reverse tcp:8080 tcp:8080` and the address `127.0.0.1:8080`.

What it simulates: start/pause/stop/dock, room and zone cleaning with a moving robot and path,
go-to, battery drain and charging, presets, consumables and reset, events, timers (create, edit,
delete, run), do-not-disturb, volume, switches, carpet sensor mode and virtual restrictions.
Live updates go out over the same SSE streams the real robot uses.

Not simulated: mDNS announcements (use manual entry), HTTPS, map changes other than the robot
and restrictions, errors. The full profile includes one capability the app does not know on
purpose, to show the "not yet supported" list.

`--dump-map PATH` writes the demo map, which is the parser test fixture
`app/src/test/resources/demo_map.json`.
