import 'package:flutter_test/flutter_test.dart';
import 'package:wifipad_controller/protocol.dart';

void main() {
  test('encodes the 11-byte little-endian WifiPad packet', () {
    final p = makePacket(buttons: ButtonBit.a | ButtonBit.mode, leftX: -127, leftY: 127, rightX: -1, rightY: 1, leftTrigger: 255, rightTrigger: 300, dpad: 8);
    expect(p.length, packetSize);
    expect(p[0], 0x57); expect(p[1], 1);
    expect(p[2], 1); expect(p[3], 4);
    expect(p[4], 129); expect(p[5], 127);
    expect(p[8], 255); expect(p[9], 255); expect(p[10], 8);
  });
  test('clamps axes, triggers and dpad to protocol ranges', () {
    final p = makePacket(leftX: -200, leftY: 200, leftTrigger: -1, rightTrigger: 300, dpad: 10);
    expect(p[4], 129); expect(p[5], 127); expect(p[8], 0); expect(p[9], 255); expect(p[10], 8);
  });
}
