# WifiPad

WifiPad turns an Android phone into one universal Wi-Fi gamepad for Android TV. Both apps are native Android/Gradle apps; neither APK includes the Flutter runtime or Flutter libraries. The controller sends the original 11-byte UDP protocol to the TV receiver, which uses Shizuku and Android's `uinput` command to register a system-wide virtual Xbox 360-style controller.

## One controller UI for all emulators

The controller keeps a single shared layout and settings screen. There are no separate UIs or settings profiles per console. The same standard gamepad exposes:

- D-pad, including diagonal combinations
- A/B/X/Y face buttons
- L1/R1 and L2/R2
- Select, Start and Mode
- Left and right analog sticks, including L3/R3 press clicks

This common XInput-style device works with Android emulators that support standard Android gamepads, including NES/Famicom, PSP, PlayStation, Nintendo and retro-console emulators. Some emulators or cores use different default key bindings, so map the buttons once in that emulator's input settings when needed. The app does not attempt to guess or change each emulator's separate mapping.

## Build

Open this repository in Android Studio, or run:

```sh
gradle :controller-app:testDebugUnitTest :controller-app:assembleDebug :receiver-app:assembleDebug
```

The `.github/workflows/build.yml` GitHub Actions workflow runs native unit tests, builds both APKs, and uploads separate artifacts for the phone controller and TV receiver. The native build is kept lightweight by not packaging Flutter into the apps.

## Setup

1. Install the receiver APK on the TV and the controller APK on the phone.
2. On the TV, enable Developer options and **Wireless debugging**.
3. Install and start Shizuku on the TV. Pair it once using Android's wireless-debugging pairing flow.
4. Open **WifiPad Receiver** and press **Start**. Grant Shizuku permission when prompted; note the TV IP address.
5. Open **WifiPad Controller** on the phone, enter the TV IP, and tap **Connect**.
6. Keep both devices on the same private Wi-Fi network. The receiver listens on UDP port `27191`.

After a TV reboot, start Shizuku again before using the receiver. Re-pairing should not be necessary unless Android's wireless-debugging pairing is reset.

## Network and limitations

- UDP is unencrypted and unauthenticated. Use only on a trusted private network; do not expose port `27191` to the internet.
- L2/R2 are digital press buttons (0 or 255), not analog pressure sliders.
- The receiver relies on the TV firmware exposing a working `uinput` command and on Shizuku running with the required shell privileges.
