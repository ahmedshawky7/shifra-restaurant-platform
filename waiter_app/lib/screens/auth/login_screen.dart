import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../../config/api_config.dart';
import '../main_navigator.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _usernameCtrl = TextEditingController(text: 'waiter1');
  final _passwordCtrl = TextEditingController();
  bool _loading = false;
  bool _obscure = true;

  @override
  void dispose() {
    _usernameCtrl.dispose();
    _passwordCtrl.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    setState(() => _loading = true);
    try {
      final res = await http.post(
        Uri.parse('${ApiConfig.baseUrl}/auth/login'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'username': _usernameCtrl.text.trim(),
          'password': _passwordCtrl.text,
        }),
      );

      if (res.statusCode == 200) {
        final data = jsonDecode(utf8.decode(res.bodyBytes));
        final role = data['role'];
        if (role != 'WAITER' && role != 'CASHIER' && role != 'ORG_ADMIN') {
          _showMsg('هذا التطبيق مخصص للويتر وطاقم الصالة فقط.');
          return;
        }
        final token = data['token'] as String;
        final userId = data['userId'] as int;
        int branchId = (data['branchId'] ?? 0) as int;

        final bRes = await http.get(
          Uri.parse('${ApiConfig.baseUrl}/branches/my'),
          headers: {'Authorization': 'Bearer $token'},
        );
        List<dynamic> branches = [];
        if (bRes.statusCode == 200) {
          branches = jsonDecode(utf8.decode(bRes.bodyBytes)) as List;
          if (branchId == 0 && branches.isNotEmpty) {
            branchId = branches[0]['id'] as int;
          }
        }

        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('token', token);
        await prefs.setInt('userId', userId);
        await prefs.setInt('branchId', branchId);
        if (branches.isNotEmpty) {
          await prefs.setString('branchesJson', jsonEncode(branches));
        }

        if (!mounted) return;
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (_) => MainNavigator(token: token, userId: userId, branchId: branchId),
          ),
        );
      } else {
        _showMsg('اسم المستخدم أو كلمة المرور غير صحيحة');
      }
    } catch (_) {
      _showMsg('تعذر الاتصال بالسيرفر (${ApiConfig.host})');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _showMsg(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              const Icon(Icons.restaurant_menu, size: 80, color: Color(0xFFEA580C)),
              const SizedBox(height: 14),
              const Text('تطبيق الويتر - سُفرة', style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold)),
              const SizedBox(height: 32),
              TextField(
                controller: _usernameCtrl,
                decoration: const InputDecoration(
                  labelText: 'اسم المستخدم',
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.person),
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _passwordCtrl,
                obscureText: _obscure,
                decoration: InputDecoration(
                  labelText: 'كلمة المرور',
                  border: const OutlineInputBorder(),
                  prefixIcon: const Icon(Icons.lock),
                  suffixIcon: IconButton(
                    icon: Icon(_obscure ? Icons.visibility_off : Icons.visibility),
                    onPressed: () => setState(() => _obscure = !_obscure),
                  ),
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                height: 52,
                child: ElevatedButton(
                  onPressed: _loading ? null : _login,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFFEA580C),
                    foregroundColor: Colors.white,
                  ),
                  child: _loading
                      ? const CircularProgressIndicator(color: Colors.white)
                      : const Text('تسجيل الدخول', style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}