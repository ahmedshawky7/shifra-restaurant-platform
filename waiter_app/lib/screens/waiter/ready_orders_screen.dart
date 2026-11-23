import 'package:flutter/material.dart';

import '../../services/waiter_api_service.dart';

class ReadyOrdersScreen extends StatefulWidget {
  final WaiterApiService api;
  final int branchId;
  final int refreshTick;
  final VoidCallback onClearBadge;

  const ReadyOrdersScreen({
    super.key,
    required this.api,
    required this.branchId,
    this.refreshTick = 0,
    required this.onClearBadge,
  });

  @override
  State<ReadyOrdersScreen> createState() => _ReadyOrdersScreenState();
}

class _ReadyOrdersScreenState extends State<ReadyOrdersScreen> {
  List<dynamic> _orders = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(covariant ReadyOrdersScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.branchId != widget.branchId || oldWidget.refreshTick != widget.refreshTick) {
      _load(silent: oldWidget.branchId == widget.branchId && _orders.isNotEmpty);
    }
  }

  Future<void> _load({bool silent = false}) async {
    if (!silent && mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final list = await widget.api.getReadyOrders(widget.branchId);
      if (mounted) {
        setState(() {
          _orders = list;
          _error = null;
        });
        widget.onClearBadge();
      }
    } catch (e) {
      if (mounted) {
        if (silent && _orders.isNotEmpty) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
        } else {
          setState(() => _error = e.toString());
        }
      }
    } finally {
      if (mounted && !silent) setState(() => _loading = false);
    }
  }

  Future<void> _serve(int orderId) async {
    try {
      await widget.api.markServed(widget.branchId, orderId);
      _load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('طلبات جاهزة للتسليم'),
        backgroundColor: const Color(0xFFEA580C),
        foregroundColor: Colors.white,
        centerTitle: true,
        actions: [
          IconButton(onPressed: _load, icon: const Icon(Icons.refresh)),
        ],
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
                  ? const Center(
                      child: Text(
                        'لا توجد طلبات جاهزة حالياً',
                        style: TextStyle(fontSize: 18),
                      ),
                    )
                  : RefreshIndicator(
                      onRefresh: _load,
                      child: ListView.builder(
                        padding: const EdgeInsets.all(16),
                        itemCount: _orders.length,
                        itemBuilder: (_, i) {
                          final o = _orders[i];
                          return Card(
                            margin: const EdgeInsets.only(bottom: 12),
                            child: ListTile(
                              title: Text(
                                'طلب #${o['orderNumber']} — ${o['tableNumber'] != null ? 'طاولة ${o['tableNumber']}' : 'تيك أواي'}',
                                style: const TextStyle(fontWeight: FontWeight.bold),
                              ),
                              subtitle: Text(
                                (o['items'] as List)
                                    .map((x) => '${x['quantity']}x ${x['itemName']}')
                                    .join(' • '),
                              ),
                              trailing: ElevatedButton(
                                onPressed: () => _serve(o['orderId'] as int),
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: Colors.green,
                                  foregroundColor: Colors.white,
                                ),
                                child: const Text('تم التسليم'),
                              ),
                            ),
                          );
                        },
                      ),
                    ),
    );
  }
}