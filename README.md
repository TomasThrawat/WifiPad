# WifiPad

A PS4-style virtual gamepad that connects over WiFi (UDP) instead of Bluetooth,
for TVs that only support wireless debugging (ADB over WiFi) + Shizuku.

Two apps, one Gradle project:

* **controller-app** — installs on your phone. It keeps the existing touch layout
  with one analog stick, D-pad, face buttons, L1/R1, L2/R2, and Start. It sends
  an 11-byte UDP state packet about 60 times/second. See `PROTOCOL.md`.
* **receiver-app** — installs on the TV. Uses Shizuku (shell privilege, no root)
  to run AOSP's `uinput` command and register a virtual gamepad input device.
  Android keylayout selection and game-level controller support vary by TV
  firmware and game; registering a device does not guarantee universal support.

## How the pieces fit

```
 phone (controller-app)                  TV (receiver-app, via Shizuku)
 ┌─────────────────────┐   WiFi / UDP    ┌───────────────────────────────┐
 │ touch sticks/buttons │ ─────────────► │ UDP socket (shell process)     │
 │ -> GamepadState      │  port 27191    │   -> uinput JSON commands      │
 │ -> 11-byte packet    │                │   -> /dev/uinput virtual pad   │
 └─────────────────────┘                 └───────────────────────────────┘
```

No PC, no USB cable is needed at play time — wireless debugging is only used
*once* to start Shizuku on the TV.

## Build

CircleCI builds both Android debug APKs on pushes to main and pull requests.
The workflow runs Kotlin unit tests and Android Lint, then assembles both APKs.
The phone and TV interfaces are native Android Material 3 screens. The project
does not use the Flutter SDK, Flutter engine, Dart runtime, or a Flutter module.

The repository does not include a Gradle wrapper. CI installs Java 17,
Gradle 8.14.6, Android SDK 35, and NDK 28.2.13676358. It links one native app
module at a time to the temporary Gradle project :app; therefore the old
./gradlew :controller-app:assembleDebug :receiver-app:assembleDebug command
is not valid for this repository. For local builds, follow the two app-build
stages in .circleci/config.yml.

## One-time setup on the TV

1. Settings → About → tap Build number 7x to unlock Developer options.
2. Developer options → enable **Wireless debugging**.
3. Install **Shizuku** (from its GitHub releases or Play Store) and
   **receiver-app** on the TV.
4. Open Shizuku's app, choose "Pair device with pairing code", it will show
   the pairing screen — this uses Android's own wireless-debugging pairing
   flow, no computer required. Confirm once.
5. Back in Shizuku, tap "Start" — this launches the Shizuku service using the
   wireless-debugging shell session you just paired.
6. Open **receiver-app**, tap **Start**, grant the Shizuku permission prompt.
   The screen shows the TV's IP and confirms it's listening.

After a TV reboot, Shizuku stops (it isn't rooted, so nothing restarts it
automatically) — reopen the Shizuku app and tap "Start" again; you will not
need to re-pair.

## On the phone

Open **controller-app**, type the TV's IP shown in receiver-app, tap
**Connect**. Both devices must be on the same WiFi network/subnet.

## Verifying it worked

On the TV, a gamepad tester or Settings → Remote & accessories on some
Android TV builds may show the virtual device after receiver-app starts.
Recognition and game compatibility depend on the TV firmware and the game.

## Notes / limitations

* L2/R2 use the existing touch regions to produce analog values. The receiver
  maps them to the trigger axes in AOSP's Xbox 360 controller keylayout. OEM
  TV keylayout behavior can still vary.
* The D-pad supports cardinal and diagonal directions through simultaneous
  touch input, using the wire codes documented in `PROTOCOL.md`.
* The virtual controller exposes one analog stick. Games that require a
  separate right stick for camera control cannot receive that second axis.
* UDP remains unencrypted and unauthenticated; use only on a trusted local network.
