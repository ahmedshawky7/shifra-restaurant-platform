import 'dart:convert';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../services/waiter_api_service.dart';

class WaiterOrderScreen extends StatefulWidget {
  final WaiterApiService api;
  final int branchId;
  final int tableId;
  final String tableNumber;
  final VoidCallback onOrderSent;
  final int? editingOrderId;
  final String? initialNotes;
  final List<dynamic>? initialItems;

  const WaiterOrderScreen({
    super.key,
    required this.api,
    required this.branchId,
    required this.tableId,
    required this.tableNumber,
    required this.onOrderSent,
    this.editingOrderId,
    this.initialNotes,
    this.initialItems,
  });

  @override
  State<WaiterOrderScreen> createState() => _WaiterOrderScreenState();
}

class _WaiterOrderScreenState extends State<WaiterOrderScreen> {
  Map<String, dynamic>? _menu;
  int? _activeCatId;
  String _searchQuery = '';
  final List<Map<String, dynamic>> _cart = [];
  final TextEditingController _orderNotesCtrl = TextEditingController();
  bool _loading = true;
  bool _submitting = false;
  String? _error;
  String _idempotencyKey = _newIdempotencyKey();

  static String _newIdempotencyKey() {
    final rnd = Random.secure();
    return '${DateTime.now().microsecondsSinceEpoch}-${rnd.nextInt(1 << 31)}';
  }

  @override
  void initState() {
    super.initState();
    _orderNotesCtrl.text = widget.initialNotes ?? '';
    _loadMenu();
  }

