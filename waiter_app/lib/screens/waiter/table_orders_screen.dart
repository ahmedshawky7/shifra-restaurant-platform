import 'package:flutter/material.dart';
import '../../services/waiter_api_service.dart';
import 'waiter_order_screen.dart';

class TableOrdersScreen extends StatefulWidget {
  final WaiterApiService api;
  final int branchId;
  final int tableId;
  final String tableNumber;
  final VoidCallback onAddNewOrder;

  const TableOrdersScreen({
    super.key,
    required this.api,
    required this.branchId,
    required this.tableId,
    required this.tableNumber,
    required this.onAddNewOrder,
  });

  @override
  State<TableOrdersScreen> createState() => _TableOrdersScreenState();
}

class _TableOrdersScreenState extends State<TableOrdersScreen> {
  List<dynamic> _orders = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final list = await widget.api.getTableOrders(widget.branchId, widget.tableId);
      if (mounted) {
        setState(() {
          _orders = list;
          _error = null;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _cancelOrder(int orderId) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('إلغاء الطلب'),
        content: const Text('هل تريد إلغاء هذا الطلب قبل دخوله المطبخ؟'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('لا')),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: ElevatedButton.styleFrom(backgroundColor: Colors.red, foregroundColor: Colors.white),
            child: const Text('تأكيد الإلغاء'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await widget.api.cancelPendingOrder(widget.branchId, orderId);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تم إلغاء الطلب بنجاح'), backgroundColor: Colors.green),
      );
      await _load();
      if (_orders.isEmpty && mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
    }
  }

  String _statusLabel(String s) {
    switch (s) {
      case 'PENDING': return 'في انتظار المطبخ (قابل للتعديل)';
      case 'PREPARING': return 'قيد التحضير في المطبخ';
      case 'READY': return 'جاهز للتقديم';
      case 'SERVED': return 'تم التقديم';
      default: return s;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('طلبات طاولة ${widget.tableNumber}'),
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
              : _orders.isEmpty
                  ? const Center(child: Text('لا توجد طلبات نشطة على هذه الطاولة'))
                  : ListView.builder(
                      padding: const EdgeInsets.all(16),
                      itemCount: _orders.length,
                      itemBuilder: (_, i) {
                        final o = _orders[i];
                        final status = o['status'].toString();
                        final isPending = status == 'PENDING';
                        final items = (o['items'] as List? ?? []);

                        return Card(
                          margin: const EdgeInsets.only(bottom: 14),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Text('طلب #${o['orderNumber']}',
                                        style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                                    Text('${o['totalAmount']} ج',
                                        style: const TextStyle(
                                            fontSize: 16, fontWeight: FontWeight.bold, color: Color(0xFFEA580C))),
                                  ],
                                ),
                                const SizedBox(height: 6),
                                Text(
                                  _statusLabel(status),
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    color: isPending ? Colors.amber.shade800 : Colors.indigo,
                                  ),
                                ),
                                const Divider(height: 18),
                                ...items.map((it) => Padding(
                                      padding: const EdgeInsets.only(bottom: 4),
                                      child: Text(
                                        '• ${it['quantity']}× ${it['itemName']}${it['variantName'] != null ? ' (${it['variantName']})' : ''}',
                                        style: const TextStyle(fontWeight: FontWeight.w600),
                                      ),
                                    )),
                                if (isPending) ...[
                                  const SizedBox(height: 12),
                                  Row(
                                    children: [
                                      Expanded(
                                        child: OutlinedButton.icon(
                                          onPressed: () async {
                                            final updated = await Navigator.push<bool>(
                                              context,
                                              MaterialPageRoute(
                                                builder: (_) => WaiterOrderScreen(
                                                  api: widget.api,
                                                  branchId: widget.branchId,
                                                  tableId: widget.tableId,
                                                  tableNumber: widget.tableNumber,
                                                  editingOrderId: o['orderId'] as int,
                                                  initialNotes: o['notes']?.toString(),
                                                  initialItems: items,
                                                  onOrderSent: () => Navigator.pop(context, true),
                                                ),
                                              ),
                                            );
                                            if (updated == true) _load();
                                          },
                                          icon: const Icon(Icons.edit, size: 18),
                                          label: const Text('تعديل الطلب'),
                                        ),
                                      ),
                                      const SizedBox(width: 10),
                                      Expanded(
                                        child: OutlinedButton.icon(
                                          onPressed: () => _cancelOrder(o['orderId'] as int),
                                          icon: const Icon(Icons.cancel_outlined, color: Colors.red, size: 18),
                                          label: const Text('إلغاء الطلب', style: TextStyle(color: Colors.red)),
                                          style: OutlinedButton.styleFrom(side: const BorderSide(color: Colors.red)),
                                        ),
                                      ),
                                    ],
                                  ),
                                ],
                              ],
                            ),
                          ),
                        );
                      },
                    ),
      bottomNavigationBar: Padding(
        padding: const EdgeInsets.all(16),
        child: ElevatedButton.icon(
          onPressed: () {
            Navigator.pop(context);
            widget.onAddNewOrder();
          },
          icon: const Icon(Icons.add_shopping_cart),
          label: const Text('إضافة طلب جديد لنفس الطاولة', style: TextStyle(fontWeight: FontWeight.bold)),
          style: ElevatedButton.styleFrom(
            minimumSize: const Size(double.infinity, 50),
            backgroundColor: const Color(0xFFEA580C),
            foregroundColor: Colors.white,
          ),
        ),
      ),
    );
  }
}