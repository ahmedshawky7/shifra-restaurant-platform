import { useState, useEffect, useCallback } from 'react';
import axiosClient from '../api/axiosClient';
import { useAuth } from '../context/AuthContext';
import DashboardLayout from '../components/DashboardLayout';
import {
  UserPlus, Users, ShieldCheck, ShieldOff, KeyRound, CheckCircle2, Ban, X, Copy
} from 'lucide-react';

export default function StaffManagement() {
  const { branchesData } = useAuth();
  const [staff, setStaff] = useState([]);
  const [loading, setLoading] = useState(true);
  const [newStaff, setNewStaff] = useState({
    fullName: '', username: '', password: '', phoneNumber: '', role: 'CASHIER', branchId: ''
  });
  const [isAdding, setIsAdding] = useState(false);
  const [tempCreds, setTempCreds] = useState(null);
  const [isResettingId, setIsResettingId] = useState(null);

  const loadStaff = useCallback(async () => {
    try {
      const res = await axiosClient.get('/org/staff');
      setStaff(res.data || []);
    } catch (e) {
      console.error('Failed to load staff', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStaff();
  }, [loadStaff]);

  useEffect(() => {
    if (branchesData.length > 0 && !newStaff.branchId) {
      setNewStaff((prev) => ({ ...prev, branchId: String(branchesData[0].id) }));
    }
  }, [branchesData, newStaff.branchId]);

  const handleAddStaff = async (e) => {
    e.preventDefault();
    setIsAdding(true);
    try {
      await axiosClient.post('/org/staff', {
        ...newStaff,
        branchId: newStaff.branchId ? Number(newStaff.branchId) : null,
      });
      alert('تم إضافة الموظف بنجاح');
      setNewStaff({
        fullName: '', username: '', password: '', phoneNumber: '', role: 'CASHIER',
        branchId: branchesData[0]?.id ? String(branchesData[0].id) : ''
      });
      await loadStaff();
    } catch (error) {
      alert(error.response?.data?.error || error.response?.data?.detail || 'فشل في إضافة الموظف');
    } finally {
      setIsAdding(false);
    }
  };

  const handleToggleStatus = async (member) => {
    const targetActive = !member.active;
    const msg = targetActive
      ? `هل تريد إعادة تفعيل حساب "${member.fullName}"؟`
      : `هل أنت متأكد من إيقاف حساب "${member.fullName}"؟ سيتم طرده فوراً مع حفظ سجلاته.`;
    if (!window.confirm(msg)) return;
    try {
      await axiosClient.put(`/org/staff/${member.id}/status`, { active: targetActive });
      await loadStaff();
    } catch (error) {
      alert(error.response?.data?.detail || error.response?.data?.error || 'فشل في تغيير الحالة');
    }
  };

  const handleResetPassword = async (member) => {
    if (!window.confirm(`هل تريد توليد كلمة مرور مؤقتة للموظف "${member.fullName}"؟`)) return;
    setIsResettingId(member.id);
    try {
      const res = await axiosClient.post(`/org/staff/${member.id}/reset-password`);
      setTempCreds({
        fullName: member.fullName,
        username: res.data.username || member.username,
        password: res.data.temporaryPassword,
      });
    } catch (error) {
      alert(error.response?.data?.detail || error.response?.data?.error || 'فشل في التوليد');
    } finally {
      setIsResettingId(null);
    }
  };

  const roleLabel = (role) => {
    switch (role) {
      case 'CASHIER': return 'كاشير (POS)';
      case 'KITCHEN_STAFF': return 'شيف / مطبخ (KDS)';
      case 'WAITER': return 'ويتر (صالة)';
      case 'ORG_ADMIN': return 'مدير مطعم';
      default: return role;
    }
  };

  return (
    <DashboardLayout title="إدارة فريق عمل المطعم">
      <div className="max-w-6xl mx-auto space-y-8">
        <form onSubmit={handleAddStaff} className="card p-6 space-y-4">
          <h3 className="font-black text-lg text-slate-800 flex items-center gap-2">
            <UserPlus size={20} className="text-indigo-600" /> إضافة موظف جديد (كاشير / مطبخ / ويتر)
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-6 gap-3 items-end">
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">الاسم الكامل *</label>
              <input
                className="input-fancy w-full p-2.5 text-sm font-semibold"
                required
                placeholder="مثال: محمود الشيف"
                value={newStaff.fullName}
                onChange={(e) => setNewStaff({ ...newStaff, fullName: e.target.value })}
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">اسم المستخدم *</label>
              <input
                className="input-fancy w-full p-2.5 text-sm font-semibold"
                dir="ltr"
                required
                placeholder="chef_mahmoud"
                value={newStaff.username}
                onChange={(e) => setNewStaff({ ...newStaff, username: e.target.value })}
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">كلمة المرور *</label>
              <input
                type="password"
                className="input-fancy w-full p-2.5 text-sm font-semibold"
                dir="ltr"
                required
                placeholder="••••••"
                value={newStaff.password}
                onChange={(e) => setNewStaff({ ...newStaff, password: e.target.value })}
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">الدور الوظيفي *</label>
              <select
                className="input-fancy w-full p-2.5 text-sm font-bold"
                value={newStaff.role}
                onChange={(e) => setNewStaff({ ...newStaff, role: e.target.value })}
              >
                <option value="CASHIER">كاشير (POS)</option>
                <option value="KITCHEN_STAFF">شيف / مطبخ (KDS)</option>
                <option value="WAITER">ويتر (Waiter App)</option>
                <option value="ORG_ADMIN">مدير فرع / شريك</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">الفرع</label>
              <select
                className="input-fancy w-full p-2.5 text-sm font-bold"
                value={newStaff.branchId}
                onChange={(e) => setNewStaff({ ...newStaff, branchId: e.target.value })}
              >
                <option value="">كل الفروع</option>
                {branchesData.map((b) => (
                  <option key={b.id} value={b.id}>{b.name}</option>
                ))}
              </select>
            </div>
            <button type="submit" disabled={isAdding} className="btn-primary font-bold py-2.5">
              {isAdding ? 'جاري الإضافة...' : '+ إضافة موظف'}
            </button>
          </div>
        </form>

        <div className="card overflow-hidden">
          <div className="p-6 border-b border-slate-100 flex items-center justify-between">
            <h3 className="font-black text-xl text-slate-800 flex items-center gap-2">
              <Users size={22} className="text-indigo-600" /> فريق العمل ({staff.length})
            </h3>
          </div>

          {loading ? (
            <div className="p-12 text-center font-bold text-slate-400">جاري تحميل فريق العمل...</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-right">
                <thead>
                  <tr className="border-b border-slate-100">
                    <th className="p-4 text-sm font-black">الاسم</th>
                    <th className="p-4 text-sm font-black">اسم المستخدم</th>
                    <th className="p-4 text-sm font-black">الدور</th>
                    <th className="p-4 text-sm font-black">الفرع</th>
                    <th className="p-4 text-sm font-black">الحالة</th>
                    <th className="p-4 text-sm font-black">الإجراءات</th>
                  </tr>
                </thead>
                <tbody>
                  {staff.map((m) => {
                    const branch = branchesData.find((b) => b.id === m.branchId);
                    return (
                      <tr key={m.id} className="border-b border-slate-50">
                        <td className="p-4 font-bold text-slate-800">{m.fullName}</td>
                        <td className="p-4 font-semibold text-slate-600" dir="ltr">{m.username}</td>
                        <td className="p-4">
                          <span className="px-3 py-1 rounded-full bg-indigo-50 text-indigo-700 text-xs font-bold">
                            {roleLabel(m.role)} {m.owner ? '• مالك' : ''}
                          </span>
                        </td>
                        <td className="p-4 text-sm font-semibold text-slate-600">
                          {branch ? branch.name : 'كل الفروع'}
                        </td>
                        <td className="p-4">
                          {m.active ? (
                            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 text-xs font-black">
                              <CheckCircle2 size={14} /> نشط
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-red-50 text-red-600 text-xs font-black">
                              <Ban size={14} /> موقوف
                            </span>
                          )}
                        </td>
                        <td className="p-4">
                          <div className="flex flex-wrap gap-2">
                            {!m.owner && (
                              m.active ? (
                                <button
                                  onClick={() => handleToggleStatus(m)}
                                  className="px-3 py-1.5 rounded-xl bg-red-50 text-red-600 hover:bg-red-100 font-bold text-xs flex items-center gap-1.5"
                                >
                                  <ShieldOff size={14} /> إيقاف
                                </button>
                              ) : (
                                <button
                                  onClick={() => handleToggleStatus(m)}
                                  className="px-3 py-1.5 rounded-xl bg-emerald-50 text-emerald-700 hover:bg-emerald-100 font-bold text-xs flex items-center gap-1.5"
                                >
                                  <ShieldCheck size={14} /> تفعيل
                                </button>
                              )
                            )}
                            <button
                              onClick={() => handleResetPassword(m)}
                              disabled={isResettingId === m.id}
                              className="px-3 py-1.5 rounded-xl bg-amber-50 text-amber-700 hover:bg-amber-100 font-bold text-xs flex items-center gap-1.5"
                            >
                              <KeyRound size={14} /> كلمة مرور مؤقتة
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {tempCreds && (
        <div className="modal-backdrop flex items-center justify-center z-50 p-4" onClick={() => setTempCreds(null)}>
          <div className="bg-white p-8 rounded-3xl w-96 max-w-full shadow-2xl relative animate-scale-in" onClick={(e) => e.stopPropagation()}>
            <button onClick={() => setTempCreds(null)} className="absolute top-5 left-5 text-slate-400 hover:text-red-500">
              <X size={20} />
            </button>
            <h3 className="text-xl font-black text-slate-800 mb-2 flex items-center gap-2">
              <KeyRound className="text-amber-500" /> بيانات الدخول المؤقتة
            </h3>
            <p className="text-sm text-slate-500 font-semibold mb-4">الموظف: {tempCreds.fullName}</p>
            <div className="bg-slate-50 border border-slate-200 rounded-2xl p-4 space-y-2 mb-5 text-sm">
              <p className="font-bold text-slate-700">اسم المستخدم: <span dir="ltr">{tempCreds.username}</span></p>
              <p className="font-bold text-indigo-700">كلمة المرور المؤقتة: <span dir="ltr">{tempCreds.password}</span></p>
            </div>
            <div className="flex gap-3">
              <button
                onClick={() => navigator.clipboard?.writeText(tempCreds.password)}
                className="btn-primary flex-1 py-3 font-bold flex items-center justify-center gap-2"
              >
                <Copy size={16} /> نسخ كلمة المرور
              </button>
              <button onClick={() => setTempCreds(null)} className="flex-1 bg-slate-100 text-slate-600 font-bold py-3 rounded-xl">
                إغلاق
              </button>
            </div>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
}