# WifiPad Flutter migration

The Flutter apps live in `controller/` and `receiver/`. GitHub Actions generates the standard Android scaffolds, injects the TV's Shizuku/uinput bridge from the original native receiver sources, runs Dart analysis and protocol tests, then builds two `arm64-v8a` debug APKs.

- **Controller APK**: install on the phone. Enter the TV IP shown by the receiver, connect, then use the touch gamepad. It sends the original 11-byte UDP protocol on port `27191` at approximately 60 Hz.
- **Receiver APK**: install on the TV. Start Shizuku on the TV first, open WifiPad Receiver, press **START**, and grant Shizuku permission. It registers the virtual Xbox 360-style controller through Android's `uinput` command and reports received packets.
- Both devices must be on the same private Wi-Fi network. UDP is not encrypted or authenticated; do not expose port `27191` to the internet.

Run `.github/workflows/flutter-build.yml` or push changes to the `flutter-migration` branch. Download `WifiPad-Flutter-APKs-arm64` from the successful workflow run. The original native apps are retained until the Flutter replacement is verified.
