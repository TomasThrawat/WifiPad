# WifiPad wire protocol

UDP, 11 bytes per packet, sent at a fixed ~60 Hz regardless of whether
anything changed (simpler than delta-encoding, and cheap on a LAN).

| Offset | Field         | Type   | Range / notes                                    |
|-------:|---------------|--------|---------------------------------------------------|
| 0      | magic         | byte   | always `0x57` ('W')                                |
| 1      | version       | byte   | `1`                                                 |
| 2–3    | buttons       | uint16 | little-endian bitmask, see below                   |
| 4      | leftX         | int8   | -127..127, left stick horizontal                    |
| 5      | leftY         | int8   | -127..127, left stick vertical                      |
| 6      | rightX        | int8   | -127..127, right stick horizontal                   |
| 7      | rightY        | int8   | -127..127, right stick vertical                     |
| 8      | leftTrigger   | uint8  | 0..255, L2; mapped to AOSP Xbox keylayout ABS_Z / LTRIGGER |
| 9      | rightTrigger  | uint8  | 0..255, R2; mapped to AOSP Xbox keylayout ABS_RZ / RTRIGGER |
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
| 7   | R3 (right-stick click) |
| 8   | Select / Share    |
| 9   | Start / Options   |
| 10  | Mode / PS button  |

The controller emits cardinal and diagonal D-pad codes when directions are
pressed simultaneously. The receiver validates D-pad values in the range 0..8.

The receiver accepts only exact 11-byte v1 packets with the correct magic and
version, button bits within 0x7ff, stick values in -127..127, and D-pad values
in 0..8. Older v1 receivers ignore bytes 6–7, so older installations remain
compatible but cannot use the right stick. The receiver identifies as AOSP's
Xbox 360 controller (VID 0x045e, PID 0x028e): left-stick axes use ABS_X/ABS_Y,
right-stick axes use Linux ABS_RX/ABS_RY (keylayout names Z/RZ), and L2/R2 use
Linux ABS_Z/ABS_RZ (keylayout names LTRIGGER/RTRIGGER). L3/R3 use BTN_THUMBL/BTN_THUMBR.
