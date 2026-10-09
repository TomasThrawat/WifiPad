import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'protocol.dart';

void main() => runApp(const WifiPadApp());

const _black = Color(0xFF000000);
const _panel = Color(0xFF171717);
const _line = Color(0xFF343434);
const _white = Color(0xFFFFFFFF);

class WifiPadApp extends StatelessWidget {
  const WifiPadApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(title: 'WifiPad', debugShowCheckedModeBanner: false, theme: ThemeData(useMaterial3: true, brightness: Brightness.dark, scaffoldBackgroundColor: _black, colorScheme: const ColorScheme.dark(primary: _white, surface: _black)), home: const ConnectPage());
}

class ConnectPage extends StatefulWidget {
  const ConnectPage({super.key});
  @override State<ConnectPage> createState() => _ConnectPageState();
}
class _ConnectPageState extends State<ConnectPage> {
  final _ip = TextEditingController();
  String _status = 'Enter the TV IP shown in the WifiPad Receiver app.';
  bool _busy = false;
  @override void dispose() { _ip.dispose(); super.dispose(); }
  Future<void> _connect() async {
    final host = _ip.text.trim();
    if (host.isEmpty) { setState(() => _status = 'Enter the TV IP address first.'); return; }
    setState(() { _busy = true; _status = 'Connecting…'; });
    try {
      final address = (await InternetAddress.lookup(host)).first;
      if (!mounted) return;
      await Navigator.of(context).push(MaterialPageRoute(builder: (_) => PadPage(host: address.address)));
      if (mounted) setState(() => _status = 'Disconnected.');
    } catch (_) { if (mounted) setState(() => _status = 'Could not resolve that address. Check the TV IP and Wi-Fi.'); }
    finally { if (mounted) setState(() => _busy = false); }
  }
  @override Widget build(BuildContext context) => Scaffold(body: SafeArea(child: Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 520), child: Padding(padding: const EdgeInsets.all(24), child: Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.stretch, children: [const Icon(Icons.sports_esports, size: 64), const SizedBox(height: 20), const Text('WifiPad', textAlign: TextAlign.center, style: TextStyle(fontSize: 30, fontWeight: FontWeight.w700)), const SizedBox(height: 8), const Text('Turn your phone into a Wi-Fi gamepad', textAlign: TextAlign.center, style: TextStyle(color: Colors.white70)), const SizedBox(height: 32), TextField(controller: _ip, keyboardType: TextInputType.url, textInputAction: TextInputAction.go, onSubmitted: (_) => _connect(), decoration: const InputDecoration(labelText: 'TV IP address', hintText: '192.168.1.100', border: OutlineInputBorder())), const SizedBox(height: 16), FilledButton(onPressed: _busy ? null : _connect, child: Padding(padding: const EdgeInsets.all(12), child: Text(_busy ? 'CONNECTING…' : 'CONNECT', style: const TextStyle(fontWeight: FontWeight.bold)))), const SizedBox(height: 12), Text(_status, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white60))])))));
}

class PadPage extends StatefulWidget {
  const PadPage({super.key, required this.host}); final String host;
  @override State<PadPage> createState() => _PadPageState();
}
class _PadPageState extends State<PadPage> {
  RawDatagramSocket? _socket; Timer? _timer; int _buttons = 0, _dpad = 0, _lx = 0, _ly = 0, _rx = 0, _ry = 0, _lt = 0, _rt = 0; String _status = 'Sending at 60 Hz';
  @override void initState() { super.initState(); _open(); }
  Future<void> _open() async { try { _socket = await RawDatagramSocket.bind(InternetAddress.anyIPv4, 0); _timer = Timer.periodic(const Duration(milliseconds: 16), (_) => _send()); if (mounted) setState(() => _status = 'Connected to ${widget.host}:$defaultPort'); } catch (_) { if (mounted) setState(() => _status = 'Unable to open UDP socket'); } }
  void _send() { final socket = _socket; if (socket == null) return; socket.send(makePacket(buttons: _buttons, leftX: _lx, leftY: _ly, rightX: _rx, rightY: _ry, leftTrigger: _lt, rightTrigger: _rt, dpad: _dpad), InternetAddress(widget.host), defaultPort); }
  void _button(int bit, bool down) { setState(() => _buttons = down ? (_buttons | bit) : (_buttons & ~bit)); }
  void _trigger(bool left, bool down) { setState(() { if (left) { _lt = down ? 255 : 0; } else { _rt = down ? 255 : 0; } }); }
  void _stick(bool left, Offset value) { setState(() { if (left) { _lx = value.dx.round(); _ly = value.dy.round(); } else { _rx = value.dx.round(); _ry = value.dy.round(); } }); }
  @override void dispose() { _timer?.cancel(); _socket?.close(); _buttons = 0; _dpad = 0; _lx = _ly = _rx = _ry = _lt = _rt = 0; super.dispose(); }
  Widget _hold(String label, int bit, {bool circle = true}) => _HoldButton(label: label, circle: circle, onChange: (down) => _button(bit, down));
  Widget _triggerButton(String label, bool left) => _HoldButton(label: label, circle: false, onChange: (down) => _trigger(left, down));
  Widget _dpadButton(String label, int value) => _HoldButton(label: label, circle: false, onChange: (down) => setState(() => _dpad = down ? value : (_dpad == value ? 0 : _dpad)));
  @override Widget build(BuildContext context) => Scaffold(body: SafeArea(child: Column(children: [Padding(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4), child: Row(children: [IconButton(onPressed: () => Navigator.pop(context), icon: const Icon(Icons.arrow_back)), Expanded(child: Text(_status, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white60))), IconButton(onPressed: () => setState(() { _buttons = _dpad = _lx = _ly = _rx = _ry = _lt = _rt = 0; }), icon: const Icon(Icons.refresh))])), Expanded(child: Padding(padding: const EdgeInsets.all(8), child: Row(children: [Expanded(child: Column(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [_triggerButton('L2', true), _hold('L1', ButtonBit.l1)]), Expanded(child: Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [Flexible(child: Center(child: _Stick(onChanged: (v) => _stick(true, v)))), _DpadCluster(button: _dpadButton)])), Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [_hold('SELECT', ButtonBit.select, circle: false), _hold('L3', ButtonBit.l3)])])), const SizedBox(width: 8), Expanded(child: Column(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [_hold('R1', ButtonBit.r1), _triggerButton('R2', false)]), Expanded(child: Center(child: _FaceCluster(button: _hold))), Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [_hold('START', ButtonBit.start, circle: false), _hold('PS', ButtonBit.mode, circle: false)])]))])))])));
}

