import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:waiter_app/main.dart';

void main() {
  testWidgets('WaiterApp renders login screen when logged out', (WidgetTester tester) async {
    await tester.pumpWidget(const WaiterApp());
    expect(find.text('تسجيل الدخول'), findsOneWidget);
    expect(find.byType(TextField), findsNWidgets(2));
  });
}