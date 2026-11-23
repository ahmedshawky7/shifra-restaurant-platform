import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../services/waiter_api_service.dart';
import 'table_orders_screen.dart';

class TablesGridScreen extends StatefulWidget {
  final WaiterApiService api;
  final int branchId;
  final int refreshTick;
  final void Function(int tableId, String tableNumber) onSelectTable;

  const TablesGridScreen({
    super.key,
    required this.api,
    required this.branchId,
    this.refreshTick = 0,
    required this.onSelectTable,
  });

  @override
  State<TablesGridScreen> createState() => _TablesGridScreenState();
}

class _TablesGridScreenState extends State<TablesGridScreen> {
  List<dynamic> _tables = [];
  bool _loading = true;
  bool _showingCache = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(covariant TablesGridScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.branchId != widget.branchId || oldWidget.refreshTick != widget.refreshTick) {
      _load(silent: oldWidget.branchId == widget.branchId && _tables.isNotEmpty);
    }
  }

  Future<void> _load({bool silent = false}) async {
    final cacheKey = 'cached_tables_${widget.branchId}';
    final prefs = await SharedPreferences.getInstance();

    if (!silent && _tables.isEmpty) {
      final cached = prefs.getString(cacheKey);
      if (cached != null && mounted) {
        try {
          final decoded = jsonDecode(cached) as List;
          setState(() {
            _tables = decoded;
            _showingCache = true;
            _loading = false;
          });
        } catch (_) {}
      }
    }

    if (!silent && _tables.isEmpty && mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }

    try {
      final list = await widget.api.getTables(widget.branchId);
      await prefs.setString(cacheKey, jsonEncode(list));
      if (mounted) {
        setState(() {
          _tables = list;
          _showingCache = false;
          _error = null;
        });
      }
    } catch (e) {
      if (mounted) {
        if (_tables.isNotEmpty) {
          setState(() => _showingCache = true);
          if (!silent) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text('${e.toString()} (يتم عرض آخر نسخة محفوظة)')),
            );
          }
        } else {
          setState(() => _error = e.toString());
        }
      }
    } finally {
      if (mounted && !silent) setState(() => _loading = false);
    }
  }

  void _openOccupiedOptions(Map<String, dynamic> t) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'طاولة ${t['tableNumber']}',
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            ListTile(
              leading: const Icon(Icons.receipt_long, color: Colors.indigo),
              title: const Text('عرض / تعديل الطلبات الحالية', style: TextStyle(fontWeight: FontWeight.bold)),
              onTap: () async {
                Navigator.pop(ctx);
                await Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => TableOrdersScreen(
                      api: widget.api,
                      branchId: widget.branchId,
                      tableId: t['id'] as int,
                      tableNumber: t['tableNumber'].toString(),
                      onAddNewOrder: () => widget.onSelectTable(
                        t['id'] as int,
                        t['tableNumber'].toString(),
                      ),
                    ),
                  ),
                );
                _load();
              },
            ),
            ListTile(
              leading: const Icon(Icons.add_circle, color: Color(0xFFEA580C)),
              title: const Text('إضافة طلب جديد للطاولة', style: TextStyle(fontWeight: FontWeight.bold)),
              onTap: () {
                Navigator.pop(ctx);
                widget.onSelectTable(t['id'] as int, t['tableNumber'].toString());
              },
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_showingCache ? 'طاولات الفرع (نسخة محفوظة)' : 'طاولات الفرع'),
        backgroundColor: const Color(0xFFEA580C),
        foregroundColor: Colors.white,
        centerTitle: true,
        actions: [IconButton(onPressed: _load, icon: const Icon(Icons.refresh))],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.wifi_off, size: 52, color: Colors.redAccent),
                        const SizedBox(height: 12),
                        Text(_error!, textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.bold)),
                        const SizedBox(height: 16),
                        ElevatedButton.icon(
                          onPressed: _load,
                          icon: const Icon(Icons.refresh),
                          label: const Text('إعادة المحاولة'),
                        ),
                      ],
                    ),
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: GridView.builder(
                    padding: const EdgeInsets.all(16),
                    gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: 2,
                      crossAxisSpacing: 12,
                      mainAxisSpacing: 12,
                      childAspectRatio: 1.15,
                    ),
                    itemCount: _tables.length,
                    itemBuilder: (_, i) {
                      final t = Map<String, dynamic>.from(_tables[i]);
                      final active = t['active'] != false;
                      final occupied = t['status'] == 'OCCUPIED';

                      final Color bgColor;
                      final Color borderColor;
                      final Color textColor;
                      final IconData icon;
                      final String statusText;

                      if (!active) {
                        bgColor = Colors.grey.shade200;
                        borderColor = Colors.grey.shade400;
                        textColor = Colors.grey.shade700;
                        icon = Icons.build_circle_outlined;
                        statusText = 'معطلة / صيانة';
                      } else if (occupied) {
                        bgColor = Colors.orange.shade50;
                        borderColor = Colors.orange;
                        textColor = Colors.orange.shade800;
                        icon = Icons.table_bar;
                        statusText = 'مشغولة';
                      } else {
                        bgColor = Colors.green.shade50;
                        borderColor = Colors.green;
                        textColor = Colors.green.shade800;
                        icon = Icons.table_bar;
                        statusText = 'متاحة';
                      }

                      return InkWell(
                        onTap: !active
                            ? () {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(content: Text('هذه الطاولة معطلة للصيانة حالياً')),
                                );
                              }
                            : occupied
                                ? () => _openOccupiedOptions(t)
                                : () => widget.onSelectTable(t['id'] as int, t['tableNumber'].toString()),
                        borderRadius: BorderRadius.circular(18),
                        child: Container(
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: bgColor,
                            borderRadius: BorderRadius.circular(18),
                            border: Border.all(color: borderColor, width: 2),
                          ),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(icon, size: 36, color: borderColor),
                              const SizedBox(height: 8),
                              Text(
                                'طاولة ${t['tableNumber']}',
                                style: TextStyle(
                                  fontSize: 18,
                                  fontWeight: FontWeight.bold,
                                  color: active ? Colors.black87 : Colors.grey.shade600,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(statusText, style: TextStyle(fontWeight: FontWeight.bold, color: textColor)),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),
    );
  }
}