class _HoldButton extends StatelessWidget {
  const _HoldButton({required this.label, required this.onChange, this.circle = true}); final String label; final ValueChanged<bool> onChange; final bool circle;
  @override Widget build(BuildContext context) => Listener(onPointerDown: (_) => onChange(true), onPointerUp: (_) => onChange(false), onPointerCancel: (_) => onChange(false), child: Container(width: circle ? 58 : 68, height: circle ? 58 : 42, alignment: Alignment.center, decoration: BoxDecoration(color: _panel, border: Border.all(color: _line), borderRadius: BorderRadius.circular(circle ? 40 : 12)), child: Text(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold))));
}

class _FaceCluster extends StatelessWidget {
  const _FaceCluster({required this.button}); final Widget Function(String, int, {bool circle}) button;
  @override Widget build(BuildContext context) => Column(mainAxisSize: MainAxisSize.min, children: [button('Y', ButtonBit.y), Row(mainAxisSize: MainAxisSize.min, children: [button('X', ButtonBit.x), const SizedBox(width: 18), button('B', ButtonBit.b)]), button('A', ButtonBit.a)]);
}

class _DpadCluster extends StatelessWidget {
  const _DpadCluster({required this.button}); final Widget Function(String, int) button;
  @override Widget build(BuildContext context) => Column(mainAxisSize: MainAxisSize.min, children: [button('↑', 1), Row(mainAxisSize: MainAxisSize.min, children: [button('←', 7), const SizedBox(width: 4), button('→', 3)]), button('↓', 5)]);
}

class _Stick extends StatefulWidget { const _Stick({required this.onChanged}); final ValueChanged<Offset> onChanged; @override State<_Stick> createState() => _StickState(); }
class _StickState extends State<_Stick> { Offset _value = Offset.zero; void _update(Offset local, Size size) { final center = Offset(size.width / 2, size.height / 2); var d = local - center; final radius = math.min(size.width, size.height) * .34; if (d.distance > radius) d = d / d.distance * radius; setState(() => _value = Offset(d.dx / radius * 127, d.dy / radius * 127)); widget.onChanged(_value); }
 @override Widget build(BuildContext context) => LayoutBuilder(builder: (context, c) { final side = math.min(c.maxWidth, c.maxHeight).clamp(100.0, 180.0).toDouble(); final size = Size(side, side); return GestureDetector(onPanStart: (d) => _update(d.localPosition, size), onPanUpdate: (d) => _update(d.localPosition, size), onPanEnd: (_) { setState(() => _value = Offset.zero); widget.onChanged(Offset.zero); }, child: SizedBox(width: side, height: side, child: CustomPaint(painter: _StickPainter(_value)))); }); }
class _StickPainter extends CustomPainter { _StickPainter(this.value); final Offset value; @override void paint(Canvas canvas, Size size) { final center = Offset(size.width / 2, size.height / 2); final r = math.min(size.width, size.height) * .42; final p = Paint()..color = _panel; final border = Paint()..color = _line..style = PaintingStyle.stroke..strokeWidth = 2; canvas.drawCircle(center, r, p); canvas.drawCircle(center, r, border); final knob = center + Offset(value.dx / 127, value.dy / 127) * r * .65; canvas.drawCircle(knob, r * .45, Paint()..color = const Color(0xFF444444)); canvas.drawCircle(knob, r * .45, border); } @override bool shouldRepaint(covariant _StickPainter old) => old.value != value; }
