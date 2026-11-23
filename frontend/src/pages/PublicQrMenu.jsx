import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axiosClient from '../api/axiosClient';
import {
  ShoppingCart, Plus, Minus, X, Bell, Receipt, Clock, CheckCircle2,
  ChefHat, UtensilsCrossed, AlertCircle, Trash2, Wifi, WifiOff
} from 'lucide-react';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

function generateIdempotencyKey() {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export default function PublicQrMenu() {
  const { qrToken, branchId } = useParams();
  const storageKey = qrToken ? `active_order_qrs_${qrToken}` : `active_order_qrs_branch_${branchId}`;
  const legacyStorageKey = qrToken ? `active_order_qr_${qrToken}` : `active_order_branch_${branchId}`;
  const menuCacheKey = qrToken ? `cached_public_menu_qr_${qrToken}` : `cached_public_menu_branch_${branchId}`;

  const [menu, setMenu] = useState(() => {
    try {
      const cached = localStorage.getItem(menuCacheKey);
      return cached ? JSON.parse(cached) : null;
    } catch {
      return null;
    }
  });
  const [loading, setLoading] = useState(!menu);
  const [errorMsg, setErrorMsg] = useState('');
  const [activeCategoryId, setActiveCategoryId] = useState(null);
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [wsState, setWsState] = useState('connected'); // connected | reconnecting | offline
  const [usingCache, setUsingCache] = useState(false);

  const [selectedItem, setSelectedItem] = useState(null);
  const [modalVariant, setModalVariant] = useState(null);
  const [modalAddons, setModalAddons] = useState([]);
  const [modalQty, setModalQty] = useState(1);
  const [modalNotes, setModalNotes] = useState('');

  const [cart, setCart] = useState([]);
  const [cartOpen, setCartOpen] = useState(false);
  const [orderNotes, setOrderNotes] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const idempotencyKeyRef = useRef(generateIdempotencyKey());

  const [trackedOrders, setTrackedOrders] = useState([]);
  const [alertNotice, setAlertNotice] = useState('');

  useEffect(() => {
    idempotencyKeyRef.current = generateIdempotencyKey();
  }, [cart, orderNotes, customerName]);

  useEffect(() => {
    const onOnline = () => setIsOnline(true);
    const onOffline = () => {
      setIsOnline(false);
      setWsState('offline');
    };
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, []);

  const readStoredTokens = useCallback(() => {
    try {
      const raw = localStorage.getItem(storageKey);
      let list = raw ? JSON.parse(raw) : [];
      if (!Array.isArray(list)) list = [];
      const legacy = localStorage.getItem(legacyStorageKey);
      if (legacy) {
        if (!list.includes(legacy)) list.push(legacy);
        localStorage.removeItem(legacyStorageKey);
        localStorage.setItem(storageKey, JSON.stringify(list));
      }
      return list;
    } catch {
      return [];
    }
  }, [storageKey, legacyStorageKey]);

  const saveStoredTokens = useCallback((tokens) => {
    const unique = [...new Set(tokens.filter(Boolean))];
    if (unique.length === 0) {
      localStorage.removeItem(storageKey);
    } else {
      localStorage.setItem(storageKey, JSON.stringify(unique));
    }
  }, [storageKey]);

  const loadMenu = useCallback(async () => {
    setErrorMsg('');
    try {
      const url = qrToken
        ? `/public/menu/table/${qrToken}`
        : `/public/menu/branch/${branchId}`;
      const res = await axiosClient.get(url);
      setMenu(res.data);
      setUsingCache(false);
      localStorage.setItem(menuCacheKey, JSON.stringify(res.data));
      if (res.data.categories?.length > 0) {
        setActiveCategoryId((prev) => prev || res.data.categories[0].id);
      }
    } catch (err) {
      if (menu) {
        setUsingCache(true);
        if (menu.categories?.length > 0) {
          setActiveCategoryId((prev) => prev || menu.categories[0].id);
        }
      } else {
        setErrorMsg(err.response?.data?.error || 'تعذر تحميل المنيو. تأكد من الاتصال بالإنترنت.');
      }
    } finally {
      setLoading(false);
    }
  }, [qrToken, branchId, menuCacheKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const loadTrackedOrders = useCallback(async () => {
    const tokens = readStoredTokens();
    if (tokens.length === 0) return;

    const results = await Promise.all(
      tokens.map(async (tok) => {
        try {
          const res = await axiosClient.get(`/public/orders/${tok}`);
          return res.data;
        } catch {
          return null;
        }
      })
    );

    const validOrders = results.filter(Boolean);
    if (validOrders.length > 0) {
      const activeTokens = validOrders
        .filter((o) => o.status !== 'COMPLETED' && o.status !== 'CANCELLED')
        .map((o) => o.trackingToken);
      saveStoredTokens(activeTokens);
      setTrackedOrders(validOrders);
    }
  }, [readStoredTokens, saveStoredTokens]);

  useEffect(() => {
    loadMenu();
    loadTrackedOrders();
  }, [loadMenu, loadTrackedOrders]);

  useEffect(() => {
    if (isOnline && usingCache) {
      loadMenu();
    }
  }, [isOnline, usingCache, loadMenu]);

  const activeTokensKey = useMemo(
    () =>
      trackedOrders
        .filter((o) => o.status !== 'COMPLETED' && o.status !== 'CANCELLED')
        .map((o) => o.trackingToken)
        .sort()
        .join(','),
    [trackedOrders]
  );

  useEffect(() => {
    if (!activeTokensKey) {
      setWsState(isOnline ? 'connected' : 'offline');
      return;
    }
    const tokens = activeTokensKey.split(',').filter(Boolean);
    setWsState('reconnecting');

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
      reconnectDelay: 5000,
      onConnect: () => {
        setWsState('connected');
        tokens.forEach((token) => {
          client.subscribe(`/topic/orders/${token}`, (msg) => {
            if (!msg.body) return;
            const ev = JSON.parse(msg.body);
            setTrackedOrders((prev) =>
              prev.map((o) => (o.trackingToken === token ? { ...o, status: ev.status } : o))
            );
            if (ev.status === 'COMPLETED' || ev.status === 'CANCELLED') {
              const remaining = readStoredTokens().filter((t) => t !== token);
              saveStoredTokens(remaining);
            }
          });
        });
      },
      onWebSocketClose: () => setWsState(navigator.onLine ? 'reconnecting' : 'offline'),
      onStompError: () => setWsState('reconnecting'),
    });
    client.activate();
    return () => client.deactivate();
  }, [activeTokensKey, isOnline, readStoredTokens, saveStoredTokens]);

  const openItemModal = (item) => {
    if (!item.available || !menu?.branchOpen) return;
    setSelectedItem(item);
    const defaultVar = item.variants?.find((v) => v.isDefault) || item.variants?.[0] || null;
    setModalVariant(defaultVar);
    setModalAddons([]);
    setModalQty(1);
    setModalNotes('');
  };

  const toggleAddon = (addon) => {
    setModalAddons((prev) =>
      prev.some((a) => a.id === addon.id) ? prev.filter((a) => a.id !== addon.id) : [...prev, addon]
    );
  };

  const addToCart = () => {
    if (!selectedItem || !menu?.branchOpen) return;
    const base = modalVariant ? Number(modalVariant.price) : Number(selectedItem.effectivePrice);
    const addonsPrice = modalAddons.reduce((acc, a) => acc + Number(a.price), 0);
    const lineTotal = (base + addonsPrice) * modalQty;

    setCart((prev) => [
      ...prev,
      {
        uid: `${selectedItem.id}-${Date.now()}-${Math.random()}`,
        menuItemId: selectedItem.id,
        name: selectedItem.name,
        variantId: modalVariant?.id || null,
        variantName: modalVariant?.name || null,
        addons: modalAddons,
        quantity: modalQty,
        notes: modalNotes.trim(),
        unitWithAddons: base + addonsPrice,
        lineTotal,
      },
    ]);
    setSelectedItem(null);
  };

  const cartSubtotal = useMemo(() => cart.reduce((sum, c) => sum + c.lineTotal, 0), [cart]);
  const estimatedTax = useMemo(() => (menu ? (cartSubtotal * Number(menu.taxRate || 0)) / 100 : 0), [cartSubtotal, menu]);
  const estimatedService = useMemo(() => {
    if (!menu || !qrToken) return 0;
    return (cartSubtotal * Number(menu.serviceChargeRate || 0)) / 100;
  }, [cartSubtotal, menu, qrToken]);
  const estimatedTotal = cartSubtotal + estimatedTax + estimatedService;

  const submitOrder = async () => {
    if (cart.length === 0 || submitting || !menu?.branchOpen) return;
    if (!isOnline) return alert('لا يوجد اتصال بالإنترنت حالياً. يرجى المحاولة بعد عودة الشبكة.');
    setSubmitting(true);
    try {
      const payload = {
        tableQrToken: qrToken || null,
        branchId: menu.branchId,
        orderType: qrToken ? 'DINE_IN' : 'TAKEAWAY',
        customerName: customerName.trim() || null,
        notes: orderNotes.trim() || null,
        items: cart.map((c) => ({
          menuItemId: c.menuItemId,
          variantId: c.variantId,
          quantity: c.quantity,
          notes: c.notes || null,
          addonIds: c.addons.map((a) => a.id),
        })),
      };
      const res = await axiosClient.post('/public/orders', payload, {
        headers: { 'Idempotency-Key': idempotencyKeyRef.current },
      });
      const updatedTokens = [...readStoredTokens(), res.data.trackingToken];
      saveStoredTokens(updatedTokens);
      setTrackedOrders((prev) => [res.data, ...prev]);
      setCart([]);
      setOrderNotes('');
      setCartOpen(false);
      idempotencyKeyRef.current = generateIdempotencyKey();
    } catch (err) {
      alert(err.response?.data?.detail || err.response?.data?.error || 'تعذر إرسال الطلب');
    } finally {
      setSubmitting(false);
    }
  };

  const sendAlert = async (alertType) => {
    if (!qrToken) return;
    try {
      await axiosClient.post('/public/tables/alert', { tableQrToken: qrToken, alertType });
      setAlertNotice(alertType === 'CALL_WAITER' ? 'تم استدعاء الويتر لطاولتك!' : 'تم إرسال طلب الحساب للكاشير!');
      setTimeout(() => setAlertNotice(''), 4000);
    } catch (err) {
      alert(err.response?.data?.error || 'تعذر إرسال التنبيه');
    }
  };

  const cancelPendingOrder = async (trackingToken) => {
    if (!trackingToken) return;
    if (!window.confirm('هل تريد إلغاء هذا الطلب؟')) return;
    try {
      const res = await axiosClient.post(`/public/orders/${trackingToken}/cancel`);
      setTrackedOrders((prev) => prev.map((o) => (o.trackingToken === trackingToken ? res.data : o)));
      saveStoredTokens(readStoredTokens().filter((t) => t !== trackingToken));
    } catch (err) {
      alert(err.response?.data?.detail || err.response?.data?.error || 'لا يمكن إلغاء الطلب الآن');
    }
  };

  const statusConfig = {
    PENDING: { label: 'في انتظار المطبخ', color: 'bg-amber-100 text-amber-800 border-amber-300', icon: Clock },
    PREPARING: { label: 'جاري تحضير طلبك في المطبخ', color: 'bg-indigo-100 text-indigo-800 border-indigo-300', icon: ChefHat },
    READY: { label: 'طلبك جاهز الآن!', color: 'bg-emerald-100 text-emerald-800 border-emerald-300', icon: CheckCircle2 },
    SERVED: { label: 'تم تقديم الطلب — بالهناء والشفاء', color: 'bg-teal-100 text-teal-800 border-teal-300', icon: UtensilsCrossed },
    COMPLETED: { label: 'تم الدفع وإغلاق الطلب', color: 'bg-slate-100 text-slate-700 border-slate-300', icon: CheckCircle2 },
    CANCELLED: { label: 'تم إلغاء الطلب', color: 'bg-red-100 text-red-700 border-red-300', icon: AlertCircle },
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <p className="font-black text-slate-500 text-lg">جاري تحميل المنيو...</p>
      </div>
    );
  }

  if (errorMsg || !menu) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 p-6">
        <div className="card p-8 max-w-md text-center space-y-3">
          <AlertCircle size={48} className="mx-auto text-red-500" />
          <h2 className="text-xl font-black text-slate-800">تعذر فتح المنيو</h2>
          <p className="text-sm text-slate-500 font-semibold">{errorMsg}</p>
        </div>
      </div>
    );
  }

  const activeCategory = menu.categories.find((c) => c.id === activeCategoryId) || menu.categories[0];

  return (
    <div className="min-h-screen bg-slate-50 pb-28 max-w-xl mx-auto shadow-xl bg-white" dir="rtl">
      <header className="sticky top-0 z-30 bg-gradient-to-br from-slate-900 to-indigo-950 text-white p-5 rounded-b-3xl shadow-lg">
        <div className="flex justify-between items-start gap-3">
          <div>
            <p className="text-xs text-orange-300 font-bold">{menu.branchName}</p>
            <h1 className="text-2xl font-black mt-0.5">{menu.organizationName}</h1>
          </div>
          <div className="flex flex-col items-end gap-1.5">
            {menu.tableNumber && (
              <span className="bg-orange-500 text-white px-3.5 py-1 rounded-2xl text-sm font-black shadow">
                طاولة {menu.tableNumber}
              </span>
            )}
            <span className={`px-2.5 py-0.5 rounded-full text-[11px] font-bold flex items-center gap-1 ${
              !isOnline || wsState === 'offline'
                ? 'bg-red-500/20 text-red-200'
                : wsState === 'reconnecting'
                  ? 'bg-amber-500/20 text-amber-200'
                  : 'bg-emerald-500/20 text-emerald-200'
            }`}>
              {isOnline ? <Wifi size={12} /> : <WifiOff size={12} />}
              {!isOnline ? 'غير متصل' : wsState === 'reconnecting' ? 'جارٍ إعادة الاتصال...' : 'متصل'}
            </span>
          </div>
        </div>

        {qrToken && (
          <div className="flex gap-2 mt-4">
            <button
              onClick={() => sendAlert('CALL_WAITER')}
              disabled={!isOnline}
              className="flex-1 bg-white/10 hover:bg-white/20 py-2 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 disabled:opacity-50"
            >
              <Bell size={15} className="text-orange-300" /> نداء الويتر
            </button>
            <button
              onClick={() => sendAlert('REQUEST_BILL')}
              disabled={!isOnline}
              className="flex-1 bg-white/10 hover:bg-white/20 py-2 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 disabled:opacity-50"
            >
              <Receipt size={15} className="text-emerald-300" /> طلب الحساب
            </button>
          </div>
        )}
      </header>

      {usingCache && (
        <div className="m-4 p-3 rounded-2xl bg-amber-50 border border-amber-200 text-amber-800 text-xs font-bold text-center">
          أنت تتصفح نسخة محفوظة من المنيو بسبب ضعف أو انقطاع الإنترنت.
        </div>
      )}

      {alertNotice && (
        <div className="m-4 p-3 rounded-2xl bg-emerald-600 text-white text-center font-bold text-sm shadow">
          {alertNotice}
        </div>
      )}

      {!menu.branchOpen && (
        <div className="m-4 p-4 rounded-2xl bg-red-50 border border-red-200 text-red-700 text-sm font-bold text-center">
          {menu.closureMessage || 'المطعم مغلق لاستقبال الطلبات حالياً'}
        </div>
      )}

      {trackedOrders.length > 0 && (
        <div className="m-4 space-y-3">
          {trackedOrders.map((order) => {
            const cfg = statusConfig[order.status] || statusConfig.PENDING;
            const Icon = cfg.icon;
            const finished = order.status === 'COMPLETED' || order.status === 'CANCELLED';
            return (
              <div key={order.trackingToken} className="p-4 rounded-2xl border-2 shadow-sm bg-white border-indigo-200">
                <div className="flex justify-between items-center mb-2">
                  <span className="font-black text-slate-800">طلب رقم #{order.orderNumber}</span>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-slate-400">{order.totalAmount} ج</span>
                    {finished && (
                      <button
                        onClick={() => setTrackedOrders((prev) => prev.filter((o) => o.trackingToken !== order.trackingToken))}
                        className="text-slate-400 hover:text-slate-700"
                        title="إخفاء"
                      >
                        <X size={15} />
                      </button>
                    )}
                  </div>
                </div>
                <div className={`p-3 rounded-xl border flex items-center justify-between gap-2 ${cfg.color}`}>
                  <div className="flex items-center gap-2 font-black text-sm">
                    <Icon size={18} />
                    <span>{cfg.label}</span>
                  </div>
                  {order.status === 'PENDING' && (
                    <button
                      onClick={() => cancelPendingOrder(order.trackingToken)}
                      className="text-xs bg-white/80 text-red-600 px-2.5 py-1 rounded-lg font-bold"
                    >
                      إلغاء
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <div className="flex gap-2 overflow-x-auto px-4 py-3 sticky top-[124px] bg-white/95 backdrop-blur z-20 border-b border-slate-100">
        {menu.categories.map((cat) => (
          <button
            key={cat.id}
            onClick={() => setActiveCategoryId(cat.id)}
            className={`px-4 py-2 rounded-full text-sm font-black whitespace-nowrap transition-all ${
              activeCategory?.id === cat.id
                ? 'bg-orange-500 text-white shadow'
                : 'bg-slate-100 text-slate-600'
            }`}
          >
            {cat.name}
          </button>
        ))}
      </div>

      <div className="p-4 space-y-3">
        {activeCategory?.items?.length === 0 && (
          <p className="text-center text-slate-400 font-bold py-10">لا توجد أصناف في هذا القسم.</p>
        )}
        {activeCategory?.items?.map((item) => {
          const canOrder = item.available && menu.branchOpen;
          return (
            <div
              key={item.id}
              onClick={canOrder ? () => openItemModal(item) : undefined}
              className={`p-4 rounded-2xl border flex justify-between items-center gap-3 transition-all ${
                canOrder
                  ? 'bg-white border-slate-200 hover:border-orange-300 cursor-pointer shadow-sm'
                  : 'bg-slate-50 border-slate-100 opacity-60 cursor-not-allowed'
              }`}
            >
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <h3 className="font-black text-slate-800">{item.name}</h3>
                  {!item.available && (
                    <span className="text-[10px] bg-red-100 text-red-600 px-2 py-0.5 rounded-full font-bold">
                      غير متوفر
                    </span>
                  )}
                </div>
                {item.description && <p className="text-xs text-slate-500 mt-1">{item.description}</p>}
                <div className="flex items-center gap-3 mt-2">
                  <span className="font-black text-orange-600">{item.effectivePrice} ج</span>
                  <span className="text-[11px] text-slate-400 font-semibold flex items-center gap-1">
                    <Clock size={12} /> {item.prepTimeMinutes} د
                  </span>
                </div>
              </div>
              {canOrder && (
                <button className="w-10 h-10 rounded-xl bg-orange-50 text-orange-600 flex items-center justify-center font-black">
                  <Plus size={20} />
                </button>
              )}
            </div>
          );
        })}
      </div>

      {cart.length > 0 && menu.branchOpen && (
        <div className="fixed bottom-4 left-1/2 -translate-x-1/2 w-[92%] max-w-lg z-40">
          <button
            onClick={() => setCartOpen(true)}
            className="w-full bg-slate-900 text-white py-4 px-5 rounded-2xl shadow-2xl flex items-center justify-between font-black"
          >
            <div className="flex items-center gap-2.5">
              <span className="bg-orange-500 text-white w-7 h-7 rounded-lg flex items-center justify-center text-sm">
                {cart.reduce((s, c) => s + c.quantity, 0)}
              </span>
              <span>عرض السلة وإرسال الطلب</span>
            </div>
            <span className="text-orange-300">{estimatedTotal.toFixed(2)} ج</span>
          </button>
        </div>
      )}

      {selectedItem && (
        <div className="modal-backdrop flex items-end sm:items-center justify-center z-50" onClick={() => setSelectedItem(null)}>
          <div className="bg-white w-full max-w-lg rounded-t-3xl sm:rounded-3xl p-6 max-h-[85vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
            <div className="flex justify-between items-start mb-4">
              <div>
                <h3 className="text-xl font-black text-slate-800">{selectedItem.name}</h3>
                {selectedItem.description && <p className="text-xs text-slate-500 mt-1">{selectedItem.description}</p>}
              </div>
              <button onClick={() => setSelectedItem(null)} className="text-slate-400">
                <X size={22} />
              </button>
            </div>

            {selectedItem.variants?.length > 0 && (
              <div className="mb-4">
                <p className="font-bold text-sm text-slate-700 mb-2">اختر الحجم:</p>
                <div className="space-y-2">
                  {selectedItem.variants.map((v) => (
                    <label
                      key={v.id}
                      className={`flex justify-between items-center p-3 rounded-xl border cursor-pointer ${
                        modalVariant?.id === v.id ? 'border-orange-500 bg-orange-50' : 'border-slate-200'
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <input
                          type="radio"
                          checked={modalVariant?.id === v.id}
                          onChange={() => setModalVariant(v)}
                          className="accent-orange-500"
                        />
                        <span className="font-bold text-sm">{v.name}</span>
                      </div>
                      <span className="font-black text-sm text-orange-600">{v.price} ج</span>
                    </label>
                  ))}
                </div>
              </div>
            )}

            {selectedItem.addons?.filter((a) => a.available).length > 0 && (
              <div className="mb-4">
                <p className="font-bold text-sm text-slate-700 mb-2">إضافات اختيارية:</p>
                <div className="space-y-2">
                  {selectedItem.addons.filter((a) => a.available).map((a) => {
                    const checked = modalAddons.some((x) => x.id === a.id);
                    return (
                      <label
                        key={a.id}
                        className={`flex justify-between items-center p-3 rounded-xl border cursor-pointer ${
                          checked ? 'border-indigo-500 bg-indigo-50' : 'border-slate-200'
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggleAddon(a)}
                            className="accent-indigo-600"
                          />
                          <span className="font-bold text-sm">{a.name}</span>
                        </div>
                        <span className="font-bold text-xs text-indigo-700">+{a.price} ج</span>
                      </label>
                    );
                  })}
                </div>
              </div>
            )}

            <input
              type="text"
              value={modalNotes}
              onChange={(e) => setModalNotes(e.target.value)}
              placeholder="ملاحظات للمطبخ (بدون بصل، تسوية زيادة...)"
              className="input-fancy w-full p-3 text-sm mb-4"
            />

            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-3 bg-slate-100 px-3 py-2 rounded-xl">
                <button onClick={() => setModalQty((q) => Math.max(1, q - 1))}><Minus size={18} /></button>
                <span className="font-black text-lg w-6 text-center">{modalQty}</span>
                <button onClick={() => setModalQty((q) => q + 1)}><Plus size={18} /></button>
              </div>
              <button onClick={addToCart} className="btn-primary flex-1 py-3.5 font-black">
                إضافة للسلة
              </button>
            </div>
          </div>
        </div>
      )}

      {cartOpen && (
        <div className="modal-backdrop flex items-end sm:items-center justify-center z-50" onClick={() => setCartOpen(false)}>
          <div className="bg-white w-full max-w-lg rounded-t-3xl sm:rounded-3xl p-6 max-h-[88vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-xl font-black flex items-center gap-2">
                <ShoppingCart className="text-orange-500" /> سلة الطلبات
              </h3>
              <button onClick={() => setCartOpen(false)}><X size={22} /></button>
            </div>

            <div className="space-y-3 mb-4">
              {cart.map((c) => (
                <div key={c.uid} className="p-3 rounded-2xl bg-slate-50 border border-slate-200 flex justify-between items-start">
                  <div>
                    <p className="font-black text-sm text-slate-800">
                      {c.quantity}x {c.name} {c.variantName ? `(${c.variantName})` : ''}
                    </p>
                    {c.addons.length > 0 && (
                      <p className="text-xs text-indigo-600 font-semibold">
                        + {c.addons.map((a) => a.name).join('، ')}
                      </p>
                    )}
                    {c.notes && <p className="text-xs text-slate-500">ملاحظة: {c.notes}</p>}
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-black text-sm">{c.lineTotal.toFixed(2)} ج</span>
                    <button
                      onClick={() => setCart((prev) => prev.filter((x) => x.uid !== c.uid))}
                      className="text-red-400 hover:text-red-600"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ))}
            </div>

            {!qrToken && (
              <input
                type="text"
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                placeholder="اسمك (لنداء الاستلام)"
                className="input-fancy w-full p-3 text-sm mb-3"
              />
            )}
            <input
              type="text"
              value={orderNotes}
              onChange={(e) => setOrderNotes(e.target.value)}
              placeholder="ملاحظات عامة للطلب..."
              className="input-fancy w-full p-3 text-sm mb-4"
            />

            <div className="bg-slate-50 p-4 rounded-2xl space-y-1.5 text-sm font-bold mb-5">
              <div className="flex justify-between text-slate-600">
                <span>المجموع الفرعي:</span><span>{cartSubtotal.toFixed(2)} ج</span>
              </div>
              <div className="flex justify-between text-slate-600">
                <span>الضريبة ({menu.taxRate}%):</span><span>{estimatedTax.toFixed(2)} ج</span>
              </div>
              {qrToken && Number(menu.serviceChargeRate) > 0 && (
                <div className="flex justify-between text-slate-600">
                  <span>الخدمة ({menu.serviceChargeRate}%):</span><span>{estimatedService.toFixed(2)} ج</span>
                </div>
              )}
              <div className="flex justify-between text-lg font-black text-slate-900 pt-2 border-t border-slate-200">
                <span>الإجمالي التقديري:</span><span>{estimatedTotal.toFixed(2)} ج</span>
              </div>
            </div>

            <button
              onClick={submitOrder}
              disabled={submitting || !menu.branchOpen || !isOnline}
              className="btn-primary w-full py-4 font-black text-lg"
            >
              {submitting ? 'جاري الإرسال للمطبخ...' : !isOnline ? 'بانتظار عودة الإنترنت...' : 'تأكيد وإرسال الطلب'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}