  @override
  void dispose() {
    _orderNotesCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadMenu() async {
    final cacheKey = 'cached_menu_${widget.branchId}';
    final prefs = await SharedPreferences.getInstance();

    if (_menu == null) {
      final cached = prefs.getString(cacheKey);
      if (cached != null && mounted) {
        try {
          final m = Map<String, dynamic>.from(jsonDecode(cached));
          final cats = m['categories'] as List? ?? [];
          setState(() {
            _menu = m;
            if (cats.isNotEmpty) _activeCatId = cats[0]['id'] as int;
            _prefillInitialItems(cats);
            _loading = false;
          });
        } catch (_) {}
      }
    }

    if (_menu == null && mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }

    try {
      final m = await widget.api.getMenu(widget.branchId);
      await prefs.setString(cacheKey, jsonEncode(m));
      if (mounted) {
        setState(() {
          _menu = m;
          final cats = m['categories'] as List? ?? [];
          if (cats.isNotEmpty) _activeCatId ??= cats[0]['id'] as int;
          _prefillInitialItems(cats);
          _error = null;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          if (_menu == null) _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  void _prefillInitialItems(List<dynamic> categories) {
    if (widget.initialItems == null || widget.initialItems!.isEmpty || _cart.isNotEmpty) {
      return;
    }
    final allMenuItems = categories
        .expand((c) => (c['items'] as List? ?? []))
        .map((x) => Map<String, dynamic>.from(x))
        .toList();

    for (final raw in widget.initialItems!) {
      final oldItem = Map<String, dynamic>.from(raw);
      final menuItemId = oldItem['menuItemId'];
      final menuMatch = allMenuItems.where((x) => x['id'] == menuItemId).firstOrNull;
      if (menuMatch == null) continue;

      final variants = (menuMatch['variants'] as List? ?? [])
          .map((v) => Map<String, dynamic>.from(v))
          .toList();
      final oldVarName = oldItem['variantName']?.toString();
      final matchedVariant = oldVarName != null
          ? variants.where((v) => v['name'].toString() == oldVarName).firstOrNull
          : null;

      final addons = (menuMatch['addons'] as List? ?? [])
          .map((a) => Map<String, dynamic>.from(a))
          .toList();
      final oldAddonNames = (oldItem['addonNames'] as List? ?? []).map((e) => e.toString()).toSet();
      final matchedAddons = addons.where((a) => oldAddonNames.contains(a['name'].toString())).toList();

      final int qty = (oldItem['quantity'] as num?)?.toInt() ?? 1;
      final double unitPrice = matchedVariant != null
          ? (matchedVariant['price'] as num).toDouble()
          : (menuMatch['effectivePrice'] as num).toDouble();
      final double addonsSum = matchedAddons.fold(0.0, (s, a) => s + (a['price'] as num).toDouble());

      _cart.add({
        'uid': '$menuItemId-${DateTime.now().microsecondsSinceEpoch}-${_cart.length}',
        'menuItemId': menuItemId,
        'name': menuMatch['name'],
        'variantId': matchedVariant?['id'],
        'variantName': matchedVariant?['name'] ?? oldVarName,
        'addons': matchedAddons,
        'quantity': qty,
        'notes': oldItem['notes']?.toString() ?? '',
        'unitWithAddons': unitPrice + addonsSum,
        'lineTotal': (unitPrice + addonsSum) * qty,
      });
    }
  }

  int _itemCountInCart(int menuItemId) {
    int total = 0;
    for (final c in _cart) {
      if (c['menuItemId'] == menuItemId) {
        total += (c['quantity'] as int);
      }
    }
    return total;
  }

  int get _totalCartQty {
    int total = 0;
    for (final c in _cart) {
      total += (c['quantity'] as int);
    }
    return total;
  }

  double get _totalCartAmount {
    double sum = 0;
    for (final c in _cart) {
      sum += (c['lineTotal'] as num).toDouble();
    }
    return sum;
  }

  void _openCustomizeSheet(Map<String, dynamic> item) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (_) => _CustomizeItemSheet(
        item: item,
        onAdd: (cartEntry) {
          setState(() {
            _idempotencyKey = _newIdempotencyKey();
            _cart.add(cartEntry);
          });
          ScaffoldMessenger.of(context).hideCurrentSnackBar();
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                'تمت إضافة ${cartEntry['quantity']} × ${cartEntry['name']}${cartEntry['variantName'] != null ? ' (${cartEntry['variantName']})' : ''}',
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              backgroundColor: Colors.green.shade700,
              duration: const Duration(seconds: 2),
            ),
          );
        },
      ),
    );
  }

  void _openCartReviewSheet() {
    final isEditing = widget.editingOrderId != null;
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setModalState) {
            return DraggableScrollableSheet(
              expand: false,
              initialChildSize: 0.72,
              minChildSize: 0.45,
              maxChildSize: 0.92,
              builder: (_, scrollCtrl) => SafeArea(
                top: false,
                child: Padding(
                  padding: EdgeInsets.only(
                    left: 20,
                    right: 20,
                    top: 16,
                    bottom: MediaQuery.of(ctx).viewInsets.bottom + 16,
                  ),
                  child: ListView(
                    controller: scrollCtrl,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            isEditing
                                ? 'مراجعة تعديل الطلب'
                                : 'مراجعة طلب طاولة ${widget.tableNumber}',
                            style: const TextStyle(fontSize: 19, fontWeight: FontWeight.bold),
                          ),
                          IconButton(
                            onPressed: () {
                              FocusScope.of(ctx).unfocus();
                              Navigator.pop(ctx);
                            },
                            icon: const Icon(Icons.close),
                          ),
                        ],
                      ),
                      const Divider(),
                      if (_cart.isEmpty)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 32),
                          child: Center(child: Text('السلة فارغة')),
                        )
                      else
                        ..._cart.asMap().entries.map((entry) {
                          final i = entry.key;
                          final c = entry.value;
                          final addonsList = (c['addons'] as List<Map<String, dynamic>>);
                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      '${c['name']}${c['variantName'] != null ? ' (${c['variantName']})' : ''}',
                                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                                    ),
                                  ),
                                  Text(
                                    '${(c['lineTotal'] as num).toStringAsFixed(1)} ج',
                                    style: const TextStyle(fontWeight: FontWeight.bold, color: Color(0xFFEA580C)),
                                  ),
                                ],
                              ),
                              if (addonsList.isNotEmpty)
                                Text(
                                  '+ ${addonsList.map((a) => a['name']).join('، ')}',
                                  style: const TextStyle(fontSize: 12, color: Colors.indigo),
                                ),
                              if ((c['notes'] ?? '').toString().isNotEmpty)
                                Text(
                                  'ملاحظة: ${c['notes']}',
                                  style: const TextStyle(fontSize: 12, color: Colors.redAccent),
                                ),
                              const SizedBox(height: 6),
                              Row(
                                children: [
                                  Container(
                                    decoration: BoxDecoration(
                                      color: Colors.grey.shade100,
                                      borderRadius: BorderRadius.circular(8),
                                    ),
                                    child: Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        IconButton(
                                          visualDensity: VisualDensity.compact,
                                          icon: const Icon(Icons.remove, size: 18),
                                          onPressed: () {
                                            setState(() {
                                              _idempotencyKey = _newIdempotencyKey();
                                              if (c['quantity'] > 1) {
                                                c['quantity'] -= 1;
                                                c['lineTotal'] = c['quantity'] * c['unitWithAddons'];
                                              } else {
                                                _cart.removeAt(i);
                                              }
                                            });
                                            setModalState(() {});
                                          },
                                        ),
                                        Text('${c['quantity']}', style: const TextStyle(fontWeight: FontWeight.bold)),
                                        IconButton(
                                          visualDensity: VisualDensity.compact,
                                          icon: const Icon(Icons.add, size: 18),
                                          onPressed: () {
                                            setState(() {
                                              _idempotencyKey = _newIdempotencyKey();
                                              c['quantity'] += 1;
                                              c['lineTotal'] = c['quantity'] * c['unitWithAddons'];
                                            });
                                            setModalState(() {});
                                          },
                                        ),
                                      ],
                                    ),
                                  ),
                                  const Spacer(),
                                  TextButton.icon(
                                    onPressed: () {
                                      setState(() {
                                        _idempotencyKey = _newIdempotencyKey();
                                        _cart.removeAt(i);
                                      });
                                      setModalState(() {});
                                    },
                                    icon: const Icon(Icons.delete_outline, color: Colors.red, size: 18),
                                    label: const Text('حذف', style: TextStyle(color: Colors.red)),
                                  ),
                                ],
                              ),
                              const Divider(height: 16),
                            ],
                          );
                        }),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _orderNotesCtrl,
                        decoration: const InputDecoration(
                          labelText: 'ملاحظات عامة للطلب',
                          border: OutlineInputBorder(),
                          isDense: true,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('الإجمالي (قبل الضريبة والخدمة):', style: TextStyle(fontWeight: FontWeight.bold)),
                          Text(
                            '${_totalCartAmount.toStringAsFixed(1)} ج',
                            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900, color: Color(0xFFEA580C)),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        height: 50,
                        child: ElevatedButton.icon(
                          onPressed: (_cart.isEmpty || _submitting)
                              ? null
                              : () async {
                                  FocusScope.of(ctx).unfocus();
                                  Navigator.pop(ctx);
                                  await _submitOrder();
                                },
                          icon: const Icon(Icons.send),
                          label: Text(
                            _submitting
                                ? 'جاري الحفظ...'
                                : (isEditing ? 'حفظ التعديلات وإرسال للمطبخ' : 'تأكيد وإرسال للمطبخ'),
                            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                          ),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.green.shade700,
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        );
      },
    );
  }

  Future<void> _submitOrder() async {
    if (_cart.isEmpty || _submitting) return;
    setState(() => _submitting = true);
    try {
      final itemsPayload = _cart.map((c) {
        final addonsList = c['addons'] as List<Map<String, dynamic>>;
        return {
          'menuItemId': c['menuItemId'],
          'variantId': c['variantId'],
          'quantity': c['quantity'],
          'notes': (c['notes'] ?? '').toString().isNotEmpty ? c['notes'] : null,
          'addonIds': addonsList.map((a) => a['id']).toList(),
        };
      }).toList();

      final notesStr = _orderNotesCtrl.text.trim().isNotEmpty ? _orderNotesCtrl.text.trim() : null;

      if (widget.editingOrderId != null) {
        await widget.api.modifyPendingOrder(
          branchId: widget.branchId,
          orderId: widget.editingOrderId!,
          notes: notesStr,
          items: itemsPayload,
        );
      } else {
        await widget.api.placeTableOrder(
          branchId: widget.branchId,
          tableId: widget.tableId,
          notes: notesStr,
          items: itemsPayload,
          idempotencyKey: _idempotencyKey,
        );
      }

      if (!mounted) return;
      _cart.clear();
      _orderNotesCtrl.clear();
      _idempotencyKey = _newIdempotencyKey();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(widget.editingOrderId != null
              ? 'تم تعديل الطلب وإخطار المطبخ!'
              : 'تم إرسال الطلب للمطبخ بنجاح!'),
          backgroundColor: Colors.green,
        ),
      );
      widget.onOrderSent();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Scaffold(body: Center(child: CircularProgressIndicator()));

    if (_error != null) {
      return Scaffold(
        appBar: AppBar(
          title: Text('طلب لطاولة ${widget.tableNumber}'),
          backgroundColor: const Color(0xFFEA580C),
          foregroundColor: Colors.white,
          centerTitle: true,
        ),
        body: Center(
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
                  onPressed: _loadMenu,
                  icon: const Icon(Icons.refresh),
                  label: const Text('إعادة المحاولة'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final categories = (_menu?['categories'] as List? ?? []);
    final List<dynamic> displayedItems;
    if (_searchQuery.trim().isNotEmpty) {
      final q = _searchQuery.trim().toLowerCase();
      displayedItems = categories
          .expand((c) => (c['items'] as List? ?? []))
          .where((it) => it['name'].toString().toLowerCase().contains(q))
          .toList();
    } else {
      final activeCat = categories.where((c) => c['id'] == _activeCatId).firstOrNull;
      displayedItems = (activeCat?['items'] as List? ?? []);
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.editingOrderId != null
            ? 'تعديل طلب طاولة ${widget.tableNumber}'
            : 'طلب لطاولة ${widget.tableNumber}'),
        backgroundColor: const Color(0xFFEA580C),
        foregroundColor: Colors.white,
        centerTitle: true,
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 10, 12, 4),
            child: TextField(
              onChanged: (v) => setState(() => _searchQuery = v),
              decoration: InputDecoration(
                hintText: 'ابحث عن صنف بالاسم...',
                prefixIcon: const Icon(Icons.search),
                isDense: true,
                filled: true,
                fillColor: Colors.white,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(14)),
              ),
            ),
          ),
          if (_searchQuery.trim().isEmpty)
            SizedBox(
              height: 52,
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                itemCount: categories.length,
                itemBuilder: (_, i) {
                  final c = categories[i];
                  final selected = c['id'] == _activeCatId;
                  return Padding(
                    padding: const EdgeInsets.only(left: 8),
                    child: ChoiceChip(
                      label: Text(c['name']),
                      selected: selected,
                      onSelected: (_) => setState(() => _activeCatId = c['id'] as int),
                    ),
                  );
                },
              ),
            ),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: displayedItems.length,
              itemBuilder: (_, i) {
                final it = Map<String, dynamic>.from(displayedItems[i]);
                final avail = it['available'] == true;
                final variants = (it['variants'] as List? ?? []);
                final inCartCount = _itemCountInCart(it['id'] as int);

                return Card(
                  margin: const EdgeInsets.only(bottom: 10),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                  child: ListTile(
                    onTap: avail ? () => _openCustomizeSheet(it) : null,
                    title: Row(
                      children: [
                        Expanded(
                          child: Text(it['name'].toString(), style: const TextStyle(fontWeight: FontWeight.bold)),
                        ),
                        if (inCartCount > 0)
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                            decoration: BoxDecoration(
                              color: Colors.green.shade100,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Text(
                              'مطلوب: $inCartCount',
                              style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Colors.green.shade800),
                            ),
                          ),
                      ],
                    ),
                    subtitle: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          variants.isNotEmpty
                              ? '${it['effectivePrice']} ج • (${variants.length} أحجام متاحة)'
                              : '${it['effectivePrice']} ج',
                          style: const TextStyle(color: Color(0xFFEA580C), fontWeight: FontWeight.bold),
                        ),
                        if (!avail)
                          const Text('غير متوفر حالياً', style: TextStyle(color: Colors.red, fontSize: 12)),
                      ],
                    ),
                    trailing: IconButton(
                      icon: Icon(Icons.add_circle, size: 32, color: avail ? const Color(0xFFEA580C) : Colors.grey),
                      onPressed: avail ? () => _openCustomizeSheet(it) : null,
                    ),
                  ),
                );
              },
            ),
          ),
          if (_cart.isNotEmpty)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: Colors.orange.shade50,
                border: Border(top: BorderSide(color: Colors.orange.shade200)),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text('الأصناف المختارة: $_totalCartQty',
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                        Text('الإجمالي: ${_totalCartAmount.toStringAsFixed(1)} ج',
                            style: const TextStyle(
                                fontWeight: FontWeight.w900, color: Color(0xFFEA580C), fontSize: 16)),
                      ],
                    ),
                  ),
                  ElevatedButton.icon(
                    onPressed: _submitting ? null : _openCartReviewSheet,
                    icon: const Icon(Icons.receipt_long),
                    label: Text(
                      widget.editingOrderId != null ? 'مراجعة وحفظ التعديل' : 'مراجعة وإرسال',
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFFEA580C),
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _CustomizeItemSheet extends StatefulWidget {
  final Map<String, dynamic> item;
  final void Function(Map<String, dynamic> cartEntry) onAdd;

  const _CustomizeItemSheet({
    required this.item,
    required this.onAdd,
  });

  @override
  State<_CustomizeItemSheet> createState() => _CustomizeItemSheetState();
}

class _CustomizeItemSheetState extends State<_CustomizeItemSheet> {
  late final List<Map<String, dynamic>> _variants;
  late final List<Map<String, dynamic>> _addons;
  Map<String, dynamic>? _selectedVariant;
  final List<Map<String, dynamic>> _selectedAddons = [];
  int _qty = 1;
  final TextEditingController _noteCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    _variants = (widget.item['variants'] as List? ?? [])
        .map((v) => Map<String, dynamic>.from(v))
        .toList();
    _addons = (widget.item['addons'] as List? ?? [])
        .where((a) => a['available'] == true)
        .map((a) => Map<String, dynamic>.from(a))
        .toList();

    if (_variants.isNotEmpty) {
      _selectedVariant = _variants.firstWhere(
        (v) => v['isDefault'] == true,
        orElse: () => _variants.first,
      );
    }
  }

  @override
  void dispose() {
    _noteCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final double unitPrice = _selectedVariant != null
        ? (_selectedVariant!['price'] as num).toDouble()
        : (widget.item['effectivePrice'] as num).toDouble();
    final double addonsSum = _selectedAddons.fold(
      0.0,
      (s, a) => s + (a['price'] as num).toDouble(),
    );
    final double previewTotal = (unitPrice + addonsSum) * _qty;

    return Padding(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Text(
                    widget.item['name'].toString(),
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                ),
                IconButton(
                  onPressed: () {
                    FocusScope.of(context).unfocus();
                    Navigator.pop(context);
                  },
                  icon: const Icon(Icons.close),
                ),
              ],
            ),
            if ((widget.item['description'] ?? '').toString().isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Text(
                  widget.item['description'].toString(),
                  style: const TextStyle(color: Colors.grey, fontSize: 13),
                ),
              ),
            if (_variants.isNotEmpty) ...[
              const Text('اختر الحجم / الخيار:', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              ..._variants.map((v) {
                final isSel = _selectedVariant?['id'] == v['id'];
                return Padding(
                  padding: const EdgeInsets.only(bottom: 6),
                  child: Material(
                    color: isSel ? Colors.orange.shade50 : Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                      side: BorderSide(
                        color: isSel ? const Color(0xFFEA580C) : Colors.grey.shade300,
                      ),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: ListTile(
                      dense: true,
                      onTap: () => setState(() => _selectedVariant = v),
                      leading: Icon(
                        isSel ? Icons.radio_button_checked : Icons.radio_button_off,
                        color: isSel ? const Color(0xFFEA580C) : Colors.grey,
                      ),
                      title: Text(v['name'].toString(), style: const TextStyle(fontWeight: FontWeight.bold)),
                      trailing: Text(
                        '${v['price']} ج',
                        style: const TextStyle(fontWeight: FontWeight.bold, color: Color(0xFFEA580C)),
                      ),
                    ),
                  ),
                );
              }),
              const SizedBox(height: 10),
            ],
            if (_addons.isNotEmpty) ...[
              const Text('إضافات اختيارية:', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              ..._addons.map((a) {
                final checked = _selectedAddons.any((x) => x['id'] == a['id']);
                return Padding(
                  padding: const EdgeInsets.only(bottom: 6),
                  child: Material(
                    color: checked ? Colors.indigo.shade50 : Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                      side: BorderSide(
                        color: checked ? Colors.indigo : Colors.grey.shade300,
                      ),
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: CheckboxListTile(
                      dense: true,
                      value: checked,
                      onChanged: (val) {
                        setState(() {
                          if (val == true) {
                            _selectedAddons.add(a);
                          } else {
                            _selectedAddons.removeWhere((x) => x['id'] == a['id']);
                          }
                        });
                      },
                      title: Text(a['name'].toString(), style: const TextStyle(fontWeight: FontWeight.bold)),
                      secondary: Text(
                        '+${a['price']} ج',
                        style: const TextStyle(color: Colors.indigo, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ),
                );
              }),
              const SizedBox(height: 10),
            ],
            TextField(
              controller: _noteCtrl,
              decoration: const InputDecoration(
                labelText: 'ملاحظة للمطبخ (اختياري)',
                hintText: 'مثال: بدون بصل، تسوية زيادة...',
                border: OutlineInputBorder(),
                isDense: true,
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Container(
                  decoration: BoxDecoration(
                    color: Colors.grey.shade200,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      IconButton(
                        onPressed: () => setState(() {
                          if (_qty > 1) _qty--;
                        }),
                        icon: const Icon(Icons.remove),
                      ),
                      Text('$_qty', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                      IconButton(
                        onPressed: () => setState(() => _qty++),
                        icon: const Icon(Icons.add),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: SizedBox(
                    height: 48,
                    child: ElevatedButton(
                      onPressed: () {
                        final entry = <String, dynamic>{
                          'uid': '${widget.item['id']}-${DateTime.now().microsecondsSinceEpoch}',
                          'menuItemId': widget.item['id'],
                          'name': widget.item['name'],
                          'variantId': _selectedVariant?['id'],
                          'variantName': _selectedVariant?['name'],
                          'addons': List<Map<String, dynamic>>.from(_selectedAddons),
                          'quantity': _qty,
                          'notes': _noteCtrl.text.trim(),
                          'unitWithAddons': unitPrice + addonsSum,
                          'lineTotal': previewTotal,
                        };
                        FocusScope.of(context).unfocus();
                        Navigator.pop(context);
                        widget.onAdd(entry);
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFEA580C),
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                      child: Text(
                        'إضافة (${previewTotal.toStringAsFixed(1)} ج)',
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}