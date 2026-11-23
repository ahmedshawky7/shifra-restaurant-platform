import { useState, useEffect } from 'react';
import axiosClient from '../api/axiosClient';
import { useAuth } from '../context/AuthContext';
import DashboardLayout from '../components/DashboardLayout';
import { MapPin, Clock, Save, Percent } from 'lucide-react';

const DAYS = [
  { key: 'SATURDAY', label: 'السبت' },
  { key: 'SUNDAY', label: 'الأحد' },
  { key: 'MONDAY', label: 'الاثنين' },
  { key: 'TUESDAY', label: 'الثلاثاء' },
  { key: 'WEDNESDAY', label: 'الأربعاء' },
  { key: 'THURSDAY', label: 'الخميس' },
  { key: 'FRIDAY', label: 'الجمعة' },
];

export default function BranchesManagement() {
  const { refreshBranches } = useAuth();
  const [branches, setBranches] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchBranches = async () => {
    try {
      const res = await axiosClient.get('/branches/my');
      setBranches(res.data || []);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBranches();
  }, []);

  const toggleDay = (idx, dayKey) => {
    const updated = [...branches];
    const days = (updated[idx].workingDays || '').split(',').filter(Boolean);
    updated[idx].workingDays = days.includes(dayKey)
      ? days.filter((d) => d !== dayKey).join(',')
      : [...days, dayKey].join(',');
    setBranches(updated);
  };

  const saveBranch = async (branch) => {
    try {
      await axiosClient.put(`/branches/${branch.id}/schedule`, {
        name: branch.name,
        address: branch.address,
        city: branch.city,
        workingDays: branch.workingDays || '',
        openTime: branch.openTime || '09:00',
        closeTime: branch.closeTime || '23:59',
        taxRate: branch.taxRate ?? 14,
        serviceChargeRate: branch.serviceChargeRate ?? 0,
      });
      alert(`تم حفظ إعدادات ${branch.name}`);
      await fetchBranches();
      await refreshBranches();
    } catch {
      alert('فشل في الحفظ');
    }
  };

  if (loading) {
    return (
      <DashboardLayout title="إدارة الفروع">
        <div className="text-center p-10 font-bold">جاري التحميل...</div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout title="إدارة الفروع وساعات العمل والضرائب">
      <div className="max-w-5xl mx-auto space-y-6">
        {branches.map((branch, idx) => (
          <div key={branch.id} className="card p-8">
            <div className="flex flex-wrap justify-between items-start gap-4 mb-6">
              <div>
                <input
                  type="text"
                  value={branch.name}
                  onChange={(e) => {
                    const nb = [...branches];
                    nb[idx].name = e.target.value;
                    setBranches(nb);
                  }}
                  className="text-2xl font-bold bg-transparent border-b-2 border-orange-200 outline-none"
                />
                <div className="flex items-center gap-2 mt-2 text-slate-500">
                  <MapPin size={16} />
                  <input
                    type="text"
                    value={branch.address || ''}
                    onChange={(e) => {
                      const nb = [...branches];
                      nb[idx].address = e.target.value;
                      setBranches(nb);
                    }}
                    placeholder="عنوان الفرع..."
                    className="bg-transparent border-b border-slate-200 outline-none text-sm w-64"
                  />
                </div>
              </div>
              <button onClick={() => saveBranch(branch)} className="btn-primary px-6 py-2.5 font-bold flex items-center gap-2">
                <Save size={18} /> حفظ التعديلات
              </button>
            </div>

            <div className="mb-6">
              <p className="text-sm font-bold text-slate-700 mb-3">أيام العمل:</p>
              <div className="flex flex-wrap gap-2">
                {DAYS.map((day) => {
                  const isActive = (branch.workingDays || '').split(',').includes(day.key);
                  return (
                    <button
                      key={day.key}
                      onClick={() => toggleDay(idx, day.key)}
                      className={`px-4 py-2 rounded-xl font-bold transition-all ${
                        isActive ? 'bg-orange-600 text-white shadow-md' : 'bg-slate-100 text-slate-500'
                      }`}
                    >
                      {day.label}
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
              <div>
                <label className="text-xs font-bold text-slate-500 flex items-center gap-1 mb-1">
                  <Clock size={14} /> وقت الفتح
                </label>
                <input
                  type="time"
                  value={branch.openTime || '09:00'}
                  onChange={(e) => {
                    const nb = [...branches];
                    nb[idx].openTime = e.target.value;
                    setBranches(nb);
                  }}
                  className="input-fancy w-full p-2.5 text-sm"
                />
              </div>
              <div>
                <label className="text-xs font-bold text-slate-500 mb-1 block">وقت الإغلاق</label>
                <input
                  type="time"
                  value={branch.closeTime || '23:59'}
                  onChange={(e) => {
                    const nb = [...branches];
                    nb[idx].closeTime = e.target.value;
                    setBranches(nb);
                  }}
                  className="input-fancy w-full p-2.5 text-sm"
                />
              </div>
              <div>
                <label className="text-xs font-bold text-indigo-700 flex items-center gap-1 mb-1">
                  <Percent size={14} /> الضريبة (%)
                </label>
                <input
                  type="number"
                  min="0"
                  step="0.5"
                  value={branch.taxRate ?? 14}
                  onChange={(e) => {
                    const nb = [...branches];
                    nb[idx].taxRate = e.target.value;
                    setBranches(nb);
                  }}
                  className="input-fancy w-full p-2.5 text-sm"
                />
              </div>
              <div>
                <label className="text-xs font-bold text-emerald-700 flex items-center gap-1 mb-1">
                  <Percent size={14} /> رسوم الخدمة (%)
                </label>
                <input
                  type="number"
                  min="0"
                  step="0.5"
                  value={branch.serviceChargeRate ?? 0}
                  onChange={(e) => {
                    const nb = [...branches];
                    nb[idx].serviceChargeRate = e.target.value;
                    setBranches(nb);
                  }}
                  className="input-fancy w-full p-2.5 text-sm"
                />
              </div>
            </div>
          </div>
        ))}
      </div>
    </DashboardLayout>
  );
}