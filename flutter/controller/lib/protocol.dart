import 'dart:typed_data';

const int packetSize = 11;
const int packetMagic = 0x57;
const int packetVersion = 1;
const int defaultPort = 27191;

abstract final class ButtonBit {
  static const int a = 1 << 0, b = 1 << 1, x = 1 << 2, y = 1 << 3;
  static const int l1 = 1 << 4, r1 = 1 << 5, l3 = 1 << 6, r3 = 1 << 7;
  static const int select = 1 << 8, start = 1 << 9, mode = 1 << 10;
}

Uint8List makePacket({int buttons = 0, int leftX = 0, int leftY = 0, int rightX = 0, int rightY = 0, int leftTrigger = 0, int rightTrigger = 0, int dpad = 0}) {
  final data = ByteData(packetSize);
  data.setUint8(0, packetMagic);
  data.setUint8(1, packetVersion);
  data.setUint16(2, buttons & 0x7ff, Endian.little);
  data.setInt8(4, leftX.clamp(-127, 127));
  data.setInt8(5, leftY.clamp(-127, 127));
  data.setInt8(6, rightX.clamp(-127, 127));
  data.setInt8(7, rightY.clamp(-127, 127));
  data.setUint8(8, leftTrigger.clamp(0, 255));
  data.setUint8(9, rightTrigger.clamp(0, 255));
  data.setUint8(10, dpad.clamp(0, 8));
  return data.buffer.asUint8List();
}
