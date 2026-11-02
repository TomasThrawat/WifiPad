# WifiPad wire protocol

UDP, 11 bytes per packet, sent at a fixed ~60 Hz regardless of whether
anything changed (simpler than delta-encoding, and cheap on a LAN).

| Offset | Field         | Type   | Range / notes                                    |
|-------:|---------------|--------|---------------------------------------------------|
| 0      | magic         | byte   | always `0x57` ('W')                                |
| 1      | version       | byte   | `1`                                                 |
| 2–3    | buttons       | uint16 | little-endian bitmask, see below                   |
| 4      | leftX         | int8   | -127..127, the one analog stick horizontal          |
| 5      | leftY         | int8   | -127..127, the one analog stick vertical            |
| 6      | rightX        | int8   | legacy/reserved; ignored by receiver                |
| 7      | rightY        | int8   | legacy/reserved; ignored by receiver                |
| 8      | leftTrigger   | uint8  | 0..255, L2; ABS_BRAKE + BTN_TL2 at >=128            |
| 9      | rightTrigger  | uint8  | 0..255, R2; ABS_GAS + BTN_TR2 at >=128              |
| 10     | dpad          | uint8  | 0=none 1=up 2=up-right 3=right 4=down-right 5=down 6=down-left 7=left 8=up-left |

## Button bitmask (byte 2 = bits 0–7, byte 3 = bits 8–10)

| Bit | Button            |
|----:|-------------------|
| 0   | A (Cross)         |
| 1   | B (Circle)        |
| 2   | X (Square)        |
| 3   | Y (Triangle)      |
| 4   | L1                |
| 5   | R1                |
| 6   | L3 (left click)   |
| 7   | R3 (right-stick click; ignored by one-stick receiver) |
| 8   | Select / Share    |
| 9   | Start / Options   |
| 10  | Mode / PS button  |

The controller emits cardinal and diagonal D-pad codes when directions are
pressed simultaneously. The receiver validates D-pad values in the range 0..8.

The receiver accepts only exact 11-byte v1 packets with the correct magic and
version, button bits within 0x7ff, stick values in -127..127, and D-pad values
in 0..8. Invalid packets do not refresh the watchdog. Bytes 6–7 are ignored so
older v1 senders remain wire-compatible. The receiver maps L2/R2 to Linux
ABS_BRAKE/ABS_GAS and also emits BTN_TL2/BTN_TR2 key transitions at value 128.
