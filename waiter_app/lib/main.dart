import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'screens/auth/login_screen.dart';
import 'screens/main_navigator.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  final token = prefs.getString('token');
  final userId = prefs.getInt('userId');
  final branchId = prefs.getInt('branchId');

  runApp(WaiterApp(token: token, userId: userId, branchId: branchId));
}

class WaiterApp extends StatelessWidget {
  final String? token;
  final int? userId;
  final int? branchId;

  const WaiterApp({super.key, this.token, this.userId, this.branchId});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'سُفرة - تطبيق الويتر',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFFEA580C)),
        useMaterial3: true,
      ),
      builder: (context, child) {
        return Directionality(
          textDirection: TextDirection.rtl,
          child: child ?? const SizedBox.shrink(),
        );
      },
      home: token != null && userId != null && branchId != null && branchId! > 0
          ? MainNavigator(token: token!, userId: userId!, branchId: branchId!)
          : const LoginScreen(),
    );
  }
}