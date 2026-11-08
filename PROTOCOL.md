# WifiPad wire protocol

UDP, 11 bytes per packet, sent at a fixed ~60 Hz regardless of whether
anything changed (simpler than delta-encoding, and cheap on a LAN).

| Offset | Field         | Type   | Range / notes                                    |
|-------:|---------------|--------|---------------------------------------------------|
| 0      | magic         | byte   | always `0x57` ('W')                                |
| 1      | version       | byte   | `1`                                                 |
| 2–3    | buttons       | uint16 | little-endian bitmask, see below                   |
| 4      | leftX         | int8   | -127..127, left stick horizontal                   |
| 5      | leftY         | int8   | -127..127, left stick vertical                      |
| 6      | rightX        | int8   | -127..127, right stick horizontal                   |
| 7      | rightY        | int8   | -127..127, right stick vertical                     |
| 8      | leftTrigger   | uint8  | 0..255, L2                                          |
| 9      | rightTrigger  | uint8  | 0..255, R2                                          |
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
| 7   | R3 (right click)  |
| 8   | Select / Share    |
| 9   | Start / Options   |
| 10  | Mode / PS button  |

The controller emits all eight dpad codes: a diagonal (2/4/6/8) is sent when two
adjacent d-pad buttons are held together.

Triggers are analog: the controller derives 0..255 from the vertical touch
position inside the L2/R2 pad (top = 0, bottom = 255).
