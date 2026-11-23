class ApiConfig {
  // على محاكي أندرويد الافتراضي هو 10.0.2.2:8080
  // وعلى جهاز حقيقي مرّر IP جهازك: flutter run --dart-define=API_HOST=192.168.1.104:8080
  static const String host = String.fromEnvironment(
    'API_HOST',
    defaultValue: '10.0.2.2:8080',
  );

  static String get baseUrl => 'http://$host/api';
  static String get wsUrl => 'ws://$host/ws/websocket';
}