# Safar BikeSim

BikeSim turns an Android phone into a simulated Suzuki Bluetooth cluster. Safar, on a second phone, finds it,
connects to it and exchanges frames with it over real BLE, so you can work on Safar without the bike.

BikeSim is a separate app (`app.safar.bikesim`, module `:bikesim`). It uses the `:protocol` module for frame
layout, checksums and UUIDs and shares no code with `:app`.

## What it simulates

- **Advertising**: connectable advertising with service `FFF0`; the device name is in the scan response. Android
  advertises the adapter name, so while the simulator is on the phone's Bluetooth name is set to the simulated
  cluster name (default `SBM01A1B2C3D`). Switching the simulator off restores the old name. The old name is saved
  until it is restored: when Bluetooth is off at that moment, or the app was killed while advertising, BikeSim
  restores it as soon as Bluetooth is on again with BikeSim open.
- **GATT server**:
  - `0000fff0-…` with `FFF1` (write with response, app to bike) and `FFF2` (notify, bike to app, with CCCD `2902`).
  - Device Information `0x180A` with synthetic values: `2A29` manufacturer, `2A24` model, `2A25` serial,
    `2A26` firmware, `2A27` hardware, `2A28` software and `2A50` PnP ID.
  - Every write to `FFF1` is answered with `GATT_SUCCESS`.
  - Notify state is kept per device. The server sees every LE link of the phone (a watch, a second central), but
    another link becomes the central only when it enables notify or no central is connected, and it never turns
    the central's notify off. Frames go to every device that enabled notify.
- **Cluster frames**: after the central enables notify on `FFF2`, BikeSim sends a cluster frame (`'7'`) every 5 s.
  Byte 23 carries the gear, byte 25 the engine temperature in °C and bytes 26-27 `FF FF`. The checksum mode follows the
  name: character [1] `A` gives the inverted checksum, anything else the sum checksum.

## Run it next to Safar

You need two devices with Bluetooth LE: a second phone, or two emulators. The simulator side needs BLE peripheral
mode; when it is missing, the Events list says "this device cannot advertise".

- **Second phone**: BikeSim on one phone, Safar on the other, within Bluetooth range.
- **Emulators**: emulator system images with API 33 or later have virtual Bluetooth, shared by the emulators running
  on the same computer. Run BikeSim in one emulator and Safar in another. A physical phone cannot reach the
  emulators' virtual Bluetooth, so do not mix the two.

1. Install BikeSim on the simulator device: `./gradlew :bikesim:installDebug`.
2. Install Safar on the other device: `./gradlew :app:installDebug`.
3. Open BikeSim, grant the Bluetooth permission (Android 12 and later) and turn Bluetooth on if the banner asks.
4. Pick a name and switch **Advertise** on. The screen stays on while the simulator is on (no foreground service).
5. In Safar, scan for the bike. The simulated name appears in the list; connect to it.
6. BikeSim shows the central's address, the MTU and "Notify on FFF2: enabled". Cluster frames start, and every
   frame Safar writes appears in the **Received** list.

With both devices connected to one computer, pick the target of each install with `ANDROID_SERIAL`, for example
`ANDROID_SERIAL=<serial> ./gradlew :bikesim:installDebug` (`adb devices` lists the serials).

## Controls

| Control | What it does |
|---|---|
| Advertise | Switches the simulator on (GATT server + advertising) or off (both stop, the link drops). |
| Advertised name | Presets `SBM01A1B2C3D` (V-Strom SX), `SBM11A1B2C3D` (Gixxer), `SBS21A1B2C3D` (Avenis), `SAS01A1B2C3D` (Access 125, inverted checksum), or type any name. The line below says whether Safar's scan filter (12 characters, contains `SA` or `SB`) matches it. The name is locked while advertising. |
| Link | Advertising state, connected central, MTU, notify state, frames sent and the last frame sent in hex. |
| Speed | Slider, 0-180 km/h. |
| Ride | Every second, odometer, trip A and trip B grow by the distance ridden at the current speed. |
| Fuel bars | 0-6. With **Drain fuel while riding** on, one bar drops every 5 km of riding. |
| Gear | `N`, `1`-`6`, sent in byte 23. |
| Engine °C (byte 25) | Engine temperature in whole °C, 0-254; 255 (`0xFF`) means no reading. |
| Send bad framing | One cluster frame with a broken start byte and end byte (and non-digit speed and odometer). Safar rejects it at the framing check. |
| Send bad fields | One cluster frame with valid framing and checksum but non-digit speed and odometer. It passes Safar's framing check and fails in the field decoder. |
| Send wrong checksum | One correct cluster frame whose checksum matches neither checksum mode. |
| Drop link | Asks the stack to drop the link to the central, as when the bike goes out of range. Events shows "Central … disconnected" when the link is gone (advertising then starts again), or an error when it is still up 5 s later. |
| Pause frames (bike off) | Stops the 5 s cluster frames; the link stays up. Press again to resume. |
| Events | The last few simulator events: connections, MTU, notify, errors. |
| Received | The last 200 frames Safar wrote, newest first: time, type, decoded fields, checksum result and hex. **Copy as hex** puts them on the clipboard, oldest first, one line per frame. |

Decoded fields per frame type:

- `'6'` rider: name and `F` (first connection) or `R` (reconnect).
- `'3'` status: battery band, charging, speed alert, signal bars, clock, messages cleared, missed calls cleared.
- `'1'` navigation: icon code and maneuver name, distance to the turn, ETA, remaining distance, status, active or ended.
- `'2'` call: name, source (`N` phone, `W` WhatsApp), ringing or answered.
- `'4'` missed call: count, name, source.
- `'5'` message: count, sender, source.

## Build and test

```sh
./gradlew :bikesim:testDebugUnitTest :bikesim:assembleDebug
```

The frame builder, the received-frame describer, the ride integration and the received log are plain Kotlin in
`app.safar.bikesim.sim`, covered by the JVM tests in `src/test`.
