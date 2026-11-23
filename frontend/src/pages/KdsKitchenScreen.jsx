import { useState, useEffect, useCallback, useRef } from 'react';
import { createPortal } from 'react-dom';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axiosClient from '../api/axiosClient';
import { useAuth } from '../context/AuthContext';
import DashboardLayout from '../components/DashboardLayout';
import {
  ChefHat, Flame, CheckCircle2, Clock, Volume2, VolumeX, PackageX, X
} from 'lucide-react';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export default function KdsKitchenScreen() {
  const { selectedBranchId } = useAuth();
  const [orders, setOrders] = useState([]);
  const [connected, setConnected] = useState(false);
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [nowTick, setNowTick] = useState(Date.now());
  const [updatedOrderIds, setUpdatedOrderIds] = useState([]);

  const [itemsModalOpen, setItemsModalOpen] = useState(false);
  const [branchItems, setBranchItems] = useState([]);

  const soundRef = useRef(true);
  const audioCtxRef = useRef(null);

  useEffect(() => {
    soundRef.current = soundEnabled;
  }, [soundEnabled]);

  useEffect(() => {
    const timer = setInterval(() => setNowTick(Date.now()), 30000);
    return () => clearInterval(timer);
  }, []);

  const playAlertChime = useCallback(() => {
    if (!soundRef.current) return;
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      if (!audioCtxRef.current) audioCtxRef.current = new AudioCtx();
      const ctx = audioCtxRef.current;
      if (ctx.state === 'suspended') ctx.resume();

      const now = ctx.currentTime;
      [660, 880].forEach((freq, idx) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.frequency.setValueAtTime(freq, now + idx * 0.15);
        gain.gain.setValueAtTime(0.001, now + idx * 0.15);
        gain.gain.exponentialRampToValueAtTime(0.25, now + idx * 0.15 + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.15 + 0.3);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now + idx * 0.15);
        osc.stop(now + idx * 0.15 + 0.32);
      });
    } catch {
      // ignore
    }
  }, []);

  const fetchOrders = useCallback(async () => {
    if (!selectedBranchId) return;
    try {
      const res = await axiosClient.get(`/kds/branches/${selectedBranchId}/orders`);
      setOrders(res.data || []);
    } catch (e) {
      console.error('Failed to fetch KDS orders:', e);
    }
  }, [selectedBranchId]);

  useEffect(() => {
    if (!selectedBranchId) return;
    fetchOrders();

    const token = localStorage.getItem('token');
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/branch/${selectedBranchId}/kds`, (msg) => {
          if (!msg.body) return;
          const ev = JSON.parse(msg.body);
          if (ev.eventType === 'ORDER_CREATED' || ev.eventType === 'ORDER_UPDATED') {
            playAlertChime();
          }
          if (ev.eventType === 'ORDER_UPDATED' && ev.orderId) {
            setUpdatedOrderIds((prev) => [...new Set([...prev, ev.orderId])]);
            setTimeout(() => {
              setUpdatedOrderIds((prev) => prev.filter((id) => id !== ev.orderId));
            }, 15000);
          }
          fetchOrders();
        });
      },
      onDisconnect: () => setConnected(false),
    });

    client.activate();
    return () => client.deactivate();
  }, [selectedBranchId, fetchOrders, playAlertChime]);

  const handleStart = async (orderId) => {
    await axiosClient.post(`/kds/branches/${selectedBranchId}/orders/${orderId}/start`);
    fetchOrders();
  };

  const handleReady = async (orderId) => {
    await axiosClient.post(`/kds/branches/${selectedBranchId}/orders/${orderId}/ready`);
    fetchOrders();
  };

  const openItemsAvailability = async () => {
    const res = await axiosClient.get(`/kds/branches/${selectedBranchId}/menu-items`);
    setBranchItems(res.data || []);
    setItemsModalOpen(true);
  };

  const toggleItemAvailable = async (item) => {
    await axiosClient.put(`/kds/branches/${selectedBranchId}/items/${item.id}/availability`, {
      available: !item.available,
    });
    openItemsAvailability();
  };

  const elapsedMinutes = (createdAt) => {
    if (!createdAt) return 0;
    return Math.max(0, Math.floor((nowTick - new Date(createdAt).getTime()) / 60000));
  };

  const pendingOrders = orders.filter((o) => o.status === 'PENDING');
  const preparingOrders = orders.filter((o) => o.status === 'PREPARING');
  const readyOrders = orders.filter((o) => o.status === 'READY');

  if (!selectedBranchId) {
    return (
      <DashboardLayout title="شاشة المطبخ (KDS)">
        <div className="card p-10 text-center font-bold text-slate-500">اختر فرعاً لعرض طلبات المطبخ.</div>
      </DashboardLayout>
    );
  }

  const OrderCard = ({ o, accent, actionLabel, onAction, actionClass }) => {
    const mins = elapsedMinutes(o.createdAt);
    const isUpdated = updatedOrderIds.includes(o.orderId);
    return (
      <div className={`p-5 rounded-2xl bg-white border-2 shadow-sm space-y-3 ${accent}`}>
        {isUpdated && (
          <div className="p-2 rounded-xl bg-blue-600 text-white text-xs font-black text-center animate-pulse">
            ⚠️ تم تعديل هذا الطلب من الويتر/الكاشير!
          </div>
        )}
        <div className="flex justify-between items-start">
          <div>
            <span className="text-2xl font-black text-slate-900">#{o.orderNumber}</span>
            <span className="mr-2 px-2.5 py-0.5 rounded-full text-xs font-black bg-slate-100 text-slate-700">
              {o.tableNumber ? `طاولة ${o.tableNumber}` : o.orderType === 'DELIVERY' ? 'دليفري' : 'تيك أواي'}
            </span>
          </div>
          <span className={`text-xs font-black px-2.5 py-1 rounded-full flex items-center gap-1 ${
            mins >= 15 ? 'bg-red-100 text-red-700' : 'bg-slate-100 text-slate-600'
          }`}>
            <Clock size={12} /> {mins} د
          </span>
        </div>

        {o.notes && (
          <div className="p-2 rounded-xl bg-amber-50 border border-amber-200 text-xs font-bold text-amber-800">
            ملاحظة: {o.notes}
          </div>
        )}

        <div className="space-y-2 border-t border-b border-slate-100 py-3">
          {o.items?.map((item, idx) => (
            <div key={idx} className="text-sm">
              <div className="font-black text-slate-800">
                <span className="inline-block bg-slate-900 text-white px-2 py-0.5 rounded-md text-xs ml-2">
                  {item.quantity}x
                </span>
                {item.itemName} {item.variantName ? `(${item.variantName})` : ''}
              </div>
              {item.addonNames?.length > 0 && (
                <p className="text-xs text-indigo-600 font-bold mr-8">+ {item.addonNames.join('، ')}</p>
              )}
              {item.notes && (
                <p className="text-xs text-rose-600 font-bold mr-8">* {item.notes}</p>
              )}
            </div>
          ))}
        </div>

        {actionLabel && (
          <button onClick={() => onAction(o.orderId)} className={`w-full py-3 rounded-xl font-black text-white ${actionClass}`}>
            {actionLabel}
          </button>
        )}
      </div>
    );
  };

  return (
    <DashboardLayout title="شاشة المطبخ اللحظية (KDS)">
      <div className="max-w-7xl mx-auto space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 bg-white px-4 py-2.5 rounded-xl border border-slate-200 font-bold text-sm">
              <span className={`w-3 h-3 rounded-full live-dot ${connected ? 'bg-emerald-500' : 'bg-red-500'}`} />
              <span>{connected ? 'متصل بالمطبخ (Live)' : 'جاري الاتصال...'}</span>
            </div>
            <button
              onClick={() => setSoundEnabled(!soundEnabled)}
              className={`px-4 py-2.5 rounded-xl font-bold text-sm flex items-center gap-2 border ${
                soundEnabled ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-slate-100 text-slate-500 border-slate-200'
              }`}
            >
              {soundEnabled ? <Volume2 size={16} /> : <VolumeX size={16} />}
              {soundEnabled ? 'التنبيه الصوتي مفعّل' : 'صامت'}
            </button>
          </div>

          <button
            onClick={openItemsAvailability}
            className="px-4 py-2.5 rounded-xl bg-rose-50 text-rose-700 border border-rose-200 font-bold text-sm flex items-center gap-2 hover:bg-rose-100"
          >
            <PackageX size={16} /> إدارة توفر الأصناف (86)
          </button>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="space-y-4">
            <div className="p-4 rounded-2xl bg-amber-50 border border-amber-200 flex items-center justify-between">
              <span className="font-black text-amber-800 flex items-center gap-2">
                <Clock size={20} /> طلبات جديدة (Pending)
              </span>
              <span className="bg-amber-600 text-white px-3 py-0.5 rounded-full font-black text-sm">
                {pendingOrders.length}
              </span>
            </div>
            {pendingOrders.map((o) => (
              <OrderCard
                key={o.orderId}
                o={o}
                accent="border-amber-300"
                actionLabel="بدء التحضير"
                onAction={handleStart}
                actionClass="bg-indigo-600 hover:bg-indigo-700"
              />
            ))}
          </div>

          <div className="space-y-4">
            <div className="p-4 rounded-2xl bg-indigo-50 border border-indigo-200 flex items-center justify-between">
              <span className="font-black text-indigo-800 flex items-center gap-2">
                <Flame size={20} /> قيد التحضير (Preparing)
              </span>
              <span className="bg-indigo-600 text-white px-3 py-0.5 rounded-full font-black text-sm">
                {preparingOrders.length}
              </span>
            </div>
            {preparingOrders.map((o) => (
              <OrderCard
                key={o.orderId}
                o={o}
                accent="border-indigo-300"
                actionLabel="جاهز للتقديم / الاستلام"
                onAction={handleReady}
                actionClass="bg-emerald-600 hover:bg-emerald-700"
              />
            ))}
          </div>

          <div className="space-y-4">
            <div className="p-4 rounded-2xl bg-emerald-50 border border-emerald-200 flex items-center justify-between">
              <span className="font-black text-emerald-800 flex items-center gap-2">
                <CheckCircle2 size={20} /> جاهز (Ready)
              </span>
              <span className="bg-emerald-600 text-white px-3 py-0.5 rounded-full font-black text-sm">
                {readyOrders.length}
              </span>
            </div>
            {readyOrders.map((o) => (
              <OrderCard key={o.orderId} o={o} accent="border-emerald-300" />
            ))}
          </div>
        </div>
      </div>

      {itemsModalOpen &&
        createPortal(
          <div className="modal-backdrop flex items-center justify-center p-4" dir="rtl" onClick={() => setItemsModalOpen(false)}>
            <div className="bg-white rounded-3xl w-[500px] max-w-full max-h-[85vh] flex flex-col shadow-2xl overflow-hidden" onClick={(e) => e.stopPropagation()}>
              <div className="flex justify-between items-center p-6 border-b border-slate-100 bg-white">
                <h3 className="text-lg font-black text-slate-800 flex items-center gap-2">
                  <ChefHat className="text-orange-500" /> توفر الأصناف في هذا الفرع
                </h3>
                <button onClick={() => setItemsModalOpen(false)} className="p-2 rounded-xl hover:bg-slate-100 text-slate-500">
                  <X size={20} />
                </button>
              </div>
              <div className="p-6 space-y-2.5 overflow-y-auto">
                {branchItems.map((it) => (
                  <div key={it.id} className="flex justify-between items-center p-3.5 rounded-2xl border border-slate-200 hover:bg-slate-50">
                    <span className="font-bold text-slate-800">{it.name}</span>
                    <button
                      onClick={() => toggleItemAvailable(it)}
                      className={`px-4 py-2 rounded-xl font-bold text-xs transition-all ${
                        it.available ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : 'bg-red-50 text-red-600 border border-red-200'
                      }`}
                    >
                      {it.available ? 'متاح ✓ (اضغط للإيقاف)' : 'نفذ من المطبخ ✕ (اضغط للتفعيل)'}
                    </button>
                  </div>
                ))}
              </div>
            </div>
          </div>,
          document.body
        )}
    </DashboardLayout>
  );
}