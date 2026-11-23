import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/api_config.dart';

class ApiException implements Exception {
  final int statusCode;
  final String message;
  ApiException(this.statusCode, this.message);
  @override
  String toString() => message;
}

class WaiterApiService {
  final String token;
  WaiterApiService({required this.token});

  Map<String, String> get _headers => {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'Authorization': 'Bearer $token',
  };

  String _parseError(http.Response res, String fallback) {
    try {
      final decoded = jsonDecode(utf8.decode(res.bodyBytes));
      if (decoded is Map) {
        final msg = decoded['detail'] ?? decoded['error'] ?? decoded['message'];
        if (msg != null && msg.toString().trim().isNotEmpty) {
          return msg.toString();
        }
      }
    } catch (_) {}
    return fallback;
  }

  Future<List<dynamic>> getMyBranches() async {
    final res = await http.get(
      Uri.parse('${ApiConfig.baseUrl}/branches/my'),
      headers: _headers,
    );
    if (res.statusCode == 200) return jsonDecode(utf8.decode(res.bodyBytes));
    throw ApiException(
      res.statusCode,
      _parseError(res, 'تعذر تحميل قائمة الفروع'),
    );
  }

  Future<List<dynamic>> getTableOrders(int branchId, int tableId) async {
    final res = await http.get(
      Uri.parse(
        '${ApiConfig.baseUrl}/waiter/branches/$branchId/tables/$tableId/orders',
      ),
      headers: _headers,
    );
    if (res.statusCode == 200) return jsonDecode(utf8.decode(res.bodyBytes));
    throw ApiException(
      res.statusCode,
      _parseError(res, 'تعذر تحميل طلبات الطاولة'),
    );
  }

  Future<Map<String, dynamic>> modifyPendingOrder({
    required int branchId,
    required int orderId,
    String? notes,
    required List<Map<String, dynamic>> items,
  }) async {
    final res = await http.put(
      Uri.parse(
        '${ApiConfig.baseUrl}/waiter/branches/$branchId/orders/$orderId',
      ),
      headers: _headers,
      body: jsonEncode({'notes': notes, 'items': items}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(utf8.decode(res.bodyBytes));
    }
    throw ApiException(res.statusCode, _parseError(res, 'تعذر تعديل الطلب'));
  }

  Future<void> cancelPendingOrder(int branchId, int orderId) async {
    final res = await http.post(
      Uri.parse(
        '${ApiConfig.baseUrl}/waiter/branches/$branchId/orders/$orderId/cancel',
      ),
      headers: _headers,
    );
    if (res.statusCode < 200 || res.statusCode >= 300) {
      throw ApiException(res.statusCode, _parseError(res, 'تعذر إلغاء الطلب'));
    }
  }

  Future<List<dynamic>> getTables(int branchId) async {
    final res = await http.get(
      Uri.parse('${ApiConfig.baseUrl}/waiter/branches/$branchId/tables'),
      headers: _headers,
    );
    if (res.statusCode == 200) return jsonDecode(utf8.decode(res.bodyBytes));
    throw ApiException(res.statusCode, _parseError(res, 'تعذر تحميل الطاولات'));
  }

  Future<Map<String, dynamic>> getMenu(int branchId) async {
    final res = await http.get(
      Uri.parse('${ApiConfig.baseUrl}/waiter/branches/$branchId/menu'),
      headers: _headers,
    );
    if (res.statusCode == 200) return jsonDecode(utf8.decode(res.bodyBytes));
    throw ApiException(res.statusCode, _parseError(res, 'تعذر تحميل المنيو'));
  }

  Future<List<dynamic>> getReadyOrders(int branchId) async {
    final res = await http.get(
      Uri.parse('${ApiConfig.baseUrl}/waiter/branches/$branchId/ready-orders'),
      headers: _headers,
    );
    if (res.statusCode == 200) return jsonDecode(utf8.decode(res.bodyBytes));
    throw ApiException(
      res.statusCode,
      _parseError(res, 'تعذر تحميل الطلبات الجاهزة'),
    );
  }

  Future<Map<String, dynamic>> placeTableOrder({
    required int branchId,
    required int tableId,
    String? notes,
    required List<Map<String, dynamic>> items,
    String? idempotencyKey,
  }) async {
    final headers = Map<String, String>.from(_headers);
    if (idempotencyKey != null && idempotencyKey.isNotEmpty) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    final res = await http.post(
      Uri.parse('${ApiConfig.baseUrl}/waiter/branches/$branchId/orders'),
      headers: headers,
      body: jsonEncode({'tableId': tableId, 'notes': notes, 'items': items}),
    );
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return jsonDecode(utf8.decode(res.bodyBytes));
    }
    throw ApiException(res.statusCode, _parseError(res, 'تعذر إرسال الطلب'));
  }

  Future<void> markServed(int branchId, int orderId) async {
    final res = await http.post(
      Uri.parse(
        '${ApiConfig.baseUrl}/waiter/branches/$branchId/orders/$orderId/serve',
      ),
      headers: _headers,
    );
    if (res.statusCode < 200 || res.statusCode >= 300) {
      throw ApiException(
        res.statusCode,
        _parseError(res, 'تعذر تحديث حالة الطلب'),
      );
    }
  }

  Future<void> changePassword(
    String currentPassword,
    String newPassword,
  ) async {
    final res = await http.post(
      Uri.parse('${ApiConfig.baseUrl}/auth/change-password'),
      headers: _headers,
      body: jsonEncode({
        'currentPassword': currentPassword,
        'newPassword': newPassword,
      }),
    );
    if (res.statusCode < 200 || res.statusCode >= 300) {
      throw ApiException(
        res.statusCode,
        _parseError(res, 'تعذر تغيير كلمة المرور'),
      );
    }
  }
}