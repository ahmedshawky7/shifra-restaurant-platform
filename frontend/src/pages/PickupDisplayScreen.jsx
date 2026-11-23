import { useEffect, useState, useRef, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { UtensilsCrossed, Volume2, VolumeX, Flame, CheckCircle2 } from 'lucide-react';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export default function PickupDisplayScreen() {
  const [searchParams] = useSearchParams();
  const branchId = Number(searchParams.get('branchId')) || 1;

  const [data, setData] = useState({ organizationName: '', branchName: '', preparing: [], ready: [] });
  const [connected, setConnected] = useState(false);
  const [soundEnabled, setSoundEnabled] = useState(true);

  const soundRef = useRef(true);
  const audioCtxRef = useRef(null);

  useEffect(() => {
    soundRef.current = soundEnabled;
  }, [soundEnabled]);

  const announceReady = useCallback((orderNumber) => {
    if (!soundRef.current || !orderNumber) return;
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (AudioCtx) {
        if (!audioCtxRef.current) audioCtxRef.current = new AudioCtx();
        const ctx = audioCtxRef.current;
        if (ctx.state === 'suspended') ctx.resume();
        const now = ctx.currentTime;
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.frequency.setValueAtTime(1046.5, now);
        gain.gain.setValueAtTime(0.001, now);
        gain.gain.exponentialRampToValueAtTime(0.25, now + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.45);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(now);
        osc.stop(now + 0.46);
      }
    } catch {}

    try {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(`طلب رقم ${orderNumber} جاهز للاستلام`);
      utterance.lang = 'ar-EG';
      utterance.rate = 0.9;
      setTimeout(() => window.speechSynthesis.speak(utterance), 250);
    } catch {}
  }, []);

  const fetchPickup = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/public/branches/${branchId}/pickup`);
      if (res.ok) setData(await res.json());
    } catch {}
  }, [branchId]);

  useEffect(() => {
    fetchPickup();
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/branch/${branchId}/pickup`, (msg) => {
          if (!msg.body) return;
          const ev = JSON.parse(msg.body);
          if (ev.eventType === 'ORDER_READY') {
            announceReady(ev.orderNumber);
          }
          fetchPickup();
        });
      },
      onDisconnect: () => setConnected(false),
    });
    client.activate();
    return () => client.deactivate();
  }, [branchId, fetchPickup, announceReady]);

  return (
    <div className="min-h-screen bg-[#070b18] text-white p-8 flex flex-col" dir="rtl">
      <header className="flex justify-between items-center pb-6 border-b border-slate-800 mb-8">
        <div className="flex items-center gap-4">
          <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-orange-500 to-rose-600 flex items-center justify-center shadow-lg">
            <UtensilsCrossed size={28} />
          </div>
          <div>
            <p className="text-orange-400 font-bold text-sm">{data.organizationName || 'المطعم'}</p>
            <h1 className="text-3xl font-black">شاشة استلام الطلبات — {data.branchName}</h1>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-2 bg-slate-900 border border-slate-700 px-4 py-2 rounded-full text-sm font-bold">
            <span className={`w-2.5 h-2.5 rounded-full live-dot ${connected ? 'bg-emerald-400' : 'bg-red-500'}`} />
            {connected ? 'مباشر' : 'غير متصل'}
          </span>
          <button
            onClick={() => setSoundEnabled(!soundEnabled)}
            className="flex items-center gap-2 bg-slate-900 border border-slate-700 px-4 py-2 rounded-full text-sm font-bold"
          >
            {soundEnabled ? <Volume2 size={18} className="text-emerald-400" /> : <VolumeX size={18} className="text-slate-400" />}
            {soundEnabled ? 'الصوت مفعّل' : 'صامت'}
          </button>
        </div>
      </header>

      <div className="flex-1 grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* جاري التحضير */}
        <div className="bg-slate-900/75 border border-slate-800 rounded-[2.5rem] p-8">
          <h2 className="text-3xl font-black text-amber-400 mb-6 flex items-center gap-3">
            <Flame size={32} /> جاري التحضير
          </h2>
          <div className="grid grid-cols-3 gap-4">
            {data.preparing.map((o) => (
              <div key={o.orderNumber} className="bg-slate-950/70 border border-amber-500/30 rounded-3xl py-6 text-center text-5xl font-black text-amber-300">
                #{o.orderNumber}
              </div>
            ))}
          </div>
        </div>

        {/* جاهز للاستلام */}
        <div className="bg-emerald-950/30 border border-emerald-500/40 rounded-[2.5rem] p-8">
          <h2 className="text-3xl font-black text-emerald-400 mb-6 flex items-center gap-3">
            <CheckCircle2 size={32} /> جاهز للاستلام الآن
          </h2>
          <div className="grid grid-cols-3 gap-4">
            {data.ready.map((o) => (
              <div key={o.orderNumber} className="bg-emerald-500 text-slate-950 rounded-3xl py-6 text-center text-6xl font-black shadow-xl animate-number-pop">
                #{o.orderNumber}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}