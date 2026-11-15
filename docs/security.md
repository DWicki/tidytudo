# Security and privacy

Tidytudo controls a device inside your home and stores maps and photos of it, so it is built to
keep all of that between your phone and your robot.

## What leaves the phone

Only requests to the robots you added, on the addresses you added (or that discovery found on
your own network). There is no backend, no analytics, no crash reporter, no ads and no update
check. The app has no dependency that phones home: AndroidX, Jetpack Compose, OkHttp,
kotlinx.coroutines and kotlinx.serialization only.

The subnet scan (F-1.9) sends one `GET /api/v2/valetudo/version` to each address of the phone's
own private IPv4 network, only when you tap **Scan the network**, and never beyond a /24.

## Transport

| Case | Rule |
|---|---|
| `http://` | Allowed only when the *resolved* address is loopback, link-local, private (RFC 1918), carrier-grade NAT (100.64/10, used by some VPNs) or IPv6 unique-local. Checked on the socket after DNS, so a public name pointing outside the LAN is refused too. Android's network security config cannot express IP ranges, which is why cleartext is permitted there and enforced in code (ADR-7). |
| `https://` with a trusted certificate | Normal validation. User-installed CAs are trusted for people who run a private CA. |
| `https://` with a self-signed certificate | Refused until the user confirms the SHA-256 fingerprint shown by the app. The fingerprint is then pinned for this robot; a changed certificate is refused again. Hostname verification is skipped only for that exact pinned certificate, because self-signed robot certificates rarely name every address the phone uses. |

## Stored data

| Data | Where | Protection |
|---|---|---|
| Robot list (addresses, names, presets, pins) | `files/robots.json` | Private app storage |
| Valetudo basic-auth credentials | `shared_prefs/vault.xml` | AES-256-GCM with a non-exportable Android Keystore key; never logged |
| Cleaning history: maps and obstacle photos | `files/history/` | Private app storage, at most 50 runs per robot, deleted with the robot |
| Settings | `shared_prefs/settings.xml` | Private app storage |

Backups and device-to-device transfer are disabled for everything (`allowBackup="false"` and
`data_extraction_rules.xml`), because the files describe the inside of a home.

## Logs

All log output goes through a redactor that removes URLs, IP addresses, host names, basic-auth
headers and password fields. Map coordinates are never logged. Release builds log warnings and
errors only, and nothing is written to files.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Talk to the robots on the local network |
| `ACCESS_NETWORK_STATE` | Know the phone's own subnet for the optional network scan |
| `POST_NOTIFICATIONS` | Cleaning progress and error/maintenance alerts; asked when the first cleaning is started or background recording is switched on |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the local connection while a cleaning runs or background recording is on; Android stops background connections otherwise. There is no push service for self-hosted robots, hence "special use" |
| `RECEIVE_BOOT_COMPLETED` | Restart background recording after a reboot, only if the user switched it on |

No location, camera, storage, contacts or Bluetooth permission is used. mDNS runs through
Android's `NsdManager`, which needs no multicast permission.

## Reporting a problem

Please open an issue on GitHub. For something sensitive, ask there for a private contact first.
