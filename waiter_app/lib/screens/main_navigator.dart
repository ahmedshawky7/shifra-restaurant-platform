import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';
import '../config/api_config.dart';
import '../services/waiter_api_service.dart';
import 'auth/login_screen.dart';
import 'waiter/tables_grid_screen.dart';
import 'waiter/waiter_order_screen.dart';
import 'waiter/ready_orders_screen.dart';

enum WsConnState { connected, reconnecting, disconnected }

class MainNavigator extends StatefulWidget {
  final String token;
  final int userId;
  final int branchId;

  const MainNavigator({
    super.key,
    required this.token,
    required this.userId,
    required this.branchId,
  });

  @override
  State<MainNavigator> createState() => _MainNavigatorState();
}

class _MainNavigatorState extends State<MainNavigator> {
  late final WaiterApiService _api;
  late int _activeBranchId;
  List<Map<String, dynamic>> _branches = [];

  int _index = 0;
  int? _selectedTableId;
  String _selectedTableNumber = '';
  StompClient? _stomp;
  WsConnState _wsState = WsConnState.reconnecting;
  int _readyBadgeCount = 0;
  int _refreshTick = 0;

  @override
  void initState() {
    super.initState();
    _api = WaiterApiService(token: widget.token);
    _activeBranchId = widget.branchId;
    _loadBranches();
    _connectWaiterWs();
  }

  Future<void> _loadBranches() async {
    final prefs = await SharedPreferences.getInstance();
    final cached = prefs.getString('branchesJson');
    if (cached != null && mounted) {
      try {
        final decoded = jsonDecode(cached) as List;
        setState(() {
          _branches = decoded.map((e) => Map<String, dynamic>.from(e)).toList();
        });
      } catch (_) {}
    }

    try {
      final live = await _api.getMyBranches();
      if (!mounted) return;
      final list = live.map((e) => Map<String, dynamic>.from(e)).toList();
      await prefs.setString('branchesJson', jsonEncode(list));
      setState(() => _branches = list);
    } catch (_) {}
  }

