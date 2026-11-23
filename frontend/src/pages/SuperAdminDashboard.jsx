import { useState, useEffect } from 'react';
import axiosClient from '../api/axiosClient';
import DashboardLayout from '../components/DashboardLayout';
import { ShieldAlert, CheckCircle2, Ban, KeyRound, Power, X, Copy } from 'lucide-react';

export default function SuperAdminDashboard() {
  const [orgs, setOrgs] = useState([]);
  const [resetModal, setResetModal] = useState(null);

  const load = async () => {
    const res = await axiosClient.get('/super-admin/organizations');
    setOrgs(res.data || []);
  };

  useEffect(() => {
    load();
  }, []);

  const updateStatus = async (id, status) => {
    await axiosClient.put(`/super-admin/organizations/${id}/subscription`, { status });
    load();
  };

  const toggleActive = async (org) => {
    const nextActive = !org.active;
    const msg = nextActive
      ? `هل تريد تفعيل مطعم "${org.name}" والسماح لموظفيه بالدخول؟`
      : `تحذير: إيقاف مطعم "${org.name}" سيمنع جميع موظفيه من الدخول ويعطّل المنيو الإلكتروني. متابعة؟`;
    if (!window.confirm(msg)) return;
    await axiosClient.put(`/super-admin/organizations/${org.id}/active`, { active: nextActive });
    load();
  };

  const resetAdminPassword = async (org) => {
    if (!window.confirm(`هل تريد توليد كلمة مرور مؤقتة جديدة لمالك "${org.name}"؟`)) return;
    try {
      const res = await axiosClient.post(`/super-admin/organizations/${org.id}/reset-admin-password`);
      setResetModal({
        orgName: org.name,
        username: res.data.username,
        password: res.data.temporaryPassword,
      });
    } catch (err) {
      alert(err.response?.data?.detail || err.response?.data?.error || 'فشل في إعادة تعيين كلمة المرور');
    }
  };

  return (
    <DashboardLayout title="لوحة إدارة منصة المطاعم (SaaS)">
      <div className="max-w-6xl mx-auto card overflow-hidden">
        <div className="p-6 border-b border-slate-100 flex items-center gap-2 font-black text-xl">
          <ShieldAlert className="text-indigo-600" /> المطاعم المشتركة ({orgs.length})
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-right">
            <thead>
              <tr>
                <th className="p-4 text-sm font-black">المطعم</th>
                <th className="p-4 text-sm font-black">النوع</th>
                <th className="p-4 text-sm font-black">الباقة</th>
                <th className="p-4 text-sm font-black">الاشتراك</th>
                <th className="p-4 text-sm font-black">الحالة</th>
                <th className="p-4 text-sm font-black">التحكم</th>
              </tr>
            </thead>
            <tbody>
              {orgs.map((o) => (
                <tr key={o.id} className="border-t border-slate-100">
                  <td className="p-4 font-bold">{o.name}</td>
                  <td className="p-4 text-sm">{o.type === 'SINGLE_BRANCH' ? 'فرع واحد' : 'سلسلة مطاعم'}</td>
                  <td className="p-4 font-bold text-indigo-700">{o.plan}</td>
                  <td className="p-4 font-bold">{o.subscriptionStatus}</td>
                  <td className="p-4">
                    {o.active !== false ? (
                      <span className="px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 text-xs font-black">نشط</span>
                    ) : (
                      <span className="px-3 py-1 rounded-full bg-red-50 text-red-600 text-xs font-black">متوقف</span>
                    )}
                  </td>
                  <td className="p-4 flex flex-wrap gap-2">
                    <button
                      onClick={() => updateStatus(o.id, 'ACTIVE')}
                      className="px-3 py-1.5 rounded-lg bg-emerald-50 text-emerald-700 font-bold text-xs flex items-center gap-1"
                    >
                      <CheckCircle2 size={14} /> تفعيل الاشتراك
                    </button>
                    <button
                      onClick={() => updateStatus(o.id, 'SUSPENDED')}
                      className="px-3 py-1.5 rounded-lg bg-orange-50 text-orange-700 font-bold text-xs flex items-center gap-1"
                    >
                      <Ban size={14} /> تعليق الاشتراك
                    </button>
                    <button
                      onClick={() => toggleActive(o)}
                      className={`px-3 py-1.5 rounded-lg font-bold text-xs flex items-center gap-1 ${
                        o.active !== false ? 'bg-red-50 text-red-600' : 'bg-indigo-50 text-indigo-700'
                      }`}
                    >
                      <Power size={14} /> {o.active !== false ? 'إيقاف المطعم' : 'تشغيل المطعم'}
                    </button>
                    <button
                      onClick={() => resetAdminPassword(o)}
                      className="px-3 py-1.5 rounded-lg bg-amber-50 text-amber-700 font-bold text-xs flex items-center gap-1"
                    >
                      <KeyRound size={14} /> كلمة مرور المالك
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {resetModal && (
        <div className="modal-backdrop flex items-center justify-center z-50 p-4" onClick={() => setResetModal(null)}>
          <div className="bg-white p-8 rounded-3xl w-96 max-w-full shadow-2xl relative animate-scale-in" onClick={(e) => e.stopPropagation()}>
            <button onClick={() => setResetModal(null)} className="absolute top-5 left-5 text-slate-400 hover:text-red-500">
              <X size={20} />
            </button>
            <h3 className="text-lg font-black text-slate-800 mb-2">كلمة مرور مالك المطعم</h3>
            <p className="text-sm text-slate-500 font-bold mb-4">{resetModal.orgName}</p>
            <div className="bg-slate-50 border border-slate-200 rounded-2xl p-4 space-y-2 mb-5 text-sm">
              <p className="font-bold text-slate-700">اسم المستخدم: <span dir="ltr">{resetModal.username}</span></p>
              <p className="font-bold text-indigo-700">كلمة المرور المؤقتة: <span dir="ltr">{resetModal.password}</span></p>
            </div>
            <div className="flex gap-3">
              <button
                onClick={() => navigator.clipboard?.writeText(resetModal.password)}
                className="btn-primary flex-1 py-3 font-bold flex items-center justify-center gap-2"
              >
                <Copy size={16} /> نسخ كلمة المرور
              </button>
              <button onClick={() => setResetModal(null)} className="flex-1 bg-slate-100 text-slate-600 font-bold py-3 rounded-xl">
                إغلاق
              </button>
            </div>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
}