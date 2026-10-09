# WifiPad

WifiPad turns an Android phone into a Wi-Fi gamepad for an Android TV. The Flutter apps preserve the original UDP protocol and use the TV's Shizuku/uinput bridge to register a system-wide virtual Xbox 360-style controller.

## Flutter apps (recommended)

The Flutter sources are in `flutter/controller/` and `flutter/receiver/`. GitHub Actions creates the Android scaffolds, injects the minimal native bridge needed for Shizuku/uinput, tests the packet protocol, and builds two `arm64-v8a` debug APKs.

- **Phone controller** — enter the TV IP, connect, and use the on-screen sticks, D-pad, face buttons, shoulder buttons, and Start/Select/PS buttons.
- **TV receiver** — shows the TV IP, receiver status, and received packet count. Press **START** to request Shizuku permission and start the UDP receiver; press **STOP** to stop it.
- **Network protocol** — 11-byte UDP packets on port `27191`, approximately 60 packets/second.
- **Builds** — [Open the Flutter GitHub Actions workflow](https://github.com/TomasThrawat/WifiPad/actions/workflows/flutter-build.yml). Open a successful run and download the `WifiPad-Flutter-APKs-arm64` artifact containing both APKs.

The CI runs Dart formatting, static analysis, packet protocol tests, and both Android arm64 APK builds. The original Android modules remain in the repository because registering a real system input device through Shizuku and AOSP `uinput` requires a small Android platform bridge; Flutter alone cannot perform that privileged operation.

## Setup

1. Install the receiver APK on the TV and the controller APK on the phone.
2. On the TV, enable Developer options and **Wireless debugging**.
3. Install and start Shizuku on the TV. Pair it once using Android's wireless-debugging pairing flow.
4. Open **WifiPad Receiver** and press **START**. Grant Shizuku permission when prompted. The screen should show the TV IP and a running status.
5. Open **WifiPad** on the phone, enter the TV IP, and tap **CONNECT**.
6. Keep both devices on the same private Wi-Fi network. The receiver listens on UDP port `27191`.

After a TV reboot, start Shizuku again before pressing **START** in WifiPad Receiver. Re-pairing should not be necessary unless Android's wireless-debugging pairing is reset.

## Original native Android modules

The original `controller-app/` and `receiver-app/` modules are retained for reference and as the source of the privileged receiver bridge. Their original Gradle build remains available:

```sh
./gradlew :controller-app:assembleDebug :receiver-app:assembleDebug
```

See [`PROTOCOL.md`](PROTOCOL.md) for the wire format.

## Security and limitations

- UDP is unencrypted and unauthenticated. Use only on a trusted private network; do not expose port `27191` to the internet.
- L2/R2 are digital press buttons (0 or 255), not analog drag triggers.
- The D-pad sends cardinal directions; diagonal direction codes remain reserved by the original protocol.
- Receiver functionality depends on the TV firmware exposing a working `uinput` command and on Shizuku running with the required shell privileges.
