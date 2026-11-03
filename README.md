# WifiPad

WifiPad turns an Android phone into a **gamepad for Android TV** over the local Wi-Fi network. It consists of two native Android apps and a secure UDP protocol. It is gamepad-only: there is no TV remote, touchpad, mouse, pointer, media-control or navigation-remote mode.

## Apps

### Controller (phone)

The phone app provides a full-screen on-screen controller with:

- Left and right analog sticks.
- A/B/X/Y face buttons, L1/R1 shoulders, analog L2/R2 triggers and an eight-direction D-pad.
- L3/R3 stick clicks, Select, Start and Mode.
- Adjustable control positions and sizes, stick dead zones and sensitivity, saved profiles and button remapping.
- Secure QR pairing, discovery of already-paired TVs, reconnect discovery, and connection/ACK statistics.

### Receiver (Android TV)

The TV app starts a Shizuku user service and registers one Xbox-style virtual gamepad through `uinput`. Its diagnostics show received, rejected and missing-sequence packets, the paired phone address, packet age, current button/axis values, and whether Android `InputManager` reports the matching gamepad device and its axis ranges.

A device appearing in `InputManager` confirms Android has registered a matching input device; it does not prove that every game consumes every key or axis. Android TV firmware, key layouts and individual games can differ.

## Secure connection

Both devices must be on the same local network. Start Shizuku on the TV, open WifiPad Receiver and start the receiver, then show its secure pairing QR code. Scan that code in WifiPad Controller. A previously paired TV can also be found through network discovery.

Protocol version 2 uses UDP port `27191`, a 256-bit pairing secret stored with Android Keystore AES-GCM, HMAC-SHA256 authentication truncated to 128 bits, fresh client/server nonces, monotonically increasing input sequence numbers and authenticated ACKs. The receiver rejects unauthenticated v1 packets, invalid sessions and replayed input, and releases virtual controls after its 750 ms watchdog timeout. The phone sends an authenticated neutral gamepad frame when the gamepad screen pauses or stops; the watchdog remains the fallback if the neutral frame or its ACK is lost.

**The protocol authenticates packets but does not encrypt the UDP input payload.** Use WifiPad only on a trusted local network and keep the pairing QR private. Rotating the TV pairing key revokes the previous pairing.

See `PROTOCOL.md` for the complete frame layout and button/axis mapping.

## No vibration

Neither app provides vibration or haptic feedback. Both Android manifests remove the `android.permission.VIBRATE` permission during manifest merging, and CircleCI checks the final APK permissions so that the permission cannot be reintroduced unnoticed.

## Build and verification

CircleCI builds the phone controller and TV receiver APKs. The workflow runs both modules' Kotlin unit tests and Android Lint, assembles both debug APKs, checks the APK contents and inspects the merged permissions for `VIBRATE`.

The current CI configuration uses JDK 17, Gradle 9.3.1, Android SDK platform 37.0, Build Tools 36.0.0 and NDK 28.2.13676358. The repository has no Gradle wrapper; use the two module build stages in `.circleci/config.yml` for local builds. The CI job creates a temporary `:app` link for each module rather than building both named modules in one Gradle invocation.

No emulator is required by the CI workflow. Unit tests validate the protocol and state logic. Real input-device registration, Shizuku/uinput behavior and game compatibility still require testing on the target Android TV.
