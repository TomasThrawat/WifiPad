import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wifipad_ui/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const controller = MethodChannel('com.wifipad/controller');
  const receiver = MethodChannel('com.wifipad/receiver');

  tearDown(() {
    controller.setMockMethodCallHandler(null);
    receiver.setMockMethodCallHandler(null);
  });

  testWidgets('controller page shows address field and controller action', (tester) async {
    controller.setMockMethodCallHandler((call) async => '192.168.1.20');
    await tester.pumpWidget(const WifiPadApp(initialRoute: '/controller'));
    await tester.pumpAndSettle();
    expect(find.text('Connect to your TV'), findsOneWidget);
    expect(find.text('Open controller'), findsOneWidget);
  });

  testWidgets('receiver page shows status and start action', (tester) async {
    receiver.setMockMethodCallHandler((call) async {
      if (call.method == 'getStatus') {
        return <String, Object?>{
          'running': false,
          'address': '192.168.1.20',
          'port': 27191,
          'packetsReceived': 0,
          'shizukuConnected': false,
          'permissionGranted': false,
        };
      }
      return <String, Object?>{};
    });
    await tester.pumpWidget(const WifiPadApp(initialRoute: '/receiver'));
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.text('Receiver stopped'), findsOneWidget);
    expect(find.text('Start receiver'), findsOneWidget);
  });
}
