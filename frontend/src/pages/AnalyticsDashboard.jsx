import { useState, useEffect } from 'react';
import axiosClient from '../api/axiosClient';
import { useAuth } from '../context/AuthContext';
import DashboardLayout from '../components/DashboardLayout';
import { ShoppingBag, Banknote, TrendingUp, Flame, Receipt } from 'lucide-react';
import {
  PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Legend, CartesianGrid
} from 'recharts';

const COLORS = ['#f97316', '#4f46e5', '#10b981'];

export default function AnalyticsDashboard() {
  const { selectedBranchId } = useAuth();
  const [stats, setStats] = useState({
    todayOrdersCount: 0,
    activeOrdersCount: 0,
    todayRevenue: 0,
    averageTicket: 0,
    qrCount: 0,
    posCount: 0,
    waiterCount: 0,
    topItems: [],
    weekly: [],
  });

  useEffect(() => {
    if (!selectedBranchId) return;
    const fetchStats = async () => {
      try {
        const res = await axiosClient.get(`/analytics/branches/${selectedBranchId}`);
        setStats(res.data);
      } catch (error) {
        console.error(error);
      }
    };
    fetchStats();
  }, [selectedBranchId]);

  const pieData = [
    { name: 'طلب QR من الطاولة', value: stats.qrCount || 0 },
    { name: 'كاشير (POS)', value: stats.posCount || 0 },
    { name: 'ويتر (Waiter)', value: stats.waiterCount || 0 },
  ];

  return (
    <DashboardLayout title="تقارير المبيعات والإحصائيات">
      <div className="max-w-6xl mx-auto space-y-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 stagger">
          <div className="rounded-3xl p-6 text-white bg-gradient-to-br from-emerald-500 to-teal-700 shadow-lg flex justify-between items-start">
            <div>
              <p className="font-bold text-emerald-100 mb-2">مبيعات اليوم</p>
              <p className="text-4xl font-black">{stats.todayRevenue ?? 0} ج</p>
            </div>
            <Banknote size={32} className="text-emerald-100" />
          </div>

          <div className="rounded-3xl p-6 text-white bg-gradient-to-br from-orange-500 to-rose-600 shadow-lg flex justify-between items-start">
            <div>
              <p className="font-bold text-orange-100 mb-2">طلبات اليوم</p>
              <p className="text-4xl font-black">{stats.todayOrdersCount ?? 0}</p>
            </div>
            <ShoppingBag size={32} className="text-orange-100" />
          </div>

          <div className="card p-6 flex justify-between items-start">
            <div>
              <p className="font-bold text-slate-400 mb-2">متوسط الفاتورة</p>
              <p className="text-4xl font-black text-slate-800">{stats.averageTicket ?? 0} ج</p>
            </div>
            <Receipt size={32} className="text-indigo-500" />
          </div>

          <div className="card p-6 flex justify-between items-start">
            <div>
              <p className="font-bold text-slate-400 mb-2">طلبات قيد التنفيذ الآن</p>
              <p className="text-4xl font-black text-orange-500">{stats.activeOrdersCount ?? 0}</p>
            </div>
            <Flame size={32} className="text-orange-500" />
          </div>
        </div>

        {/* الأكثر مبيعاً اليوم */}
        {stats.topItems?.length > 0 && (
          <div className="card p-6">
            <h3 className="text-xl font-black text-slate-800 mb-4">الأصناف الأكثر طلباً اليوم</h3>
            <div className="grid grid-cols-1 sm:grid-cols-5 gap-3">
              {stats.topItems.map((it, idx) => (
                <div key={idx} className="p-4 rounded-2xl bg-slate-50 border border-slate-200 text-center">
                  <p className="font-black text-slate-800 text-sm">{it.name}</p>
                  <p className="text-2xl font-black text-orange-600 mt-1">{it.quantity}</p>
                  <p className="text-[11px] text-slate-400 font-bold">وحدة مباعة</p>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div className="card p-8">
            <h3 className="text-xl font-black text-slate-800 mb-6 flex items-center gap-2">
              <TrendingUp className="text-indigo-500" /> مصدر الطلبات اليوم
            </h3>
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={pieData} cx="50%" cy="50%" innerRadius={65} outerRadius={105} paddingAngle={6} dataKey="value">
                    {pieData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                  </Pie>
                  <Tooltip />
                  <Legend verticalAlign="bottom" height={36} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div className="card p-8">
            <h3 className="text-xl font-black text-slate-800 mb-6 flex items-center gap-2">
              <TrendingUp className="text-indigo-500" /> إيرادات آخر 7 أيام (ج)
            </h3>
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={stats.weekly}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="day" />
                  <YAxis />
                  <Tooltip />
                  <Bar dataKey="revenue" fill="#f97316" radius={[10, 10, 0, 0]} barSize={38} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
}