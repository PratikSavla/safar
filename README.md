# Safar

Safar is an Android app for Suzuki motorcycles and scooters with a Bluetooth display. It connects to the bike's
display and sends it turns from Google Maps, who is calling, WhatsApp calls and messages, the time and the phone's
battery. It also keeps a record of your riding: trips with their routes, fuel-ups and mileage, service reminders and
where you parked.

## Features

- **Today**: connection status, the odometer, trip meters, fuel bars and gear from the bike, and a garage of shortcuts.
- **Route**: start Google Maps navigation from your saved places. Each turn is shown on the bike's display.
- **Alerts**: phone calls, missed calls, WhatsApp calls and messages, each with its own switch. The bike only gets the
  caller's name and a count, never the message text.
- **Ride mode**: a large, glanceable screen that opens by itself when you start moving.
- **Trips**: every ride with its distance, speeds and a map of the route.
- **Fuel**: log fill-ups, see your mileage and an estimated range, and get a low-fuel reminder.
- **Service**: reminders by distance for oil, chain, air filter, brakes and tyres.
- **Parked**: the place your phone was when the bike switched off.
- **Papers**: your RC, insurance, PUC, licence and service bills as PDFs or photos, with an optional expiry date. Safar
  reminds you 30 and 7 days before a paper runs out, and on the day.
- **Widget**: the connection, odometer, Trip A, range and the next turn on your home screen.
- **Backup**: rides, fuel log, places and settings are saved with Android's own backup and device transfer. You turn
  it on or off in your phone's settings, under Backup. Papers stay on the phone and are not part of that backup.
- **Your data**: in Settings, export everything (rides with their routes, fuel, service, places, papers and settings)
  to one `.zip` file, import such a file to replace what's on the phone, or delete everything and start fresh.

## Bikes

Safar recognises the display by its Bluetooth name and supports:

- V-Strom SX
- Gixxer / Gixxer SF and Gixxer 250 / Gixxer SF 250
- Access 125
- Burgman Street and Burgman Street EX
- Avenis

You don't need a PIN to pair. Safar reconnects on its own when you switch the ignition on.

## Requirements

- Android 8.0 (API 26) or newer, with Bluetooth LE.
- Google Maps, for navigation.
- Permissions: Nearby devices, Notifications, Notification access (for Google Maps turns and WhatsApp), Location
  and Phone. Safar explains each one when you set it up. You can review them at any time in **Settings → All
  permissions**.

## Your data

Everything is stored on the phone: a Room database for trips, fuel, service, places and papers, DataStore for
settings, and the papers' files in the app's own storage. Safar has no account and no server. It only goes online through Google Maps, after you start a route there.

An export is a `.zip` file you can open on any computer:

- `manifest.json`: the format (`safar-backup`), its version, the app version, when it was made and what it holds.
- `data.json`: trips with their GPS tracks, fuel-ups, service items, places, the parked spot, papers, settings and
  the paired bike, as plain JSON.
- `documents/`: each paper's file, unchanged.

Import reads and checks the whole file before it changes anything, and leaves the phone's paired bike as it is.

## Developer mode

To turn on Developer mode, tap the version number in **Settings → About** seven times. Developer mode shows where
each value comes from and how old it is, the Bluetooth link details, the debug log with export, and the icon test.

## Build

You need JDK 25 for the Gradle daemon. The wrapper downloads Gradle 9.8.

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:assembleRelease
```

```bash
./gradlew :protocol:test :app:testDebugUnitTest :bikesim:testDebugUnitTest
```

To sign the release APK so a phone will install it, copy `keystore.properties.example` to `keystore.properties`
and point it at your own keystore. Keep the keystore safe: updates must be signed with the same key.

A release build is minified and ships a baseline profile. To regenerate the profile, run this with a device or
emulator connected:

```bash
./gradlew :app:generateBaselineProfile
```

## Modules

| Module | What it is |
|---|---|
| `:protocol` | Pure Kotlin: frame layout, checksums, GATT UUIDs, the bike model table and Google Maps text parsing. |
| `:app` | The Safar app: Jetpack Compose UI, the Bluetooth session, the phone bridges (Maps, calls, WhatsApp, GPS) and storage. |
| `:bikesim` | BikeSim, a simulated Bluetooth display for a second phone or emulator. See [bikesim/README.md](bikesim/README.md). |
| `:baselineprofile` | Baseline profile generator and startup and scroll benchmarks. |

## Working without the bike

BikeSim advertises as a Suzuki display, accepts Safar's frames and sends cluster readings every few seconds. Run it
on a second phone, or run both apps on two emulators linked by virtual Bluetooth. The unit tests also run the
Bluetooth session against a fake GATT that sends synthetic bike frames.

## License

Copyright (C) 2026 Pratik Savla

Safar is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License
as published by the Free Software Foundation, version 3 of the License. It is distributed in the hope that it will be
useful, but without any warranty; see [LICENSE](LICENSE) for details.

The bundled fonts (Fraunces, IBM Plex Mono and Instrument Sans) are under the SIL Open Font License 1.1; their
licenses are in `app/src/main/assets/licenses/`.

## Trademarks

Safar is an independent project. It is not affiliated with, endorsed by or sponsored by Suzuki Motor Corporation
or any of its subsidiaries. Suzuki, V-Strom, Gixxer, Access, Burgman and Avenis are trademarks of their owners and
are used here only to name the bikes Safar works with. Google Maps is a trademark of Google LLC, and WhatsApp is a
trademark of WhatsApp LLC.
