# WifiPad secure wire protocol

## Version 2 (current)

WifiPad v2 uses UDP on port `27191`, a persistent pairing secret, HMAC-SHA256
truncated to 128 bits, a fresh two-nonce session handshake, monotonically
increasing sequence numbers, and authenticated acknowledgements. The receiver
accepts input only from the paired controller ID and the source address/port
that completed the current handshake. A HELLO creates a provisional challenge;
it cannot replace a recently active session. The receiver commits that session
only after a correctly authenticated INPUT proves possession of the fresh server
nonce. Duplicate HELLOs from the same pending endpoint reuse the original
challenge, and a bounded recent client-nonce history rejects replayed handshakes
during the service lifetime. Replayed, stale, unsigned, malformed, or
out-of-session frames are rejected. The receiver releases every virtual input
after the existing 750 ms watchdog timeout.

The controller sends a final authenticated neutral INPUT frame when the gamepad
screen pauses or the controller stops. It waits briefly for the matching
authenticated ACK before closing the UDP socket; the receiver watchdog remains
the fallback if that frame or ACK is lost. RTT measurements are matched to the
exact acknowledged sequence, including delayed ACKs, instead of measuring the
current packet against an unrelated earlier ACK.

The 256-bit pairing secret is stored encrypted using Android Keystore AES-GCM
on both devices. The TV displays a QR code containing the TV ID, controller ID,
secret, receiver address and UDP port. Rotating the pairing key invalidates
the earlier controller identity and key. Show the QR only while pairing a
trusted phone.

### Frame format

All multibyte integers are big-endian. Every frame starts with `0x57`, version
`2`, a message type, and ends with a 16-byte HMAC tag over the preceding bytes.

| Type | Value | Total bytes | Authenticated fields |
|---|---:|---:|---|
| HELLO | 1 | 43 | 16-byte controller ID, 64-bit client nonce |
| WELCOME | 2 | 51 | controller ID, echoed client nonce, fresh 64-bit server nonce |
| INPUT | 3 | 68 | controller ID, both nonces, 64-bit sequence, 9-byte gamepad payload |
| ACK | 4 | 59 | controller ID, both nonces, acknowledged sequence |

### 9-byte gamepad payload

| Offset | Field | Type | Range |
|---:|---|---|---|
| 0–1 | buttons | uint16 | bits 0–10; mapping below |
| 2 | leftX | int8 | -127..127 |
| 3 | leftY | int8 | -127..127 |
| 4 | rightX | int8 | -127..127 |
| 5 | rightY | int8 | -127..127 |
| 6 | leftTrigger | uint8 | 0..255 |
| 7 | rightTrigger | uint8 | 0..255 |
| 8 | dpad | uint8 | 0=none, 1=N, 2=NE, 3=E, 4=SE, 5=S, 6=SW, 7=W, 8=NW |

### Button bitmask

| Bit | Button |
|---:|---|
| 0 | A (Cross) |
| 1 | B (Circle) |
| 2 | X (Square) |
| 3 | Y (Triangle) |
| 4 | L1 |
| 5 | R1 |
| 6 | L3 |
| 7 | R3 |
| 8 | Select |
| 9 | Start |
| 10 | Mode |

## Compatibility and migration

Version 1 used an unauthenticated 11-byte packet. The current receiver does
not accept v1 packets because that would restore unauthenticated controller
input. Both the phone Controller and TV Receiver must be updated to v2 and
paired using the TV's QR code. Do not mix v1 and v2 applications.

WifiPad is a gamepad-only application: it sends only game buttons, both analog
sticks, both triggers and the D-pad. The TV creates one virtual Xbox-style
gamepad through Shizuku/uinput. TV firmware and individual games may map
controller keys and axes differently.
