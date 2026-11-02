import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const _controller = MethodChannel('com.wifipad/controller');
const _receiver = MethodChannel('com.wifipad/receiver');
const _black = Color(0xFF000000);
const _panel = Color(0xFF101010);
const _raised = Color(0xFF171717);
const _line = Color(0xFF303030);
const _muted = Color(0xFF9B9B9B);
const _white = Color(0xFFFFFFFF);
const _error = Color(0xFFFF7777);

void main() {
  final route = WidgetsBinding.instance.platformDispatcher.defaultRouteName;
  runApp(WifiPadApp(initialRoute: route));
}

class WifiPadApp extends StatelessWidget {
  const WifiPadApp({super.key, required this.initialRoute});
  final String initialRoute;

  @override
  Widget build(BuildContext context) {
    final isReceiver = initialRoute == '/receiver';
    const colors = ColorScheme.dark(
      primary: _white,
      onPrimary: _black,
      secondary: Color(0xFFD0D0D0),
      onSecondary: _black,
      surface: _black,
      onSurface: _white,
      error: _error,
      onError: _black,
    );
    return MaterialApp(
      title: 'WifiPad',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: colors,
        scaffoldBackgroundColor: _black,
        canvasColor: _black,
        dividerColor: _line,
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: _raised,
          hintStyle: const TextStyle(color: _muted),
          labelStyle: const TextStyle(color: _muted),
          contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: _line),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: _line),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: _white, width: 1.4),
          ),
        ),
      ),
      home: isReceiver ? const ReceiverHomePage() : const ControllerHomePage(),
    );
  }
}

class ControllerHomePage extends StatefulWidget {
  const ControllerHomePage({super.key});

  @override
  State<ControllerHomePage> createState() => _ControllerHomePageState();
}

class _ControllerHomePageState extends State<ControllerHomePage> {
  final _host = TextEditingController();
  bool _busy = false;
  String? _message;

  @override
  void initState() {
    super.initState();
    _restoreHost();
  }

  Future<void> _restoreHost() async {
    try {
      final value = await _controller.invokeMethod<String>('getSavedHost');
      if (mounted && value != null && _host.text.isEmpty) {
        _host.text = value;
      }
    } on PlatformException {
      // The editable address field remains usable without saved preferences.
    }
  }

