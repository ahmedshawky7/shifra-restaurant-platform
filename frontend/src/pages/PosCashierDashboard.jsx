import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { createPortal } from 'react-dom';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import QRCode from 'qrcode';
import axiosClient from '../api/axiosClient';
import { useAuth } from '../context/AuthContext';
import DashboardLayout from '../components/DashboardLayout';
import {
  ShoppingBag, Grid, Receipt, Plus, Minus, Trash2, CheckCircle2, XCircle, Bell, X, Printer
} from 'lucide-react';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

function generateIdempotencyKey() {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export default function PosCashierDashboard() {
  const { selectedBranchId, currentBranch, user } = useAuth();
  const isManager = user?.role === 'ORG_ADMIN' || user?.isOwner;

  const [tab, setTab] = useState('tables');
  const [connStatus, setConnStatus] = useState('reconnecting');
  const [submittingOrder, setSubmittingOrder] = useState(false);
  const [closingBill, setClosingBill] = useState(false);

  const [tables, setTables] = useState([]);
  const [orders, setOrders] = useState([]);
  const [menu, setMenu] = useState(null);
  const [alerts, setAlerts] = useState([]);

  const [orderType, setOrderType] = useState('TAKEAWAY');
  const [selectedTableId, setSelectedTableId] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [deliveryAddress, setDeliveryAddress] = useState('');
  const [orderNotes, setOrderNotes] = useState('');
  const [posCart, setPosCart] = useState([]);
  const [activeCatId, setActiveCatId] = useState(null);

  const [customizingItem, setCustomizingItem] = useState(null);
  const [modalVariant, setModalVariant] = useState(null);
  const [modalAddons, setModalAddons] = useState([]);
  const [modalQty, setModalQty] = useState(1);
  const [modalNotes, setModalNotes] = useState('');

  const [billModal, setBillModal] = useState(null);
  const [discountAmount, setDiscountAmount] = useState(0);
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [lastInvoice, setLastInvoice] = useState(null);
  const [invoiceQrUrl, setInvoiceQrUrl] = useState('');

  const orderIdemKeyRef = useRef(generateIdempotencyKey());
  const billIdemKeyRef = useRef(generateIdempotencyKey());

  useEffect(() => {
    orderIdemKeyRef.current = generateIdempotencyKey();
  }, [posCart, orderType, selectedTableId, orderNotes]);

  useEffect(() => {
    billIdemKeyRef.current = generateIdempotencyKey();
  }, [billModal, discountAmount, paymentMethod]);

  useEffect(() => {
    if (!lastInvoice?.taxQrBase64) {
      setInvoiceQrUrl('');
      return;
    }
    QRCode.toDataURL(lastInvoice.taxQrBase64, { width: 160, margin: 1 })
      .then(setInvoiceQrUrl)
      .catch(() => setInvoiceQrUrl(''));
  }, [lastInvoice]);

  useEffect(() => {
    const onOffline = () => setConnStatus('offline');
    const onOnline = () => setConnStatus('reconnecting');
    window.addEventListener('offline', onOffline);
    window.addEventListener('online', onOnline);
    return () => {
      window.removeEventListener('offline', onOffline);
      window.removeEventListener('online', onOnline);
    };
  }, []);

  const loadData = useCallback(async () => {
    if (!selectedBranchId) return;
    try {
      const [tRes, oRes, mRes] = await Promise.all([
        axiosClient.get(`/pos/branches/${selectedBranchId}/tables`),
        axiosClient.get(`/pos/branches/${selectedBranchId}/orders`),
        axiosClient.get(`/pos/branches/${selectedBranchId}/menu`),
      ]);
      setTables(tRes.data || []);
      setOrders(oRes.data || []);
      setMenu(mRes.data);
      localStorage.setItem(`pos_cache_tables_${selectedBranchId}`, JSON.stringify(tRes.data || []));
      localStorage.setItem(`pos_cache_menu_${selectedBranchId}`, JSON.stringify(mRes.data || null));
      if (mRes.data?.categories?.length > 0) {
        setActiveCatId((prev) => prev || mRes.data.categories[0].id);
      }
    } catch (e) {
      console.error('POS load failed, falling back to local cache:', e);
      try {
        const cachedTables = localStorage.getItem(`pos_cache_tables_${selectedBranchId}`);
        const cachedMenu = localStorage.getItem(`pos_cache_menu_${selectedBranchId}`);
        if (cachedTables && tables.length === 0) setTables(JSON.parse(cachedTables));
        if (cachedMenu && !menu) setMenu(JSON.parse(cachedMenu));
      } catch {
        // ignore cache parse errors
      }
    }
  }, [selectedBranchId]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!selectedBranchId) return;
    loadData();
    setConnStatus(navigator.onLine ? 'reconnecting' : 'offline');

    const token = localStorage.getItem('token');
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5000,
      onConnect: () => {
        setConnStatus('connected');
        client.subscribe(`/topic/branch/${selectedBranchId}/pos`, (msg) => {
          if (!msg.body) return;
          const ev = JSON.parse(msg.body);
          if (ev.eventType === 'CALL_WAITER' || ev.eventType === 'REQUEST_BILL') {
            setAlerts((prev) => [
              {
                id: Date.now(),
                type: ev.eventType,
                tableNumber: ev.tableNumber,
                time: new Date().toLocaleTimeString('ar-EG'),
              },
              ...prev.slice(0, 4),
            ]);
          }
          loadData();
        });
      },
      onWebSocketClose: () => setConnStatus(navigator.onLine ? 'reconnecting' : 'offline'),
      onStompError: () => setConnStatus('reconnecting'),
    });

    client.activate();
    return () => client.deactivate();
  }, [selectedBranchId, loadData]);

  const openCustomizePosItem = (item) => {
    setCustomizingItem(item);
    const defaultVar = item.variants?.find((v) => v.isDefault) || item.variants?.[0] || null;
    setModalVariant(defaultVar);
    setModalAddons([]);
    setModalQty(1);
    setModalNotes('');
  };

  const toggleModalAddon = (addon) => {
    setModalAddons((prev) =>
      prev.some((a) => a.id === addon.id) ? prev.filter((a) => a.id !== addon.id) : [...prev, addon]
    );
  };

  const addCustomizedToCart = () => {
    if (!customizingItem) return;
    const base = modalVariant ? Number(modalVariant.price) : Number(customizingItem.effectivePrice);
    const addonsPrice = modalAddons.reduce((s, a) => s + Number(a.price), 0);
    const unitTotal = base + addonsPrice;

    setPosCart((prev) => [
      ...prev,
      {
        uid: `${customizingItem.id}-${Date.now()}-${Math.random()}`,
        menuItemId: customizingItem.id,
        name: customizingItem.name,
        variantId: modalVariant?.id || null,
        variantName: modalVariant?.name || null,
        addons: modalAddons,
        notes: modalNotes.trim(),
        quantity: modalQty,
        unitPrice: unitTotal,
        lineTotal: unitTotal * modalQty,
      },
    ]);
    setCustomizingItem(null);
  };

  const quickAddPosItem = (item, variant = null) => {
    if ((item.addons?.filter((a) => a.available).length || 0) > 0 && !variant) {
      openCustomizePosItem(item);
      return;
    }
    const chosenVar = variant || item.variants?.find((v) => v.isDefault) || item.variants?.[0] || null;
    const price = chosenVar ? Number(chosenVar.price) : Number(item.effectivePrice);

    setPosCart((prev) => {
      const idx = prev.findIndex(
        (c) =>
          c.menuItemId === item.id &&
          c.variantId === (chosenVar?.id || null) &&
          (c.addons?.length || 0) === 0 &&
          !c.notes
      );
      if (idx >= 0) {
        const copy = [...prev];
        copy[idx].quantity += 1;
        copy[idx].lineTotal = copy[idx].quantity * copy[idx].unitPrice;
        return copy;
      }
      return [
        ...prev,
        {
          uid: `${item.id}-${chosenVar?.id || 'base'}-${Date.now()}`,
          menuItemId: item.id,
          name: item.name,
          variantId: chosenVar?.id || null,
          variantName: chosenVar?.name || null,
          addons: [],
          notes: '',
          quantity: 1,
          unitPrice: price,
          lineTotal: price,
        },
      ];
    });
  };

  const posCartSubtotal = useMemo(() => posCart.reduce((s, c) => s + c.lineTotal, 0), [posCart]);
  const taxRate = Number(menu?.taxRate ?? currentBranch?.taxRate ?? 0);
  const serviceRate = Number(menu?.serviceChargeRate ?? currentBranch?.serviceChargeRate ?? 0);
  const posCartTax = (posCartSubtotal * taxRate) / 100;
  const posCartService = orderType === 'DINE_IN' ? (posCartSubtotal * serviceRate) / 100 : 0;
  const posCartTotal = posCartSubtotal + posCartTax + posCartService;

  const submitPosOrder = async () => {
    if (submittingOrder) return;
    if (posCart.length === 0) return alert('أضف أصنافاً للطلب أولاً');
    if (orderType === 'DINE_IN' && !selectedTableId) return alert('اختر الطاولة أولاً');

    setSubmittingOrder(true);
    try {
      await axiosClient.post(
        `/pos/branches/${selectedBranchId}/orders`,
        {
          tableId: orderType === 'DINE_IN' ? Number(selectedTableId) : null,
          orderType,
          customerName: customerName || null,
          customerPhone: customerPhone || null,
          deliveryAddress: deliveryAddress || null,
          notes: orderNotes.trim() || null,
          items: posCart.map((c) => ({
            menuItemId: c.menuItemId,
            variantId: c.variantId,
            quantity: c.quantity,
            notes: c.notes || null,
            addonIds: (c.addons || []).map((a) => a.id),
          })),
        },
        { headers: { 'Idempotency-Key': orderIdemKeyRef.current } }
      );
      alert('تم إرسال الطلب للمطبخ!');
      setPosCart([]);
      setSelectedTableId('');
      setCustomerName('');
      setCustomerPhone('');
      setDeliveryAddress('');
      setOrderNotes('');
      orderIdemKeyRef.current = generateIdempotencyKey();
      setTab('tables');
      loadData();
    } catch (err) {
      alert(err.response?.data?.detail || err.response?.data?.error || 'فشل إرسال الطلب');
    } finally {
      setSubmittingOrder(false);
    }
  };

  const handleCloseBill = async () => {
    if (!billModal || closingBill) return;
    setClosingBill(true);
    try {
      const payload = {
        tableId: billModal.type === 'TABLE' ? billModal.id : null,
        singleOrderId: billModal.type === 'ORDER' ? billModal.id : null,
        discountAmount: Number(discountAmount || 0),
        paymentMethod,
      };
      const res = await axiosClient.post(`/pos/branches/${selectedBranchId}/close-bill`, payload, {
        headers: { 'Idempotency-Key': billIdemKeyRef.current },
      });
      setLastInvoice(res.data);
      setBillModal(null);
      setDiscountAmount(0);
      billIdemKeyRef.current = generateIdempotencyKey();
      loadData();
    } catch (err) {
      alert(err.response?.data?.detail || err.response?.data?.error || 'تعذر إغلاق الفاتورة');
    } finally {
      setClosingBill(false);
    }
  };

  const handlePrintThermalReceipt = () => {
    if (!lastInvoice) return;
    const win = window.open('', '_blank', 'width=360,height=640');
    if (!win) return alert('يرجى السماح للنوافذ المنبثقة لطباعة الفاتورة');

    const safeBranch = escapeHtml(currentBranch?.name || 'الفرع');
    const safeInvNo = escapeHtml(lastInvoice.invoiceNumber);
    const safeDate = escapeHtml(new Date(lastInvoice.paidAt || Date.now()).toLocaleString('ar-EG'));
    const safeMethod = escapeHtml(
      lastInvoice.paymentMethod === 'CASH' ? 'نقدي' : lastInvoice.paymentMethod === 'CARD' ? 'بطاقة' : 'محفظة'
    );

    win.document.write(`
      <!doctype html>
      <html lang="ar" dir="rtl">
      <head>
        <meta charset="UTF-8" />
        <title>فاتورة ${safeInvNo}</title>
        <style>
          body { font-family: Tahoma, Arial, sans-serif; width: 76mm; margin: 0 auto; padding: 8px; color: #000; font-size: 12px; }
          .center { text-align: center; }
          .bold { font-weight: bold; }
          .line { border-top: 1px dashed #000; margin: 8px 0; }
          .row { display: flex; justify-content: space-between; margin: 4px 0; }
          .total { font-size: 15px; font-weight: bold; }
          .qr img { width: 130px; height: 130px; margin: 8px auto; display: block; }
          @media print { .no-print { display: none; } }
        </style>
      </head>
      <body>
        <div class="center bold" style="font-size: 16px;">فاتورة ضريبية مبسطة</div>
        <div class="center">${safeBranch}</div>
        <div class="center" style="font-size: 11px;">${safeInvNo}</div>
        <div class="center" style="font-size: 10px;">${safeDate}</div>
        <div class="line"></div>
        <div class="row"><span>قيمة الأصناف:</span><span>${Number(lastInvoice.subtotal).toFixed(2)} ج</span></div>
        <div class="row"><span>الضريبة:</span><span>${Number(lastInvoice.taxAmount).toFixed(2)} ج</span></div>
        <div class="row"><span>رسوم الخدمة:</span><span>${Number(lastInvoice.serviceCharge).toFixed(2)} ج</span></div>
        <div class="row"><span>الخصم:</span><span>-${Number(lastInvoice.discountAmount).toFixed(2)} ج</span></div>
        <div class="line"></div>
        <div class="row total"><span>الإجمالي المدفوع:</span><span>${Number(lastInvoice.totalAmount).toFixed(2)} ج</span></div>
        <div class="row"><span>طريقة الدفع:</span><span>${safeMethod}</span></div>
        <div class="line"></div>
        ${invoiceQrUrl ? `<div class="qr"><img src="${invoiceQrUrl}" alt="Tax QR" /></div>` : ''}
        <div class="center" style="font-size: 11px;">شكراً لزيارتكم</div>
        <button class="no-print" onclick="window.print()" style="width:100%;margin-top:12px;padding:8px;font-weight:bold;">طباعة</button>
      </body>
      </html>
    `);
    win.document.close();
  };

  const handleServe = async (orderId) => {
    try {
      await axiosClient.post(`/pos/branches/${selectedBranchId}/orders/${orderId}/serve`);
      loadData();
    } catch (err) {
      alert(err.response?.data?.detail || err.response?.data?.error || 'تعذر تحديث حالة الطلب');
    }
  };

  const handleCancel = async (orderId) => {
    if (!window.confirm('هل تريد إلغاء هذا الطلب؟')) return;
    try {
      await axiosClient.post(`/pos/branches/${selectedBranchId}/orders/${orderId}/cancel`);
      loadData();
    } catch (err) {
      alert(err.response?.data?.detail || err.response?.data?.error || 'تعذر إلغاء الطلب');
    }
  };

  const handleTableClick = async (t, occupied) => {
    if (occupied && t.unpaidTotal > 0) {
      const hasPreparing = orders.some((o) => o.tableId === t.id && o.status === 'PREPARING');
      if (hasPreparing) {
        return alert('يوجد طلب ما زال قيد التحضير في المطبخ، لا يمكن إغلاق الفاتورة قبل جاهزيته');
      }
      setBillModal({
        type: 'TABLE',
        id: t.id,
        title: `فاتورة طاولة ${t.tableNumber}`,
        subtotal: Number(t.unpaidSubtotal ?? t.unpaidTotal),
        tax: Number(t.unpaidTax ?? 0),
        service: Number(t.unpaidService ?? 0),
        total: Number(t.unpaidTotal),
        items: t.unpaidItems || [],
      });
    } else if (occupied && t.activeOrdersCount === 0) {
      if (window.confirm(`طاولة ${t.tableNumber} لا تحتوي على طلبات. هل تريد تحريرها لتصبح متاحة؟`)) {
        await axiosClient.post(`/pos/branches/${selectedBranchId}/tables/${t.id}/release`);
        loadData();
      }
    }
  };

  if (!selectedBranchId) {
    return (
      <DashboardLayout title="الكاشير ونقطة البيع (POS)">
        <div className="card p-10 text-center font-bold text-slate-500">اختر فرعاً أولاً.</div>
      </DashboardLayout>
    );
  }

  const activeCategory = menu?.categories?.find((c) => c.id === activeCatId) || menu?.categories?.[0];

  const statusMeta = {
    connected: { dot: 'bg-emerald-500', text: 'متصل لحظياً' },
    reconnecting: { dot: 'bg-amber-500', text: 'جارٍ إعادة الاتصال...' },
    offline: { dot: 'bg-red-500', text: 'غير متصل بالإنترنت' },
  }[connStatus];

  return (
    <DashboardLayout title="الكاشير ونقطة البيع (POS)">
      <div className="max-w-7xl mx-auto space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex gap-2">
            <button
              onClick={() => setTab('tables')}
              className={`px-5 py-2.5 rounded-xl font-black text-sm flex items-center gap-2 ${
                tab === 'tables' ? 'bg-slate-900 text-white' : 'bg-white border border-slate-200 text-slate-600'
              }`}
            >
              <Grid size={17} /> الطاولات والطلبات النشطة
            </button>
            <button
              onClick={() => setTab('new-order')}
              className={`px-5 py-2.5 rounded-xl font-black text-sm flex items-center gap-2 ${
                tab === 'new-order' ? 'bg-orange-600 text-white' : 'bg-white border border-slate-200 text-slate-600'
              }`}
            >
              <ShoppingBag size={17} /> طلب جديد (POS)
            </button>
          </div>

          <div className="flex items-center gap-2 bg-white px-4 py-2 rounded-xl border border-slate-200 font-bold text-xs">
            <span className={`w-2.5 h-2.5 rounded-full live-dot ${statusMeta.dot}`} />
            <span>{statusMeta.text}</span>
          </div>
        </div>

        {alerts.length > 0 && (
          <div className="space-y-2">
            {alerts.map((al) => (
              <div key={al.id} className="p-3.5 rounded-2xl bg-amber-50 border border-amber-300 flex justify-between items-center">
                <div className="flex items-center gap-2 font-black text-amber-900 text-sm">
                  <Bell className="text-amber-600" size={18} />
                  <span>
                    {al.type === 'REQUEST_BILL'
                      ? `طاولة ${al.tableNumber} تطلب الحساب الآن!`
                      : `طاولة ${al.tableNumber} تنادي الويتر!`}
                  </span>
                  <span className="text-xs text-amber-600">({al.time})</span>
                </div>
                <button onClick={() => setAlerts((prev) => prev.filter((x) => x.id !== al.id))}>
                  <X size={16} />
                </button>
              </div>
            ))}
          </div>
        )}

        {tab === 'tables' && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 space-y-4">
              <h3 className="font-black text-lg text-slate-800">حالة الطاولات ({tables.length})</h3>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
                {tables.map((t) => {
                  const occupied = t.status === 'OCCUPIED' || t.activeOrdersCount > 0;
                  const disabled = t.active === false;

                  return (
                    <div
                      key={t.id}
                      onClick={() => handleTableClick(t, occupied)}
                      className={`p-5 rounded-2xl border-2 transition-all ${
                        disabled
                          ? 'bg-slate-100 border-slate-300 opacity-70 cursor-not-allowed'
                          : occupied
                            ? 'bg-orange-50 border-orange-300 cursor-pointer hover:shadow-md'
                            : 'bg-emerald-50/70 border-emerald-200'
                      }`}
                    >
                      <div className="flex justify-between items-center mb-2 gap-1">
                        <span className="text-xl font-black text-slate-800">{t.tableNumber}</span>
                        <span
                          className={`text-[10px] font-black px-2.5 py-0.5 rounded-full ${
                            disabled
                              ? 'bg-slate-600 text-white'
                              : occupied
                                ? 'bg-orange-600 text-white'
                                : 'bg-emerald-600 text-white'
                          }`}
                        >
                          {disabled ? 'معطلة / صيانة' : occupied ? 'مشغولة' : 'متاحة'}
                        </span>
                      </div>

                      {disabled ? (
                        <p className="text-xs text-slate-500 font-bold">خارج الخدمة حالياً</p>
                      ) : occupied ? (
                        <div className="text-xs font-bold text-orange-900 space-y-1">
                          <p>طلبات مفتوحة: {t.activeOrdersCount}</p>
                          <p className="text-base font-black text-slate-900">{t.unpaidTotal} ج</p>
                          <p className="text-[11px] text-indigo-700 underline">
                            {t.activeOrdersCount === 0 ? 'اضغط لتحرير الطاولة' : 'اضغط لتفصيل وتحصيل الفاتورة'}
                          </p>
                        </div>
                      ) : (
                        <p className="text-xs text-emerald-700 font-semibold">{t.capacity} مقاعد</p>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="card p-5 space-y-4 max-h-[75vh] overflow-y-auto">
              <h3 className="font-black text-lg text-slate-800">الطلبات النشطة ({orders.length})</h3>
              {orders.length === 0 ? (
                <p className="text-slate-400 font-bold text-sm text-center py-8">لا توجد طلبات نشطة حالياً</p>
              ) : (
                orders.map((o) => {
                  const isPreparing = o.status === 'PREPARING';
                  const isTakeawayOrDelivery = o.orderType === 'TAKEAWAY' || o.orderType === 'DELIVERY';
                  const canCancel = isManager || o.status === 'PENDING';

                  return (
                    <div key={o.orderId} className="p-4 rounded-2xl bg-slate-50 border border-slate-200 space-y-2">
                      <div className="flex justify-between items-center">
                        <span className="font-black text-slate-900">
                          #{o.orderNumber} — {o.tableNumber ? `طاولة ${o.tableNumber}` : o.orderType}
                        </span>
                        <span className="text-xs font-black px-2.5 py-0.5 rounded-full bg-indigo-100 text-indigo-700">
                          {o.status}
                        </span>
                      </div>
                      <p className="text-xs text-slate-500 font-bold">
                        {o.items?.map((i) => `${i.quantity}x ${i.itemName}`).join(' • ')}
                      </p>
                      <div className="flex justify-between items-center pt-2 border-t border-slate-200 gap-2 flex-wrap">
                        <span className="font-black text-emerald-700">{o.totalAmount} ج</span>
                        <div className="flex gap-1.5 flex-wrap">
                          {o.status === 'READY' && (
                            <button
                              onClick={() => handleServe(o.orderId)}
                              className="px-2.5 py-1 rounded-lg bg-emerald-600 text-white text-xs font-bold flex items-center gap-1"
                            >
                              <CheckCircle2 size={13} />
                              {isTakeawayOrDelivery ? 'تم التسليم للعميل' : 'تسليم'}
                            </button>
                          )}
                          {!o.tableId && (
                            <button
                              onClick={() => {
                                if (isPreparing) {
                                  return alert('يوجد طلب ما زال قيد التحضير في المطبخ، لا يمكن إغلاق الفاتورة قبل جاهزيته');
                                }
                                setBillModal({
                                  type: 'ORDER',
                                  id: o.orderId,
                                  title: `تحصيل طلب #${o.orderNumber}`,
                                  subtotal: Number(o.subtotal ?? o.totalAmount),
                                  tax: Number(o.taxAmount ?? 0),
                                  service: Number(o.serviceCharge ?? 0),
                                  total: Number(o.totalAmount),
                                  items: (o.items || []).map((it) => ({
                                    name: it.itemName + (it.variantName ? ` (${it.variantName})` : ''),
                                    quantity: it.quantity,
                                    lineTotal: it.lineTotal,
                                  })),
                                });
                              }}
                              disabled={isPreparing}
                              className={`px-2.5 py-1 rounded-lg text-xs font-bold flex items-center gap-1 ${
                                isPreparing
                                  ? 'bg-slate-200 text-slate-500 cursor-not-allowed'
                                  : 'bg-indigo-600 text-white hover:bg-indigo-700'
                              }`}
                            >
                              <Receipt size={13} /> تحصيل
                            </button>
                          )}
                          {canCancel && (
                            <button
                              onClick={() => handleCancel(o.orderId)}
                              className="px-2 py-1 rounded-lg bg-red-50 text-red-600 text-xs font-bold"
                              title="إلغاء الطلب"
                            >
                              <XCircle size={14} />
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        )}

        {tab === 'new-order' && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 space-y-4">
              <div className="flex gap-2 overflow-x-auto pb-2">
                {menu?.categories?.map((cat) => (
                  <button
                    key={cat.id}
                    onClick={() => setActiveCatId(cat.id)}
                    className={`px-4 py-2 rounded-xl font-bold text-sm whitespace-nowrap ${
                      activeCategory?.id === cat.id ? 'bg-slate-900 text-white' : 'bg-white border border-slate-200'
                    }`}
                  >
                    {cat.name}
                  </button>
                ))}
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                {activeCategory?.items?.filter((i) => i.available).map((item) => (
                  <div key={item.id} className="card p-4 flex flex-col justify-between space-y-2">
                    <div className="flex justify-between items-start gap-2">
                      <div>
                        <p className="font-black text-slate-800 text-sm">{item.name}</p>
                        <p className="text-xs font-black text-orange-600 mt-1">{item.effectivePrice} ج</p>
                      </div>
                      <button
                        onClick={() => openCustomizePosItem(item)}
                        className="text-[11px] font-bold text-indigo-600 bg-indigo-50 px-2 py-1 rounded-lg hover:bg-indigo-100"
                      >
                        تخصيص
                      </button>
                    </div>
                    {item.variants?.length > 0 ? (
                      <div className="flex flex-wrap gap-1">
                        {item.variants.map((v) => (
                          <button
                            key={v.id}
                            onClick={() => quickAddPosItem(item, v)}
                            className="px-2.5 py-1 rounded-lg bg-indigo-50 text-indigo-700 text-[11px] font-bold hover:bg-indigo-100"
                          >
                            + {v.name} ({v.price}ج)
                          </button>
                        ))}
                      </div>
                    ) : (
                      <button
                        onClick={() => quickAddPosItem(item)}
                        className="w-full py-2 rounded-xl bg-orange-50 text-orange-700 font-bold text-xs hover:bg-orange-100 flex items-center justify-center gap-1"
                      >
                        <Plus size={14} /> إضافة سريعة
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>

            <div className="card p-6 space-y-4 h-fit">
              <h3 className="font-black text-lg">تفاصيل الطلب الجديد</h3>
              <div className="grid grid-cols-3 gap-2">
                {[
                  { k: 'TAKEAWAY', l: 'تيك أواي' },
                  { k: 'DINE_IN', l: 'صالة' },
                  { k: 'DELIVERY', l: 'دليفري' },
                ].map((t) => (
                  <button
                    key={t.k}
                    onClick={() => setOrderType(t.k)}
                    className={`py-2 rounded-xl text-xs font-black border ${
                      orderType === t.k ? 'bg-indigo-600 text-white border-indigo-600' : 'border-slate-200 text-slate-600'
                    }`}
                  >
                    {t.l}
                  </button>
                ))}
              </div>

              {orderType === 'DINE_IN' && (
                <select
                  className="input-fancy w-full p-2.5 text-sm font-bold"
                  value={selectedTableId}
                  onChange={(e) => setSelectedTableId(e.target.value)}
                >
                  <option value="">اختر الطاولة المتاحة</option>
                  {tables
                    .filter((t) => t.active !== false)
                    .map((t) => (
                      <option key={t.id} value={t.id}>
                        طاولة {t.tableNumber} ({t.capacity} مقاعد)
                      </option>
                    ))}
                </select>
              )}

              {orderType === 'DELIVERY' && (
                <div className="space-y-2">
                  <input
                    className="input-fancy w-full p-2.5 text-xs"
                    placeholder="اسم العميل"
                    value={customerName}
                    onChange={(e) => setCustomerName(e.target.value)}
                  />
                  <input
                    className="input-fancy w-full p-2.5 text-xs"
                    placeholder="رقم الهاتف"
                    value={customerPhone}
                    onChange={(e) => setCustomerPhone(e.target.value)}
                  />
                  <input
                    className="input-fancy w-full p-2.5 text-xs"
                    placeholder="عنوان التوصيل"
                    value={deliveryAddress}
                    onChange={(e) => setDeliveryAddress(e.target.value)}
                  />
                </div>
              )}

              <input
                className="input-fancy w-full p-2.5 text-xs"
                placeholder="ملاحظات عامة للطلب..."
                value={orderNotes}
                onChange={(e) => setOrderNotes(e.target.value)}
              />

              <div className="space-y-2.5 max-h-60 overflow-y-auto border-t border-b border-slate-100 py-3">
                {posCart.map((c) => (
                  <div key={c.uid} className="text-xs font-bold space-y-0.5">
                    <div className="flex justify-between items-center">
                      <span>
                        {c.quantity}x {c.name} {c.variantName ? `(${c.variantName})` : ''}
                      </span>
                      <div className="flex items-center gap-2">
                        <span>{c.lineTotal.toFixed(2)} ج</span>
                        <button
                          onClick={() => setPosCart((prev) => prev.filter((x) => x.uid !== c.uid))}
                          className="text-red-500"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>
                    {c.addons?.length > 0 && (
                      <p className="text-[11px] text-indigo-600">+ {c.addons.map((a) => a.name).join('، ')}</p>
                    )}
                    {c.notes && <p className="text-[11px] text-rose-600">* {c.notes}</p>}
                  </div>
                ))}
                {posCart.length === 0 && <p className="text-slate-400 text-xs text-center">السلة فارغة</p>}
              </div>

              <div className="bg-slate-50 p-3.5 rounded-2xl space-y-1 text-xs font-bold">
                <div className="flex justify-between text-slate-600">
                  <span>المجموع الفرعي:</span><span>{posCartSubtotal.toFixed(2)} ج</span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>الضريبة ({taxRate}%):</span><span>{posCartTax.toFixed(2)} ج</span>
                </div>
                {orderType === 'DINE_IN' && serviceRate > 0 && (
                  <div className="flex justify-between text-slate-600">
                    <span>الخدمة ({serviceRate}%):</span><span>{posCartService.toFixed(2)} ج</span>
                  </div>
                )}
                <div className="flex justify-between font-black text-base text-slate-900 pt-1.5 border-t border-slate-200">
                  <span>الإجمالي التقديري:</span>
                  <span className="text-emerald-700">{posCartTotal.toFixed(2)} ج</span>
                </div>
              </div>

              <button
                onClick={submitPosOrder}
                disabled={submittingOrder || connStatus === 'offline'}
                className="btn-primary w-full py-3.5 font-black"
              >
                {submittingOrder ? 'جاري الإرسال...' : 'إرسال الطلب للمطبخ'}
              </button>
            </div>
          </div>
        )}
      </div>

      {customizingItem &&
        createPortal(
          <div className="modal-backdrop flex items-center justify-center p-4" dir="rtl" onClick={() => setCustomizingItem(null)}>
            <div className="bg-white rounded-3xl p-6 w-[440px] max-w-full max-h-[88vh] overflow-y-auto shadow-2xl space-y-4" onClick={(e) => e.stopPropagation()}>
              <div className="flex justify-between items-center">
                <h3 className="text-xl font-black text-slate-800">{customizingItem.name}</h3>
                <button onClick={() => setCustomizingItem(null)}><X size={20} /></button>
              </div>

              {customizingItem.variants?.length > 0 && (
                <div className="space-y-2">
                  <p className="text-xs font-black text-slate-600">الحجم / الخيار:</p>
                  {customizingItem.variants.map((v) => (
                    <label
                      key={v.id}
                      className={`flex justify-between items-center p-2.5 rounded-xl border cursor-pointer text-sm ${
                        modalVariant?.id === v.id ? 'border-orange-500 bg-orange-50' : 'border-slate-200'
                      }`}
                    >
                      <span className="flex items-center gap-2 font-bold">
                        <input type="radio" checked={modalVariant?.id === v.id} onChange={() => setModalVariant(v)} />
                        {v.name}
                      </span>
                      <span className="font-black text-orange-600">{v.price} ج</span>
                    </label>
                  ))}
                </div>
              )}

              {customizingItem.addons?.filter((a) => a.available).length > 0 && (
                <div className="space-y-2">
                  <p className="text-xs font-black text-slate-600">الإضافات:</p>
                  {customizingItem.addons.filter((a) => a.available).map((a) => {
                    const checked = modalAddons.some((x) => x.id === a.id);
                    return (
                      <label
                        key={a.id}
                        className={`flex justify-between items-center p-2.5 rounded-xl border cursor-pointer text-sm ${
                          checked ? 'border-indigo-500 bg-indigo-50' : 'border-slate-200'
                        }`}
                      >
                        <span className="flex items-center gap-2 font-bold">
                          <input type="checkbox" checked={checked} onChange={() => toggleModalAddon(a)} />
                          {a.name}
                        </span>
                        <span className="font-bold text-indigo-700">+{a.price} ج</span>
                      </label>
                    );
                  })}
                </div>
              )}

              <input
                type="text"
                value={modalNotes}
                onChange={(e) => setModalNotes(e.target.value)}
                placeholder="ملاحظة للصنف (بدون بصل، تسوية...)"
                className="input-fancy w-full p-3 text-sm"
              />

              <div className="flex items-center gap-3">
                <div className="flex items-center gap-2 bg-slate-100 px-3 py-2 rounded-xl">
                  <button onClick={() => setModalQty((q) => Math.max(1, q - 1))}><Minus size={16} /></button>
                  <span className="font-black w-6 text-center">{modalQty}</span>
                  <button onClick={() => setModalQty((q) => q + 1)}><Plus size={16} /></button>
                </div>
                <button onClick={addCustomizedToCart} className="btn-primary flex-1 py-3 font-black">
                  إضافة للسلة
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}

      {billModal &&
        createPortal(
          <div className="modal-backdrop flex items-center justify-center p-4" dir="rtl" onClick={() => setBillModal(null)}>
            <div
              className="bg-white rounded-3xl p-7 w-[470px] max-w-full max-h-[90vh] overflow-y-auto shadow-2xl space-y-4"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex justify-between items-center">
                <h3 className="text-xl font-black text-slate-800">{billModal.title}</h3>
                <button onClick={() => setBillModal(null)} className="text-slate-400 hover:text-slate-700">
                  <X size={20} />
                </button>
              </div>

              {billModal.items?.length > 0 && (
                <div className="bg-slate-50 border border-slate-200 rounded-2xl p-3.5 space-y-1.5 max-h-40 overflow-y-auto">
                  <p className="text-xs font-black text-slate-500 mb-1">تفاصيل الأصناف:</p>
                  {billModal.items.map((it, idx) => (
                    <div key={idx} className="flex justify-between text-xs font-bold text-slate-700">
                      <span>{it.quantity}× {it.name}</span>
                      <span>{Number(it.lineTotal).toFixed(2)} ج</span>
                    </div>
                  ))}
                </div>
              )}

              <div className="p-4 rounded-2xl bg-slate-50 border border-slate-200 space-y-2 text-sm font-bold">
                <div className="flex justify-between text-slate-600">
                  <span>قيمة الأصناف (Subtotal):</span>
                  <span>{billModal.subtotal.toFixed(2)} ج</span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>ضريبة القيمة المضافة (Tax):</span>
                  <span className="text-indigo-700">+{billModal.tax.toFixed(2)} ج</span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>رسوم الخدمة (Service Charge):</span>
                  <span className="text-indigo-700">+{billModal.service.toFixed(2)} ج</span>
                </div>
                {Number(discountAmount || 0) > 0 && (
                  <div className="flex justify-between text-red-600">
                    <span>الخصم:</span>
                    <span>-{Number(discountAmount).toFixed(2)} ج</span>
                  </div>
                )}
                <div className="flex justify-between font-black text-base text-slate-900 border-t border-slate-200 pt-2">
                  <span>الإجمالي قبل الخصم:</span>
                  <span>{billModal.total.toFixed(2)} ج</span>
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-600 mb-1">خصم (جنيه)</label>
                <input
                  type="number"
                  min="0"
                  value={discountAmount}
                  onChange={(e) => setDiscountAmount(e.target.value)}
                  className="input-fancy w-full p-3 font-bold"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-600 mb-1.5">طريقة الدفع</label>
                <div className="grid grid-cols-3 gap-2">
                  {['CASH', 'CARD', 'WALLET'].map((pm) => (
                    <button
                      key={pm}
                      onClick={() => setPaymentMethod(pm)}
                      className={`py-2.5 rounded-xl text-xs font-black border ${
                        paymentMethod === pm ? 'bg-emerald-600 text-white border-emerald-600' : 'border-slate-200 text-slate-600'
                      }`}
                    >
                      {pm === 'CASH' ? 'نقدي' : pm === 'CARD' ? 'بطاقة' : 'محفظة'}
                    </button>
                  ))}
                </div>
              </div>

              <div className="p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 font-black flex justify-between text-base">
                <span>الصافي للدفع:</span>
                <span>{Math.max(0, billModal.total - Number(discountAmount || 0)).toFixed(2)} ج</span>
              </div>

              <div className="flex gap-3 pt-1">
                <button
                  onClick={handleCloseBill}
                  disabled={closingBill}
                  className="btn-primary flex-1 py-3 font-black"
                >
                  {closingBill ? 'جاري التحصيل...' : 'تأكيد التحصيل وإصدار الفاتورة'}
                </button>
                <button onClick={() => setBillModal(null)} className="flex-1 bg-slate-100 text-slate-600 font-bold py-3 rounded-xl">
                  إلغاء
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}

      {lastInvoice &&
        createPortal(
          <div className="modal-backdrop flex items-center justify-center p-4" dir="rtl" onClick={() => setLastInvoice(null)}>
            <div className="bg-white rounded-3xl p-8 w-96 max-w-full shadow-2xl space-y-3 text-center" onClick={(e) => e.stopPropagation()}>
              <CheckCircle2 size={48} className="mx-auto text-emerald-600" />
              <h3 className="text-xl font-black">تم إصدار الفاتورة بنجاح</h3>
              <p className="text-xs font-mono text-slate-500">{lastInvoice.invoiceNumber}</p>
              <div className="bg-slate-50 p-4 rounded-2xl text-sm font-bold space-y-1 text-right">
                <div className="flex justify-between"><span>قيمة الأصناف:</span><span>{lastInvoice.subtotal} ج</span></div>
                <div className="flex justify-between"><span>الضريبة:</span><span>+{lastInvoice.taxAmount} ج</span></div>
                <div className="flex justify-between"><span>الخدمة:</span><span>+{lastInvoice.serviceCharge} ج</span></div>
                <div className="flex justify-between text-red-600"><span>الخصم:</span><span>-{lastInvoice.discountAmount} ج</span></div>
                <div className="flex justify-between text-lg font-black border-t pt-2">
                  <span>الإجمالي المدفوع:</span><span>{lastInvoice.totalAmount} ج</span>
                </div>
              </div>
              {invoiceQrUrl && (
                <div className="pt-2">
                  <img src={invoiceQrUrl} alt="Tax QR" className="w-32 h-32 mx-auto border rounded-xl p-1" />
                  <p className="text-[10px] text-slate-400 font-bold mt-1">رمز الفاتورة الضريبية (Base64 TLV)</p>
                </div>
              )}
              <div className="flex gap-2 pt-1">
                <button
                  onClick={handlePrintThermalReceipt}
                  className="flex-1 py-3 rounded-xl bg-slate-900 text-white font-bold flex items-center justify-center gap-1.5"
                >
                  <Printer size={16} /> طباعة حرارية
                </button>
                <button onClick={() => setLastInvoice(null)} className="btn-primary flex-1 py-3 font-bold">
                  إغلاق
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}
    </DashboardLayout>
  );
}