  void _connectWaiterWs() {
    _stomp?.deactivate();
    if (mounted) setState(() => _wsState = WsConnState.reconnecting);
    final branchId = _activeBranchId;
    _stomp = StompClient(
      config: StompConfig(
        url: ApiConfig.wsUrl,
        reconnectDelay: const Duration(seconds: 5),
        stompConnectHeaders: {'Authorization': 'Bearer ${widget.token}'},
        webSocketConnectHeaders: {'Authorization': 'Bearer ${widget.token}'},
        onConnect: (_) {
          if (mounted) setState(() => _wsState = WsConnState.connected);
          _stomp?.subscribe(
            destination: '/topic/branch/$branchId/waiter',
            callback: (frame) {
              if (frame.body == null || !mounted) return;
              final ev = jsonDecode(frame.body!);
              final type = ev['eventType']?.toString() ?? '';

              setState(() {
                _refreshTick++;
                if (type == 'ORDER_READY') {
                  _readyBadgeCount++;
                }
              });

              String msg = '';
              if (type == 'ORDER_READY') {
                msg = 'طلب رقم #${ev['orderNumber']} جاهز للتسليم (${ev['tableNumber'] ?? 'تيك أواي'})';
              } else if (type == 'CALL_WAITER') {
                msg = 'نداء ويتر من طاولة ${ev['tableNumber']}!';
              } else if (type == 'REQUEST_BILL') {
                msg = 'طاولة ${ev['tableNumber']} تطلب الحساب!';
              }
              if (msg.isNotEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(msg, style: const TextStyle(fontWeight: FontWeight.bold)),
                    backgroundColor: const Color(0xFFEA580C),
                    duration: const Duration(seconds: 4),
                  ),
                );
              }
            },
          );
        },
        onDisconnect: (_) {
          if (mounted) setState(() => _wsState = WsConnState.reconnecting);
        },
        onWebSocketError: (_) {
          if (mounted) setState(() => _wsState = WsConnState.disconnected);
        },
        onStompError: (_) {
          if (mounted) setState(() => _wsState = WsConnState.disconnected);
        },
      ),
    );
    _stomp?.activate();
  }

  Future<void> _switchBranch(int newBranchId) async {
    if (newBranchId == _activeBranchId) return;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('branchId', newBranchId);

    setState(() {
      _activeBranchId = newBranchId;
      _selectedTableId = null;
      _selectedTableNumber = '';
      _readyBadgeCount = 0;
      _refreshTick++;
    });
    _connectWaiterWs();

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('تم تبديل الفرع بنجاح')),
    );
  }

  Future<void> _showChangePasswordDialog() async {
    final currentCtrl = TextEditingController();
    final newCtrl = TextEditingController();
    final confirmCtrl = TextEditingController();
    bool saving = false;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) => AlertDialog(
          title: const Text('تغيير كلمة المرور'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: currentCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'كلمة المرور الحالية',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: newCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'كلمة المرور الجديدة (6 أحرف فأكثر)',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: confirmCtrl,
                  obscureText: true,
                  decoration: const InputDecoration(
                    labelText: 'تأكيد كلمة المرور الجديدة',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: saving ? null : () => Navigator.pop(ctx),
              child: const Text('إلغاء'),
            ),
            ElevatedButton(
              onPressed: saving
                  ? null
                  : () async {
                      final curr = currentCtrl.text;
                      final next = newCtrl.text;
                      final confirm = confirmCtrl.text;
                      if (curr.isEmpty || next.length < 6) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('يجب إدخال كلمة المرور الحالية وكلمة جديدة لا تقل عن 6 أحرف')),
                        );
                        return;
                      }
                      if (next != confirm) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('تأكيد كلمة المرور غير متطابق')),
                        );
                        return;
                      }
                      setModalState(() => saving = true);
                      try {
                        await _api.changePassword(curr, next);
                        if (!ctx.mounted) return;
                        Navigator.pop(ctx);
                        if (!mounted) return;
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('تم تغيير كلمة المرور بنجاح'),
                            backgroundColor: Colors.green,
                          ),
                        );
                      } catch (e) {
                        setModalState(() => saving = false);
                        if (!mounted) return;
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text(e.toString())),
                        );
                      }
                    },
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFFEA580C),
                foregroundColor: Colors.white,
              ),
              child: Text(saving ? 'جاري الحفظ...' : 'حفظ'),
            ),
          ],
        ),
      ),
    );

    currentCtrl.dispose();
    newCtrl.dispose();
    confirmCtrl.dispose();
  }

  @override
  void dispose() {
    _stomp?.deactivate();
    super.dispose();
  }

  Future<void> _logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();
    if (!mounted) return;
    Navigator.pushAndRemoveUntil(
      context,
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (_) => false,
    );
  }

  Widget _buildConnectionBanner() {
    if (_wsState == WsConnState.connected) {
      return const SizedBox.shrink();
    }
    final isOffline = _wsState == WsConnState.disconnected;
    return Material(
      color: isOffline ? Colors.red.shade700 : Colors.amber.shade700,
      child: SafeArea(
        bottom: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(isOffline ? Icons.wifi_off : Icons.sync, color: Colors.white, size: 16),
              const SizedBox(width: 8),
              Text(
                isOffline ? 'غير متصل بالسيرفر — جاري عرض البيانات المحفوظة' : 'جارٍ إعادة الاتصال اللحظي بالسيرفر...',
                style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final screens = [
      TablesGridScreen(
        api: _api,
        branchId: _activeBranchId,
        refreshTick: _refreshTick,
        onSelectTable: (tableId, tableNum) {
          setState(() {
            _selectedTableId = tableId;
            _selectedTableNumber = tableNum;
            _index = 1;
          });
        },
      ),
      _selectedTableId != null
          ? WaiterOrderScreen(
              api: _api,
              branchId: _activeBranchId,
              tableId: _selectedTableId!,
              tableNumber: _selectedTableNumber,
              onOrderSent: () => setState(() {
                _refreshTick++;
                _index = 0;
              }),
            )
          : Center(
              child: ElevatedButton(
                onPressed: () => setState(() => _index = 0),
                child: const Text('اختر طاولة أولاً من التبويب الأول'),
              ),
            ),
      ReadyOrdersScreen(
        api: _api,
        branchId: _activeBranchId,
        refreshTick: _refreshTick,
        onClearBadge: () => setState(() => _readyBadgeCount = 0),
      ),
      Scaffold(
        appBar: AppBar(
          title: const Text('حسابي وإعدادات الفرع'),
          backgroundColor: const Color(0xFFEA580C),
          foregroundColor: Colors.white,
          centerTitle: true,
        ),
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              margin: const EdgeInsets.only(bottom: 16),
              decoration: BoxDecoration(
                color: _wsState == WsConnState.connected ? Colors.green.shade50 : Colors.amber.shade50,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: _wsState == WsConnState.connected ? Colors.green.shade300 : Colors.amber.shade300,
                ),
              ),
              child: Row(
                children: [
                  Icon(
                    _wsState == WsConnState.connected ? Icons.cloud_done : Icons.cloud_off,
                    color: _wsState == WsConnState.connected ? Colors.green : Colors.amber.shade800,
                  ),
                  const SizedBox(width: 10),
                  Text(
                    _wsState == WsConnState.connected
                        ? 'الاتصال اللحظي بالسيرفر: متصل'
                        : _wsState == WsConnState.reconnecting
                            ? 'الاتصال اللحظي: جارٍ إعادة الاتصال...'
                            : 'الاتصال اللحظي: غير متصل',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                ],
              ),
            ),
            if (_branches.isNotEmpty) ...[
              const Text(
                'الفرع النشط حالياً',
                style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              DropdownButtonFormField<int>(
                key: ValueKey(_activeBranchId),
                initialValue: _branches.any((b) => b['id'] == _activeBranchId)
                    ? _activeBranchId
                    : (_branches.first['id'] as int),
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  prefixIcon: Icon(Icons.storefront),
                ),
                items: _branches
                    .map(
                      (b) => DropdownMenuItem<int>(
                        value: b['id'] as int,
                        child: Text(b['name'].toString()),
                      ),
                    )
                    .toList(),
                onChanged: (val) {
                  if (val != null) _switchBranch(val);
                },
              ),
              const SizedBox(height: 24),
            ],
            ListTile(
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14),
                side: BorderSide(color: Colors.grey.shade300),
              ),
              leading: const Icon(Icons.lock_reset, color: Color(0xFFEA580C)),
              title: const Text('تغيير كلمة المرور', style: TextStyle(fontWeight: FontWeight.bold)),
              trailing: const Icon(Icons.arrow_forward_ios, size: 16),
              onTap: _showChangePasswordDialog,
            ),
            const SizedBox(height: 24),
            SizedBox(
              height: 50,
              child: ElevatedButton.icon(
                onPressed: _logout,
                icon: const Icon(Icons.logout),
                label: const Text('تسجيل الخروج', style: TextStyle(fontWeight: FontWeight.bold)),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.red,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                ),
              ),
            ),
          ],
        ),
      ),
    ];

    return Scaffold(
      body: Column(
        children: [
          _buildConnectionBanner(),
          Expanded(child: screens[_index]),
        ],
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _index,
        onTap: (i) {
          setState(() {
            _index = i;
            if (i == 2) _readyBadgeCount = 0;
          });
        },
        selectedItemColor: const Color(0xFFEA580C),
        unselectedItemColor: Colors.grey,
        type: BottomNavigationBarType.fixed,
        items: [
          const BottomNavigationBarItem(icon: Icon(Icons.table_restaurant), label: 'الطاولات'),
          const BottomNavigationBarItem(icon: Icon(Icons.add_shopping_cart), label: 'طلب جديد'),
          BottomNavigationBarItem(
            icon: Badge(
              isLabelVisible: _readyBadgeCount > 0,
              label: Text('$_readyBadgeCount'),
              child: const Icon(Icons.notifications_active),
            ),
            label: 'جاهز للتسليم',
          ),
          const BottomNavigationBarItem(icon: Icon(Icons.person), label: 'حسابي'),
        ],
      ),
    );
  }
}