  Future<void> _connect() async {
    final host = _host.text.trim();
    if (host.isEmpty) {
      setState(() => _message = 'Enter the TV IP address or host name.');
      return;
    }
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      await _controller.invokeMethod<bool>(
        'connect',
        <String, Object?>{'host': host},
      );
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Could not open controller.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  void dispose() {
    _host.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _black,
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(24, 24, 24, 32),
              children: [
                const _BrandHeader(
                  eyebrow: 'PHONE CONTROLLER',
                  subtitle: 'Use your phone as a wireless game controller.',
                  icon: Icons.gamepad_rounded,
                ),
                const SizedBox(height: 44),
                const Text(
                  'Connect to your TV',
                  style: TextStyle(
                    color: _white,
                    fontSize: 25,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.5,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Enter the receiver address shown on your TV.',
                  style: TextStyle(color: _muted, height: 1.5),
                ),
                const SizedBox(height: 22),
                _Panel(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      TextField(
                        controller: _host,
                        keyboardType: TextInputType.url,
                        textInputAction: TextInputAction.go,
                        onSubmitted: (_) => _connect(),
                        style: const TextStyle(color: _white, fontSize: 16),
                        decoration: const InputDecoration(
                          labelText: 'TV IP address',
                          hintText: '192.168.1.20',
                          prefixIcon: Icon(Icons.wifi_rounded, color: _muted),
                        ),
                      ),
                      if (_message != null) ...[
                        const SizedBox(height: 12),
                        Text(_message!, style: const TextStyle(color: _error)),
                      ],
                      const SizedBox(height: 18),
                      SizedBox(
                        height: 56,
                        child: FilledButton.icon(
                          onPressed: _busy ? null : _connect,
                          icon: _busy
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: _black,
                                  ),
                                )
                              : const Icon(Icons.arrow_forward_rounded),
                          label: Text(_busy ? 'Connecting…' : 'Open controller'),
                          style: FilledButton.styleFrom(
                            backgroundColor: _white,
                            foregroundColor: _black,
                            disabledBackgroundColor: const Color(0xFFBDBDBD),
                            disabledForegroundColor: _black,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(16),
                            ),
                            textStyle: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 18),
                const _InfoRow(
                  icon: Icons.info_outline_rounded,
                  text: 'Connect both devices to the same Wi-Fi network.',
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class ReceiverHomePage extends StatefulWidget {
  const ReceiverHomePage({super.key});

  @override
  State<ReceiverHomePage> createState() => _ReceiverHomePageState();
}

class _ReceiverHomePageState extends State<ReceiverHomePage> {
  Timer? _timer;
  Map<String, Object?> _status = const {};
  bool _busy = false;
  String? _message;

  @override
  void initState() {
    super.initState();
    _refresh();
    _timer = Timer.periodic(const Duration(seconds: 2), (_) => _refresh());
  }

  Future<void> _refresh() async {
    try {
      final value = await _receiver.invokeMapMethod<String, Object?>('getStatus');
      if (mounted && value != null) {
        setState(() {
          _status = value;
          _message = null;
        });
      }
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Unable to read receiver status.');
    }
  }

  Future<void> _action(String method) async {
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      final value = await _receiver.invokeMapMethod<String, Object?>(method);
      if (mounted && value != null) setState(() => _status = value);
    } on PlatformException catch (error) {
      if (mounted) setState(() => _message = error.message ?? 'Receiver action failed.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    await _refresh();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final running = _status['running'] == true;
    final shizuku = _status['shizukuConnected'] == true;
    final permission = _status['permissionGranted'] == true;
    final receiverError = (_status['lastError'] ?? '').toString();

    return Scaffold(
      backgroundColor: _black,
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 560),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(24, 24, 24, 32),
              children: [
                const _BrandHeader(
                  eyebrow: 'ANDROID TV RECEIVER',
                  subtitle: 'Receive controller input over your local network.',
                  icon: Icons.desktop_windows_rounded,
                ),
                const SizedBox(height: 36),
                _Panel(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              running ? 'Receiver active' : 'Receiver stopped',
                              style: const TextStyle(
                                color: _white,
                                fontSize: 20,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                          _StatusPill(active: running),
                        ],
                      ),
                      const SizedBox(height: 20),
                      _MetricLine(
                        label: 'TV address',
                        value: (_status['address'] ?? 'Checking network…').toString(),
                      ),
                      const SizedBox(height: 14),
                      _MetricLine(
                        label: 'UDP port',
                        value: (_status['port'] ?? 27191).toString(),
                      ),
                      const SizedBox(height: 14),
                      _MetricLine(
                        label: 'Packets received',
                        value: (_status['packetsReceived'] ?? 0).toString(),
                      ),
                      const SizedBox(height: 18),
                      const Divider(color: _line, height: 1),
                      const SizedBox(height: 16),
                      _InfoRow(
                        icon: shizuku ? Icons.check_circle_outline : Icons.info_outline,
                        text: shizuku
                            ? (permission
                                ? 'Shizuku connected and permission granted.'
                                : 'Shizuku connected; permission required.')
                            : 'Shizuku is not connected.',
                      ),
                      if (receiverError.isNotEmpty) ...[
                        const SizedBox(height: 12),
                        Text(receiverError, style: const TextStyle(color: _error, height: 1.4)),
                      ],
                      if (_message != null) ...[
                        const SizedBox(height: 12),
                        Text(_message!, style: const TextStyle(color: _error, height: 1.4)),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: SizedBox(
                        height: 54,
                        child: FilledButton(
                          onPressed: _busy || running ? null : () => _action('startReceiver'),
                          style: FilledButton.styleFrom(
                            backgroundColor: _white,
                            foregroundColor: _black,
                            disabledBackgroundColor: const Color(0xFF242424),
                            disabledForegroundColor: _muted,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(16),
                            ),
                          ),
                          child: _busy
                              ? const SizedBox(
                                  height: 18,
                                  width: 18,
                                  child: CircularProgressIndicator(strokeWidth: 2, color: _black),
                                )
                              : const Text('Start receiver', style: TextStyle(fontWeight: FontWeight.w700)),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: SizedBox(
                        height: 54,
                        child: OutlinedButton(
                          onPressed: _busy || !running ? null : () => _action('stopReceiver'),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: _white,
                            side: const BorderSide(color: _line),
                            disabledForegroundColor: const Color(0xFF666666),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(16),
                            ),
                          ),
                          child: const Text('Stop receiver'),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                const _InfoRow(
                  icon: Icons.security_rounded,
                  text: 'Starting requires Shizuku permission to create the virtual gamepad.',
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _BrandHeader extends StatelessWidget {
  const _BrandHeader({
    required this.eyebrow,
    required this.subtitle,
    required this.icon,
  });
  final String eyebrow;
  final String subtitle;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          height: 58,
          width: 58,
          decoration: BoxDecoration(
            color: _raised,
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: _line),
          ),
          child: Icon(icon, color: _white, size: 29),
        ),
        const SizedBox(height: 22),
        Text(
          eyebrow,
          style: const TextStyle(
            color: _muted,
            fontSize: 11,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.8,
          ),
        ),
        const SizedBox(height: 7),
        const Text(
          'WifiPad',
          style: TextStyle(
            color: _white,
            fontSize: 36,
            fontWeight: FontWeight.w800,
            letterSpacing: -1.3,
          ),
        ),
        const SizedBox(height: 7),
        Text(subtitle, style: const TextStyle(color: _muted, height: 1.5, fontSize: 14)),
      ],
    );
  }
}

class _Panel extends StatelessWidget {
  const _Panel({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: _panel,
          borderRadius: BorderRadius.circular(22),
          border: Border.all(color: _line),
        ),
        child: child,
      );
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.active});
  final bool active;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 7),
        decoration: BoxDecoration(
          color: active ? const Color(0xFF16231B) : _raised,
          borderRadius: BorderRadius.circular(40),
          border: Border.all(color: active ? const Color(0xFF345640) : _line),
        ),
        child: Text(
          active ? 'LISTENING' : 'OFFLINE',
          style: TextStyle(
            color: active ? const Color(0xFF9BD3A9) : _muted,
            fontSize: 10,
            fontWeight: FontWeight.w800,
            letterSpacing: 1,
          ),
        ),
      );
}

class _MetricLine extends StatelessWidget {
  const _MetricLine({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Row(
        children: [
          Expanded(child: Text(label, style: const TextStyle(color: _muted, fontSize: 13))),
          const SizedBox(width: 16),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.end,
              style: const TextStyle(color: _white, fontSize: 14, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      );
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: _muted),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(color: _muted, fontSize: 12, height: 1.5),
            ),
          ),
        ],
      );
}
