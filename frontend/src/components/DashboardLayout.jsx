import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  LogOut, Monitor, BarChart3, XCircle, CheckCircle, Clock, Building2, ShieldAlert,
  PanelRightClose, PanelRightOpen, UtensilsCrossed, MapPin, UserCog,
  KeyRound, Eye, EyeOff, Tv, ChefHat, ShoppingBag
} from 'lucide-react';
import axiosClient from '../api/axiosClient';

export default function DashboardLayout({ children, title }) {
  const {
    user,
    logout,
    branchesData,
    currentBranch,
    selectedBranchId,
    selectBranch,
    refreshBranches,
  } = useAuth();

  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);

  const [isBranchOpen, setIsBranchOpen] = useState(true);
  const [showReasonModal, setShowReasonModal] = useState(false);
  const [closureReason, setClosureReason] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  const [schedule, setSchedule] = useState({
    openTime: '09:00',
    closeTime: '23:59',
    taxRate: 14,
    serviceChargeRate: 0,
  });

  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });
  const [showPasswords, setShowPasswords] = useState({
    current: false,
    next: false,
    confirm: false,
  });
  const [isChangingPassword, setIsChangingPassword] = useState(false);

  useEffect(() => {
    if (currentBranch) {
      setIsBranchOpen(Boolean(currentBranch.openToday));
      setClosureReason(currentBranch.closureReason || '');
      setSchedule({
        openTime: currentBranch.openTime || '09:00',
        closeTime: currentBranch.closeTime || '23:59',
        taxRate: currentBranch.taxRate ?? 14,
        serviceChargeRate: currentBranch.serviceChargeRate ?? 0,
      });
    }
  }, [currentBranch]);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const closePasswordModal = () => {
    setShowPasswordModal(false);
    setShowPasswords({ current: false, next: false, confirm: false });
  };

  const handleToggleStatus = async (openStatus, reason = '') => {
    if (!selectedBranchId) return;
    setIsLoading(true);
    try {
      await axiosClient.put(`/branches/${selectedBranchId}/status`, { isOpen: openStatus, reason });
      setIsBranchOpen(openStatus);
      setShowReasonModal(false);
      if (openStatus) setClosureReason('');
      await refreshBranches();
    } catch (e) {
      alert(e.response?.data?.detail || e.response?.data?.error || 'خطأ في تغيير حالة الفرع');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSaveSchedule = async () => {
    if (!selectedBranchId) return;
    try {
      await axiosClient.put(`/branches/${selectedBranchId}/schedule`, schedule);
      alert('تم حفظ ساعات العمل والضرائب بنجاح');
      setShowScheduleModal(false);
      await refreshBranches();
    } catch (e) {
      alert(e.response?.data?.detail || e.response?.data?.error || 'فشل في حفظ الإعدادات');
    }
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    if (!passwordForm.currentPassword || !passwordForm.newPassword) {
      return alert('يرجى إدخال كلمة المرور الحالية والجديدة');
    }
    if (passwordForm.newPassword.length < 6) {
      return alert('كلمة المرور الجديدة يجب أن تكون 6 أحرف على الأقل');
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      return alert('تأكيد كلمة المرور غير متطابق');
    }
    setIsChangingPassword(true);
    try {
      await axiosClient.post('/auth/change-password', {
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
      });
      alert('تم تغيير كلمة المرور بنجاح');
      closePasswordModal();
      setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
    } catch (err) {
      alert(err.response?.data?.detail || err.response?.data?.error || 'خطأ أثناء تحديث كلمة المرور');
    } finally {
      setIsChangingPassword(false);
    }
  };

  const isSuperAdmin = user?.role === 'SUPER_ADMIN';
  const isOrgAdmin = user?.role === 'ORG_ADMIN' || user?.isOwner;
  const isCashier = user?.role === 'CASHIER';
  const isKitchen = user?.role === 'KITCHEN_STAFF';

  const navItems = [];
  if (isSuperAdmin) {
    navItems.push({ path: '/super-admin', label: 'إدارة المنصة (SaaS)', icon: ShieldAlert });
  } else {
    if (isOrgAdmin) {
      navItems.push({ path: '/organization', label: 'المطعم والمنيو والطاولات', icon: Building2 });
      navItems.push({ path: '/staff', label: 'فريق العمل', icon: UserCog });
      navItems.push({ path: '/branches', label: 'إدارة الفروع', icon: MapPin });
    }
    if (isCashier || isOrgAdmin) {
      navItems.push({ path: '/pos', label: 'الكاشير والطاولات (POS)', icon: ShoppingBag });
    }
    if (isKitchen || isOrgAdmin) {
      navItems.push({ path: '/kds', label: 'شاشة المطبخ (KDS)', icon: ChefHat });
    }
    if (isCashier || isOrgAdmin) {
      navItems.push({ path: '/analytics', label: 'المبيعات والإحصائيات', icon: BarChart3 });
    }
  }

  return (
    <div className="flex h-screen overflow-hidden" dir="rtl">
      <aside
        className={`${
          collapsed ? 'w-20' : 'w-64'
        } bg-gradient-to-b from-slate-900 via-slate-900 to-indigo-950 flex flex-col transition-all duration-300 relative shrink-0`}
      >
        <div className="h-20 flex items-center justify-center border-b border-white/10 relative">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-gradient-to-br from-orange-500 to-rose-600 flex items-center justify-center text-white shadow-lg">
              <UtensilsCrossed size={22} className="animate-float" />
            </div>
            {!collapsed && (
              <div className="animate-fade-in">
                <h1 className="text-xl font-black text-white leading-tight">سُفرة</h1>
                <p className="text-[10px] text-slate-400 font-bold tracking-wide">QR MENU & POS</p>
              </div>
            )}
          </div>
        </div>

        <button
          onClick={() => setCollapsed(!collapsed)}
          className="absolute -left-3 top-24 w-7 h-7 bg-white rounded-full shadow-lg border border-slate-200 flex items-center justify-center text-slate-500 hover:scale-110 transition-all z-20"
        >
          {collapsed ? <PanelRightOpen size={15} /> : <PanelRightClose size={15} />}
        </button>

        <nav className="flex-1 p-4 space-y-2 relative overflow-y-auto">
          {navItems.map((item) => {
            const active = location.pathname === item.path;
            return (
              <button
                key={item.path}
                onClick={() => navigate(item.path)}
                title={collapsed ? item.label : undefined}
                className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-300 relative ${
                  active
                    ? 'bg-white/10 text-white font-bold shadow-inner'
                    : 'text-slate-400 hover:text-white hover:bg-white/5'
                } ${collapsed ? 'justify-center px-0' : ''}`}
              >
                {active && <span className="absolute right-0 top-1/2 -translate-y-1/2 w-1 h-7 bg-orange-400 rounded-full"></span>}
                <item.icon size={20} />
                {!collapsed && <span>{item.label}</span>}
              </button>
            );
          })}

          {!isSuperAdmin && selectedBranchId && (
            <div className="pt-2 space-y-1.5 border-t border-white/10">
              <button
                onClick={() => window.open(`/pickup?branchId=${selectedBranchId}`, '_blank')}
                title={collapsed ? 'شاشة استلام الطلبات (TV)' : undefined}
                className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl text-cyan-300 hover:text-white hover:bg-white/5 transition-all ${
                  collapsed ? 'justify-center px-0' : ''
                }`}
              >
                <Tv size={20} />
                {!collapsed && <span>شاشة الاستلام (TV)</span>}
              </button>
              <button
                onClick={() => window.open(`/menu/${selectedBranchId}`, '_blank')}
                title={collapsed ? 'معاينة المنيو العام' : undefined}
                className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl text-slate-400 hover:text-white hover:bg-white/5 transition-all ${
                  collapsed ? 'justify-center px-0' : ''
                }`}
              >
                <Monitor size={20} />
                {!collapsed && <span>معاينة المنيو العام</span>}
              </button>
            </div>
          )}
        </nav>

        <div className="p-4 border-t border-white/10 space-y-2">
          <button
            onClick={() => setShowPasswordModal(true)}
            title={collapsed ? 'تغيير كلمة المرور' : undefined}
            className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl bg-white/5 text-slate-300 hover:bg-white/10 hover:text-white font-bold transition-all ${
              collapsed ? 'justify-center px-0' : ''
            }`}
          >
            <KeyRound size={18} />
            {!collapsed && <span>تغيير كلمة المرور</span>}
          </button>

          <button
            onClick={handleLogout}
            title={collapsed ? 'تسجيل الخروج' : undefined}
            className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl bg-red-500/10 text-red-400 hover:bg-red-500/20 font-bold transition-all ${
              collapsed ? 'justify-center px-0' : ''
            }`}
          >
            <LogOut size={20} />
            {!collapsed && <span>تسجيل الخروج</span>}
          </button>
        </div>
      </aside>

      <main className="flex-1 flex flex-col overflow-hidden">
        <header className="min-h-20 py-3 bg-white/80 backdrop-blur-xl border-b border-slate-200/80 flex flex-wrap items-center px-6 justify-between gap-4 shrink-0 z-30">
          <h2 className="text-2xl font-black text-slate-800">{title}</h2>

          {!isSuperAdmin ? (
            <div className="flex flex-wrap items-center gap-3">
              {branchesData.length > 0 && (
                <select
                  value={selectedBranchId || ''}
                  onChange={(e) => selectBranch(e.target.value)}
                  className="input-fancy px-3 py-2 text-sm font-bold"
                >
                  {branchesData.map((b) => (
                    <option key={b.id} value={b.id}>
                      فرع: {b.name}
                    </option>
                  ))}
                </select>
              )}

              {selectedBranchId && (isOrgAdmin || isCashier) && (
                <>
                  {isBranchOpen ? (
                    <button
                      onClick={() => setShowReasonModal(true)}
                      className="bg-red-50 text-red-600 px-3.5 py-2 rounded-xl font-bold text-sm hover:bg-red-100 flex items-center gap-1.5"
                    >
                      <XCircle size={16} /> إغلاق الفرع
                    </button>
                  ) : (
                    <button
                      onClick={() => handleToggleStatus(true)}
                      disabled={isLoading}
                      className="bg-emerald-50 text-emerald-700 px-3.5 py-2 rounded-xl font-bold text-sm hover:bg-emerald-100 flex items-center gap-1.5"
                    >
                      <CheckCircle size={16} /> فتح الفرع
                    </button>
                  )}

                  <button
                    onClick={() => setShowScheduleModal(true)}
                    className="bg-indigo-50 text-indigo-600 px-3.5 py-2 rounded-xl font-bold text-sm hover:bg-indigo-100 flex items-center gap-1.5"
                  >
                    <Clock size={16} /> ساعات العمل والضريبة
                  </button>
                </>
              )}

              <div className="text-xs text-slate-600 font-bold bg-slate-100 px-3.5 py-2 rounded-full">
                {user?.username}
              </div>
            </div>
          ) : (
            <div className="text-xs text-slate-600 font-bold bg-slate-100 px-3.5 py-2 rounded-full">
              {user?.username}
            </div>
          )}
        </header>

        <div className="flex-1 overflow-y-auto p-6 md:p-8">
          <div className="animate-fade-up" key={location.pathname}>
            {children}
          </div>
        </div>
      </main>

      {showReasonModal && (
        <div className="modal-backdrop flex items-center justify-center z-50 p-4">
          <div className="bg-white p-8 rounded-3xl w-96 shadow-2xl animate-scale-in">
            <h3 className="text-2xl font-bold mb-3 text-red-600 flex items-center gap-2">
              <XCircle /> إغلاق الفرع مؤقتاً
            </h3>
            <p className="text-slate-500 mb-5 text-sm">
              سيتم إيقاف استقبال طلبات الـ QR للفرع وإظهار رسالة الإغلاق للزبائن.
            </p>
            <input
              type="text"
              value={closureReason}
              onChange={(e) => setClosureReason(e.target.value)}
              placeholder="سبب الإغلاق (صيانة، ضغط مطبخ، إجازة...)"
              className="input-fancy w-full p-3 mb-6"
            />
            <div className="flex gap-3">
              <button
                onClick={() => handleToggleStatus(false, closureReason)}
                disabled={isLoading || !closureReason.trim()}
                className="flex-1 bg-red-600 text-white font-bold py-3 rounded-xl hover:bg-red-700 disabled:opacity-50"
              >
                تأكيد الإغلاق
              </button>
              <button
                onClick={() => setShowReasonModal(false)}
                className="flex-1 bg-slate-100 text-slate-600 font-bold py-3 rounded-xl"
              >
                إلغاء
              </button>
            </div>
          </div>
        </div>
      )}

      {showScheduleModal && (
        <div className="modal-backdrop flex items-center justify-center z-50 p-4">
          <div className="bg-white p-8 rounded-3xl w-[460px] max-w-full shadow-2xl animate-scale-in">
            <h3 className="text-2xl font-bold mb-6 text-indigo-600 flex items-center gap-2">
              <Clock /> إعدادات العمل والضرائب
            </h3>
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-bold text-slate-700 mb-1.5">وقت الفتح</label>
                  <input
                    type="time"
                    value={schedule.openTime}
                    onChange={(e) => setSchedule({ ...schedule, openTime: e.target.value })}
                    className="input-fancy w-full p-3"
                  />
                </div>
                <div>
                  <label className="block text-sm font-bold text-slate-700 mb-1.5">وقت الإغلاق</label>
                  <input
                    type="time"
                    value={schedule.closeTime}
                    onChange={(e) => setSchedule({ ...schedule, closeTime: e.target.value })}
                    className="input-fancy w-full p-3"
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-bold text-slate-700 mb-1.5">الضريبة (%)</label>
                  <input
                    type="number"
                    min="0"
                    step="0.5"
                    value={schedule.taxRate}
                    onChange={(e) => setSchedule({ ...schedule, taxRate: e.target.value })}
                    className="input-fancy w-full p-3"
                  />
                </div>
                <div>
                  <label className="block text-sm font-bold text-slate-700 mb-1.5">رسوم الخدمة (%)</label>
                  <input
                    type="number"
                    min="0"
                    step="0.5"
                    value={schedule.serviceChargeRate}
                    onChange={(e) => setSchedule({ ...schedule, serviceChargeRate: e.target.value })}
                    className="input-fancy w-full p-3"
                  />
                </div>
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={handleSaveSchedule} className="btn-primary flex-1 font-bold py-3">
                حفظ الإعدادات
              </button>
              <button
                onClick={() => setShowScheduleModal(false)}
                className="flex-1 bg-slate-100 text-slate-600 font-bold py-3 rounded-xl"
              >
                إلغاء
              </button>
            </div>
          </div>
        </div>
      )}

      {showPasswordModal && (
        <div className="modal-backdrop flex items-center justify-center z-50 p-4" onClick={closePasswordModal}>
          <div className="bg-white p-8 rounded-3xl w-[420px] max-w-full shadow-2xl animate-scale-in" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-2xl font-black mb-5 text-slate-800 flex items-center gap-2">
              <KeyRound className="text-indigo-600" /> تغيير كلمة المرور
            </h3>
            <form onSubmit={handleChangePassword} className="space-y-4">
              <div className="relative">
                <input
                  type={showPasswords.current ? 'text' : 'password'}
                  required
                  dir="ltr"
                  placeholder="كلمة المرور الحالية"
                  value={passwordForm.currentPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, currentPassword: e.target.value })}
                  className="input-fancy w-full p-3 pl-11 font-semibold"
                />
                <button
                  type="button"
                  onClick={() => setShowPasswords({ ...showPasswords, current: !showPasswords.current })}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-indigo-600"
                >
                  {showPasswords.current ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              <div className="relative">
                <input
                  type={showPasswords.next ? 'text' : 'password'}
                  required
                  dir="ltr"
                  placeholder="كلمة المرور الجديدة (6 أحرف فأكثر)"
                  value={passwordForm.newPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, newPassword: e.target.value })}
                  className="input-fancy w-full p-3 pl-11 font-semibold"
                />
                <button
                  type="button"
                  onClick={() => setShowPasswords({ ...showPasswords, next: !showPasswords.next })}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-indigo-600"
                >
                  {showPasswords.next ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              <div className="relative">
                <input
                  type={showPasswords.confirm ? 'text' : 'password'}
                  required
                  dir="ltr"
                  placeholder="تأكيد كلمة المرور الجديدة"
                  value={passwordForm.confirmPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })}
                  className="input-fancy w-full p-3 pl-11 font-semibold"
                />
                <button
                  type="button"
                  onClick={() => setShowPasswords({ ...showPasswords, confirm: !showPasswords.confirm })}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-indigo-600"
                >
                  {showPasswords.confirm ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              <div className="flex gap-3 pt-2">
                <button type="submit" disabled={isChangingPassword} className="btn-primary flex-1 font-bold py-3">
                  {isChangingPassword ? 'جاري الحفظ...' : 'تحديث كلمة المرور'}
                </button>
                <button
                  type="button"
                  onClick={closePasswordModal}
                  className="flex-1 bg-slate-100 text-slate-600 font-bold py-3 rounded-xl"
                >
                  إلغاء
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}