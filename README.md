# WifiPad

A PS4-style virtual gamepad that connects over WiFi (UDP) instead of Bluetooth,
for TVs that only support wireless debugging (ADB over WiFi) + Shizuku.

Two apps, one Gradle project:

* **controller-app** — installs on your phone (OPPO A73 4G). Draws two analog
  sticks, D-pad, face buttons, L1/R1, L2/R2, Share/PS/Options. Sends an 11-byte
  UDP packet ~60 times/second to the TV. See `PROTOCOL.md`.
* **receiver-app** — installs on the TV. Uses Shizuku (shell privilege, no root)
  to run AOSP's built-in `uinput` command and register a real virtual gamepad
  device that the whole system — including games — sees exactly like a wired
  Xbox 360 controller, because it registers with that controller's actual
  vendor/product ID (0x045e / 0x028e) and every stock Android build already
  ships the matching keylayout file for it.

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

### Editing the layout

Tap **Edit layout** on the controller screen. The pad stays on screen (no
separate settings page): tap a control to select it, drag it to move it, and use
the small panel to hide/show it or change its size (and dead zone / sensitivity
for the sticks). Hidden controls stay visible but dimmed while editing so you can
bring them back. **Save** keeps the layout, **Cancel** (or Back) discards the
changes, **Reset** restores the defaults. There is a single layout, stored on the
phone.

## Verifying it worked

On the TV, any app that reads gamepad input (a "gamepad tester" app, or
Settings → Remote & accessories on some Android TV builds) should list a
device named "Xbox 360 Controller" the moment receiver-app is started — even
before you touch the phone's sticks, since the virtual device is registered
immediately.

## Notes / limitations

* L2/R2 on the phone UI are simple press buttons (0 or 255), not a smooth
  drag-to-analog gesture — straightforward to extend in `GamepadView.kt`
  if a game needs a graduated trigger pull.
* The D-pad supports all four cardinal directions and four diagonals
  (8-way input), using the wire codes documented in `PROTOCOL.md`.
* UDP is unencrypted and unauthenticated — fine on a private home network,
  not something to expose past your router.
