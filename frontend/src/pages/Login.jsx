import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import axiosClient from '../api/axiosClient';
import {
  UtensilsCrossed, LogIn, UserPlus, ShieldCheck, Sparkles, Building2, Store
} from 'lucide-react';

export default function Login() {
  const [isLogin, setIsLogin] = useState(true);
  const [form, setForm] = useState({
    username: '',
    password: '',
    fullName: '',
    phoneNumber: '',
    organizationName: '',
    organizationType: 'SINGLE_BRANCH',
    firstBranchName: 'الفرع الرئيسي',
    city: '',
    address: '',
    plan: 'TRIAL',
  });
  const { login } = useAuth();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      if (isLogin) {
        const loggedUser = await login(form.username, form.password);
        if (loggedUser.role === 'SUPER_ADMIN') navigate('/super-admin');
        else if (loggedUser.role === 'ORG_ADMIN') navigate('/organization');
        else if (loggedUser.role === 'CASHIER') navigate('/pos');
        else if (loggedUser.role === 'KITCHEN_STAFF') navigate('/kds');
        else alert('حسابات الويتر تستخدم تطبيق الموبايل.');
      } else {
        await axiosClient.post('/auth/register-workspace', form);
        alert('تم إنشاء مساحة عمل المطعم بنجاح مع 14 يوم تجربة مجانية! سجل دخولك الآن.');
        setIsLogin(true);
      }
    } catch (error) {
      alert(error.response?.data?.error || error.response?.data?.detail || 'حدث خطأ. تأكد من البيانات.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex" dir="rtl">
      <div className="w-full lg:w-[48%] flex items-center justify-center p-6 relative">
        <div className="w-full max-w-lg relative animate-fade-up">
          <div className="flex items-center gap-3 mb-8">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-orange-500 to-rose-600 flex items-center justify-center text-white shadow-lg animate-float">
              <UtensilsCrossed size={28} />
            </div>
            <div>
              <h1 className="text-3xl font-black text-gradient">سُفرة SaaS</h1>
              <p className="text-sm text-slate-500 font-semibold">المنيو الذكي والكاشير وشاشة المطبخ</p>
            </div>
          </div>

          <div className="card p-8 animate-scale-in">
            <div className="flex bg-slate-100 rounded-xl p-1 mb-6">
              <button
                type="button"
                onClick={() => setIsLogin(true)}
                className={`flex-1 py-2.5 rounded-lg font-bold transition-all ${
                  isLogin ? 'bg-white text-blue-600 shadow-sm' : 'text-slate-400'
                }`}
              >
                تسجيل الدخول
              </button>
              <button
                type="button"
                onClick={() => setIsLogin(false)}
                className={`flex-1 py-2.5 rounded-lg font-bold transition-all ${
                  !isLogin ? 'bg-white text-blue-600 shadow-sm' : 'text-slate-400'
                }`}
              >
                اشتراك مطعم جديد
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              {!isLogin && (
                <div className="space-y-4 animate-fade-up">
                  <div className="grid grid-cols-2 gap-3">
                    <button
                      type="button"
                      onClick={() => setForm({ ...form, organizationType: 'SINGLE_BRANCH' })}
                      className={`p-3 rounded-xl border text-sm font-bold flex flex-col items-center gap-1.5 transition-all ${
                        form.organizationType === 'SINGLE_BRANCH'
                          ? 'border-orange-500 bg-orange-50 text-orange-800 shadow-sm'
                          : 'border-slate-200 text-slate-500'
                      }`}
                    >
                      <Store size={20} />
                      مطعم / كافيه (فرع واحد)
                    </button>
                    <button
                      type="button"
                      onClick={() => setForm({ ...form, organizationType: 'RESTAURANT_CHAIN' })}
                      className={`p-3 rounded-xl border text-sm font-bold flex flex-col items-center gap-1.5 transition-all ${
                        form.organizationType === 'RESTAURANT_CHAIN'
                          ? 'border-orange-500 bg-orange-50 text-orange-800 shadow-sm'
                          : 'border-slate-200 text-slate-500'
                      }`}
                    >
                      <Building2 size={20} />
                      سلسلة مطاعم (متعدد الفروع)
                    </button>
                  </div>

                  <input
                    type="text"
                    required
                    value={form.organizationName}
                    onChange={(e) => setForm({ ...form, organizationName: e.target.value })}
                    className="input-fancy w-full p-3 font-semibold"
                    placeholder="اسم المطعم أو الكافيه"
                  />

                  <div className="grid grid-cols-2 gap-3">
                    <input
                      type="text"
                      required
                      value={form.fullName}
                      onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                      className="input-fancy w-full p-3 font-semibold"
                      placeholder="اسم المالك / المدير"
                    />
                    <input
                      type="text"
                      required
                      value={form.phoneNumber}
                      onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })}
                      className="input-fancy w-full p-3 font-semibold"
                      placeholder="رقم الهاتف"
                    />
                  </div>

                  <div className="grid grid-cols-3 gap-3">
                    <input
                      type="text"
                      required
                      value={form.firstBranchName}
                      onChange={(e) => setForm({ ...form, firstBranchName: e.target.value })}
                      className="input-fancy w-full p-3 font-semibold"
                      placeholder="اسم الفرع الأول"
                    />
                    <input
                      type="text"
                      value={form.city}
                      onChange={(e) => setForm({ ...form, city: e.target.value })}
                      className="input-fancy w-full p-3 font-semibold"
                      placeholder="المدينة"
                    />
                    <input
                      type="text"
                      value={form.address}
                      onChange={(e) => setForm({ ...form, address: e.target.value })}
                      className="input-fancy w-full p-3 font-semibold"
                      placeholder="العنوان"
                    />
                  </div>

                  <select
                    value={form.plan}
                    onChange={(e) => setForm({ ...form, plan: e.target.value })}
                    className="input-fancy w-full p-3 font-bold text-sm"
                  >
                    <option value="TRIAL">باقة تجريبية مجانية (14 يوم)</option>
                    <option value="BASIC">الباقة الأساسية (حتى فرعين و15 موظف)</option>
                    <option value="PRO">الباقة الاحترافية (حتى 5 فروع و50 موظف)</option>
                    <option value="ENTERPRISE">باقة السلاسل الكبرى (حتى 50 فرع)</option>
                  </select>
                </div>
              )}

              <div>
                <label className="block text-slate-600 font-bold mb-1.5 text-sm">اسم المستخدم</label>
                <input
                  type="text"
                  required
                  value={form.username}
                  onChange={(e) => setForm({ ...form, username: e.target.value })}
                  className="input-fancy w-full p-3.5 font-semibold"
                  placeholder="username"
                  dir="ltr"
                />
              </div>

              <div>
                <label className="block text-slate-600 font-bold mb-1.5 text-sm">كلمة المرور</label>
                <input
                  type="password"
                  required
                  value={form.password}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                  className="input-fancy w-full p-3.5 font-semibold"
                  placeholder="••••••••"
                  dir="ltr"
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                className="btn-primary w-full font-bold py-4 mt-2 flex items-center justify-center gap-2"
              >
                {loading ? 'جاري التحميل...' : isLogin ? <><LogIn size={20} /> دخول</> : <><UserPlus size={20} /> إنشاء مساحة المطعم</>}
              </button>
            </form>
          </div>

          <p className="text-center text-xs text-slate-400 mt-5 font-semibold">
            <ShieldCheck size={14} className="inline ml-1" />
            بيانات كل مطعم وفروعه معزولة ومؤمّنة بالكامل
          </p>
        </div>
      </div>

      <div className="hidden lg:flex w-[52%] relative overflow-hidden bg-gradient-to-br from-slate-900 via-indigo-900 to-orange-900 items-center justify-center p-16">
        <div className="relative z-10 text-white max-w-lg animate-fade-up">
          <div className="inline-flex items-center gap-2 bg-white/10 backdrop-blur-md border border-white/20 px-4 py-2 rounded-full text-sm font-bold mb-8">
            <Sparkles size={16} className="text-orange-300" />
            منصة SaaS متكاملة للمطاعم والكافيهات
          </div>
          <h2 className="text-5xl font-black leading-[1.3] mb-6">
            منيو QR ذكي<br />
            <span className="text-orange-300">وكاشير ومطبخ لحظي</span>
          </h2>
          <p className="text-slate-200 text-lg leading-relaxed mb-10 font-semibold">
            يمسح الزبون الـ QR من الطاولة، يصل الطلب فوراً لشاشة المطبخ KDS والكاشير POS، ويتابع الويتر والزبون حالة التحضير لحظة بلحظة.
          </p>
        </div>
      </div>
    </div>
  );
}