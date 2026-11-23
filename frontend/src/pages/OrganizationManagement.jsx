import { useState, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import QRCode from 'qrcode';
import axiosClient from '../api/axiosClient';
import { useAuth } from '../context/AuthContext';
import DashboardLayout from '../components/DashboardLayout';
import {
  Building2, CreditCard, UtensilsCrossed, QrCode, Plus, Trash2, RefreshCw,
  Copy, ExternalLink, Printer, Pencil, Power, X
} from 'lucide-react';

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function LocalQrImage({ value, size = 160, alt }) {
  const [src, setSrc] = useState('');

  useEffect(() => {
    let active = true;
    QRCode.toDataURL(value, { width: size, margin: 1 })
      .then((url) => {
        if (active) setSrc(url);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [value, size]);

  if (!src) {
    return <div style={{ width: size, height: size }} className="bg-slate-100 rounded-xl animate-pulse mx-auto" />;
  }
  return <img src={src} alt={alt} style={{ width: size, height: size }} className="mx-auto" />;
}

export default function OrganizationManagement() {
  const { selectedBranchId, currentBranch, refreshBranches } = useAuth();
  const [tab, setTab] = useState('menu');
  const [overview, setOverview] = useState(null);
  const [menu, setMenu] = useState({ categories: [], items: [] });
  const [tables, setTables] = useState([]);

  const [newBranch, setNewBranch] = useState({ name: '', city: '', address: '', phone: '' });
  const [newCategory, setNewCategory] = useState({ name: '', description: '', sortOrder: 1 });
  const [newItem, setNewItem] = useState({
    categoryId: '', name: '', description: '', basePrice: '', prepTimeMinutes: 15,
    variants: [], addons: []
  });
  const [newTable, setNewTable] = useState({ tableNumber: '', capacity: 4 });
  const [editingTable, setEditingTable] = useState(null);

  const loadAll = useCallback(async () => {
    try {
      const [ovRes, menuRes] = await Promise.all([
        axiosClient.get('/org/overview'),
        axiosClient.get('/org/menu'),
      ]);
      setOverview(ovRes.data);
      setMenu(menuRes.data);
      if (menuRes.data.categories?.length > 0) {
        setNewItem((prev) => prev.categoryId ? prev : { ...prev, categoryId: String(menuRes.data.categories[0].id) });
      }
      if (selectedBranchId) {
        const tblRes = await axiosClient.get(`/org/tables?branchId=${selectedBranchId}`);
        setTables(tblRes.data || []);
      }
    } catch (e) {
      console.error(e);
    }
  }, [selectedBranchId]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const runAction = async (fn, msg) => {
    try {
      await fn();
      if (msg) alert(msg);
      await loadAll();
      await refreshBranches();
    } catch (e) {
      alert(e.response?.data?.detail || e.response?.data?.error || 'حدث خطأ');
    }
  };

  const handlePrintQrCard = async (table) => {
    const win = window.open('', '_blank', 'width=480,height=680');
    if (!win) {
      return alert('يرجى السماح للنوافذ المنبثقة (Pop-ups) لفتح بطاقة الطباعة');
    }

    const qrUrl = `${window.location.origin}/m/${table.qrToken}`;
    const qrDataUrl = await QRCode.toDataURL(qrUrl, { width: 280, margin: 1 });
    const safeOrgName = escapeHtml(overview?.organization?.name || 'المطعم');
    const safeBranchName = escapeHtml(currentBranch?.name || '');
    const safeTableNumber = escapeHtml(table.tableNumber);

    win.document.write(`
      <!doctype html>
      <html lang="ar" dir="rtl">
      <head>
        <meta charset="UTF-8" />
        <title>بطاقة طاولة ${safeTableNumber}</title>
        <style>
          * { font-family: 'Cairo', Tahoma, Arial, sans-serif; box-sizing: border-box; }
          body { margin: 0; padding: 24px; background: #f8fafc; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
          .card {
            width: 360px; background: #fff; border: 3px solid #0f172a; border-radius: 28px;
            padding: 28px 24px; text-align: center; box-shadow: 0 20px 40px rgba(0,0,0,0.08);
          }
          .brand { font-size: 24px; font-weight: 900; color: #0f172a; margin: 0; }
          .branch { font-size: 13px; font-weight: 700; color: #64748b; margin: 4px 0 16px; }
          .table-badge {
            display: inline-block; background: #ea580c; color: #fff; font-size: 22px; font-weight: 900;
            padding: 6px 26px; border-radius: 999px; margin-bottom: 18px;
          }
          .qr-box {
            border: 2px dashed #cbd5e1; border-radius: 20px; padding: 16px; display: inline-block; background: #fff;
          }
          .qr-box img { width: 220px; height: 220px; display: block; }
          .cta { font-size: 16px; font-weight: 900; color: #0f172a; margin: 18px 0 6px; }
          .sub { font-size: 12px; font-weight: 600; color: #475569; margin: 0 0 18px; }
          .print-btn {
            background: #0f172a; color: #fff; border: none; padding: 12px 28px; border-radius: 12px;
            font-size: 15px; font-weight: 800; cursor: pointer; width: 100%;
          }
          @media print {
            body { background: #fff; padding: 0; }
            .card { box-shadow: none; }
            .print-btn { display: none; }
          }
        </style>
      </head>
      <body>
        <div class="card">
          <h1 class="brand">${safeOrgName}</h1>
          <p class="branch">${safeBranchName}</p>
          <div class="table-badge">طاولة ${safeTableNumber}</div>
          <div class="qr-box">
            <img src="${qrDataUrl}" alt="QR Code" />
          </div>
          <p class="cta">امسح الكود بكاميرا هاتفك</p>
          <p class="sub">لتصفح المنيو وإرسال طلبك مباشرة للمطبخ بدون انتظار</p>
          <button class="print-btn" onclick="window.print()">طباعة البطاقة الآن</button>
        </div>
      </body>
      </html>
    `);
    win.document.close();
  };

  if (!overview) {
    return (
      <DashboardLayout title="إدارة المطعم">
        <div className="p-10 text-center font-bold text-slate-500">جاري التحميل...</div>
      </DashboardLayout>
    );
  }

  const { organization: org, branches } = overview;

  return (
    <DashboardLayout title={`إدارة: ${org.name}`}>
      <div className="max-w-6xl mx-auto space-y-6">
        <div className="card p-6 flex flex-wrap items-center justify-between gap-6">
          <div>
            <span className="inline-block px-3 py-1 rounded-full bg-orange-50 text-orange-700 text-xs font-black mb-2">
              {org.type === 'SINGLE_BRANCH' ? 'مطعم فرع واحد' : 'سلسلة مطاعم متعددة الفروع'}
            </span>
            <h3 className="text-2xl font-black text-slate-800">
              الباقة: {org.plan} — الحالة: {org.subscriptionStatus}
            </h3>
            <p className="text-sm text-slate-600 mt-2 font-bold">
              الفروع: {overview.branchesCount} | الموظفين: {overview.staffCount} | الأصناف: {overview.itemsCount} | طلبات الشهر: {overview.monthlyOrders}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {['BASIC', 'PRO', 'ENTERPRISE'].map((plan) => (
              <button
                key={plan}
                onClick={() => runAction(() => axiosClient.put('/org/subscription', { plan }), `تم التفعيل على باقة ${plan}`)}
                className={`px-4 py-2.5 rounded-xl font-bold text-sm flex items-center gap-1.5 ${
                  org.plan === plan ? 'bg-emerald-600 text-white shadow-md' : 'bg-indigo-50 text-indigo-700 hover:bg-indigo-100'
                }`}
              >
                <CreditCard size={16} /> باقة {plan}
              </button>
            ))}
          </div>
        </div>

        <div className="flex flex-wrap gap-3">
          <button
            onClick={() => setTab('menu')}
            className={`px-5 py-3 rounded-2xl font-black text-sm flex items-center gap-2 transition-all ${
              tab === 'menu' ? 'bg-slate-900 text-white shadow-lg' : 'bg-white text-slate-600 border border-slate-200'
            }`}
          >
            <UtensilsCrossed size={18} /> إدارة المنيو والأصناف
          </button>
          <button
            onClick={() => setTab('tables')}
            className={`px-5 py-3 rounded-2xl font-black text-sm flex items-center gap-2 transition-all ${
              tab === 'tables' ? 'bg-slate-900 text-white shadow-lg' : 'bg-white text-slate-600 border border-slate-200'
            }`}
          >
            <QrCode size={18} /> الطاولات ورموز QR Code
          </button>
          <button
            onClick={() => setTab('branches')}
            className={`px-5 py-3 rounded-2xl font-black text-sm flex items-center gap-2 transition-all ${
              tab === 'branches' ? 'bg-slate-900 text-white shadow-lg' : 'bg-white text-slate-600 border border-slate-200'
            }`}
          >
            <Building2 size={18} /> الفروع ({branches.length})
          </button>
        </div>

        {tab === 'menu' && (
          <div className="space-y-6">
            <div className="card p-6 space-y-4">
              <h4 className="font-black text-lg">إضافة قسم للمنيو</h4>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-3 items-end">
                <input
                  className="input-fancy p-2.5 text-sm font-semibold"
                  placeholder="اسم القسم (مشويات، بيتزا...)"
                  value={newCategory.name}
                  onChange={(e) => setNewCategory({ ...newCategory, name: e.target.value })}
                />
                <input
                  className="input-fancy p-2.5 text-sm font-semibold"
                  placeholder="وصف مختصر"
                  value={newCategory.description}
                  onChange={(e) => setNewCategory({ ...newCategory, description: e.target.value })}
                />
                <input
                  type="number"
                  className="input-fancy p-2.5 text-sm font-semibold"
                  placeholder="الترتيب"
                  value={newCategory.sortOrder}
                  onChange={(e) => setNewCategory({ ...newCategory, sortOrder: Number(e.target.value) })}
                />
                <button
                  className="btn-primary py-2.5 font-bold"
                  onClick={() =>
                    runAction(
                      () => axiosClient.post('/org/menu/categories', newCategory).then(() =>
                        setNewCategory({ name: '', description: '', sortOrder: 1 })
                      ),
                      'تمت إضافة القسم'
                    )
                  }
                >
                  + إضافة قسم
                </button>
              </div>
              <div className="flex flex-wrap gap-2">
                {menu.categories.map((c) => (
                  <span key={c.id} className="px-3.5 py-1.5 rounded-full bg-orange-50 text-orange-700 font-bold text-sm">
                    {c.name}
                  </span>
                ))}
              </div>
            </div>

            <div className="card p-6 space-y-4">
              <h4 className="font-black text-lg">إضافة صنف جديد للمنيو</h4>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                <select
                  className="input-fancy p-2.5 text-sm font-bold"
                  value={newItem.categoryId}
                  onChange={(e) => setNewItem({ ...newItem, categoryId: e.target.value })}
                >
                  <option value="">اختر القسم</option>
                  {menu.categories.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
                <input
                  className="input-fancy p-2.5 text-sm font-semibold"
                  placeholder="اسم الصنف *"
                  value={newItem.name}
                  onChange={(e) => setNewItem({ ...newItem, name: e.target.value })}
                />
                <input
                  type="number"
                  min="0"
                  className="input-fancy p-2.5 text-sm font-semibold"
                  placeholder="السعر الأساسي (ج) *"
                  value={newItem.basePrice}
                  onChange={(e) => setNewItem({ ...newItem, basePrice: e.target.value })}
                />
                <input
                  type="number"
                  min="1"
                  className="input-fancy p-2.5 text-sm font-semibold"
                  placeholder="وقت التحضير (دقيقة)"
                  value={newItem.prepTimeMinutes}
                  onChange={(e) => setNewItem({ ...newItem, prepTimeMinutes: Number(e.target.value) })}
                />
              </div>
              <input
                className="input-fancy w-full p-2.5 text-sm font-semibold"
                placeholder="وصف الصنف والمكونات..."
                value={newItem.description}
                onChange={(e) => setNewItem({ ...newItem, description: e.target.value })}
              />

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="p-4 rounded-2xl bg-slate-50 border border-slate-200 space-y-2">
                  <div className="flex justify-between items-center">
                    <span className="font-bold text-sm text-slate-700">الأحجام / الخيارات (Variants)</span>
                    <button
                      type="button"
                      onClick={() => setNewItem({
                        ...newItem,
                        variants: [...newItem.variants, { name: '', price: '', isDefault: newItem.variants.length === 0 }]
                      })}
                      className="text-xs font-bold text-indigo-600 flex items-center gap-1"
                    >
                      <Plus size={14} /> إضافة حجم
                    </button>
                  </div>
                  {newItem.variants.map((v, i) => (
                    <div key={i} className="flex gap-2">
                      <input
                        className="input-fancy flex-1 p-2 text-xs"
                        placeholder="الحجم (وسط، كبير)"
                        value={v.name}
                        onChange={(e) => {
                          const nv = [...newItem.variants];
                          nv[i].name = e.target.value;
                          setNewItem({ ...newItem, variants: nv });
                        }}
                      />
                      <input
                        type="number"
                        className="input-fancy w-24 p-2 text-xs"
                        placeholder="السعر"
                        value={v.price}
                        onChange={(e) => {
                          const nv = [...newItem.variants];
                          nv[i].price = e.target.value;
                          setNewItem({ ...newItem, variants: nv });
                        }}
                      />
                    </div>
                  ))}
                </div>

                <div className="p-4 rounded-2xl bg-slate-50 border border-slate-200 space-y-2">
                  <div className="flex justify-between items-center">
                    <span className="font-bold text-sm text-slate-700">الإضافات (Addons)</span>
                    <button
                      type="button"
                      onClick={() => setNewItem({
                        ...newItem,
                        addons: [...newItem.addons, { name: '', price: '' }]
                      })}
                      className="text-xs font-bold text-indigo-600 flex items-center gap-1"
                    >
                      <Plus size={14} /> إضافة خيار
                    </button>
                  </div>
                  {newItem.addons.map((a, i) => (
                    <div key={i} className="flex gap-2">
                      <input
                        className="input-fancy flex-1 p-2 text-xs"
                        placeholder="إضافة (جبنة إضافية...)"
                        value={a.name}
                        onChange={(e) => {
                          const na = [...newItem.addons];
                          na[i].name = e.target.value;
                          setNewItem({ ...newItem, addons: na });
                        }}
                      />
                      <input
                        type="number"
                        className="input-fancy w-24 p-2 text-xs"
                        placeholder="السعر"
                        value={a.price}
                        onChange={(e) => {
                          const na = [...newItem.addons];
                          na[i].price = e.target.value;
                          setNewItem({ ...newItem, addons: na });
                        }}
                      />
                    </div>
                  ))}
                </div>
              </div>

              <button
                className="btn-primary px-8 py-3 font-bold"
                onClick={() =>
                  runAction(
                    () => axiosClient.post('/org/menu/items', {
                      ...newItem,
                      categoryId: Number(newItem.categoryId),
                      basePrice: Number(newItem.basePrice),
                      variants: newItem.variants.filter((v) => v.name && v.price).map((v) => ({ ...v, price: Number(v.price) })),
                      addons: newItem.addons.filter((a) => a.name && a.price).map((a) => ({ ...a, price: Number(a.price) })),
                    }).then(() => setNewItem({
                      categoryId: menu.categories[0]?.id ? String(menu.categories[0].id) : '',
                      name: '', description: '', basePrice: '', prepTimeMinutes: 15, variants: [], addons: []
                    })),
                    'تمت إضافة الصنف بنجاح'
                  )
                }
              >
                + حفظ الصنف في المنيو
              </button>
            </div>

            <div className="card p-6">
              <h4 className="font-black text-lg mb-4">الأصناف الحالية ({menu.items.filter((i) => i.active).length})</h4>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {menu.items.filter((i) => i.active).map((item) => (
                  <div key={item.id} className="p-4 rounded-2xl border border-slate-200 bg-white flex flex-col justify-between">
                    <div>
                      <div className="flex justify-between items-start gap-2">
                        <h5 className="font-black text-slate-800">{item.name}</h5>
                        <span className="font-black text-emerald-700">{item.basePrice} ج</span>
                      </div>
                      {item.description && <p className="text-xs text-slate-500 mt-1">{item.description}</p>}
                      {item.variants?.length > 0 && (
                        <p className="text-[11px] text-indigo-600 font-bold mt-2">
                          الأحجام: {item.variants.map((v) => `${v.name} (${v.price}ج)`).join(' • ')}
                        </p>
                      )}
                      {item.addons?.length > 0 && (
                        <p className="text-[11px] text-orange-600 font-bold mt-1">
                          إضافات: {item.addons.map((a) => `${a.name} (+${a.price}ج)`).join(' • ')}
                        </p>
                      )}
                    </div>
                    <div className="flex justify-end pt-3 mt-3 border-t border-slate-100">
                      <button
                        onClick={() => runAction(() => axiosClient.delete(`/org/menu/items/${item.id}`), 'تم حذف الصنف')}
                        className="text-xs text-red-500 font-bold flex items-center gap-1 hover:text-red-700"
                      >
                        <Trash2 size={14} /> أرشفة
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {tab === 'tables' && (
          <div className="space-y-6">
            <div className="card p-6 space-y-4">
              <h4 className="font-black text-lg">إضافة طاولة جديدة للفرع المحدد</h4>
              <div className="flex flex-wrap gap-3 items-end">
                <div>
                  <label className="block text-xs font-bold text-slate-600 mb-1">رقم / اسم الطاولة *</label>
                  <input
                    className="input-fancy p-2.5 text-sm font-semibold"
                    placeholder="مثال: T-7 أو VIP-1"
                    value={newTable.tableNumber}
                    onChange={(e) => setNewTable({ ...newTable, tableNumber: e.target.value })}
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-600 mb-1">السعة (أشخاص)</label>
                  <input
                    type="number"
                    min="1"
                    className="input-fancy w-28 p-2.5 text-sm font-semibold"
                    value={newTable.capacity}
                    onChange={(e) => setNewTable({ ...newTable, capacity: Number(e.target.value) })}
                  />
                </div>
                <button
                  disabled={!selectedBranchId}
                  className="btn-primary px-6 py-2.5 font-bold"
                  onClick={() =>
                    runAction(
                      () => axiosClient.post('/org/tables', { ...newTable, branchId: selectedBranchId })
                        .then(() => setNewTable({ tableNumber: '', capacity: 4 })),
                      'تمت إضافة الطاولة وتوليد QR Code'
                    )
                  }
                >
                  + إضافة طاولة
                </button>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {tables.map((t) => {
                const qrUrl = `${window.location.origin}/m/${t.qrToken}`;
                return (
                  <div
                    key={t.id}
                    className={`card p-6 text-center space-y-3 border-2 ${
                      t.active ? 'border-transparent' : 'border-red-200 bg-red-50/30 opacity-80'
                    }`}
                  >
                    <div className="flex justify-between items-center">
                      <div className="flex items-center gap-2">
                        <span className="text-xl font-black text-slate-800">طاولة {t.tableNumber}</span>
                        {!t.active && (
                          <span className="text-[10px] font-black px-2 py-0.5 rounded-full bg-red-100 text-red-700">
                            معطلة / صيانة
                          </span>
                        )}
                      </div>
                      <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-slate-100 text-slate-600">
                        {t.capacity} مقاعد
                      </span>
                    </div>

                    <div className="bg-white p-3 rounded-2xl border border-slate-200 inline-block mx-auto">
                      <LocalQrImage value={qrUrl} size={160} alt={`QR ${t.tableNumber}`} />
                    </div>
                    <p className="text-[11px] text-slate-400 font-mono break-all" dir="ltr">{qrUrl}</p>

                    <div className="flex flex-wrap gap-1.5 justify-center pt-2">
                      <button
                        onClick={() => handlePrintQrCard(t)}
                        className="px-3 py-2 rounded-xl bg-slate-900 text-white text-xs font-bold flex items-center gap-1 hover:bg-slate-800"
                      >
                        <Printer size={14} /> طباعة بطاقة QR
                      </button>
                      <button
                        onClick={() => window.open(`/m/${t.qrToken}`, '_blank')}
                        className="px-3 py-2 rounded-xl bg-indigo-50 text-indigo-700 text-xs font-bold flex items-center gap-1"
                      >
                        <ExternalLink size={14} /> فتح المنيو
                      </button>
                      <button
                        onClick={() => navigator.clipboard?.writeText(qrUrl)}
                        className="px-3 py-2 rounded-xl bg-slate-100 text-slate-700 text-xs font-bold flex items-center gap-1"
                        title="نسخ الرابط"
                      >
                        <Copy size={14} />
                      </button>
                    </div>

                    <div className="flex flex-wrap gap-1.5 justify-center pt-2 border-t border-slate-100">
                      <button
                        onClick={() => setEditingTable({
                          id: t.id, tableNumber: t.tableNumber, capacity: t.capacity, active: t.active
                        })}
                        className="px-3 py-1.5 rounded-xl bg-blue-50 text-blue-700 text-xs font-bold flex items-center gap-1"
                      >
                        <Pencil size={13} /> تعديل
                      </button>
                      <button
                        onClick={() =>
                          runAction(
                            () => axiosClient.put(`/org/tables/${t.id}`, { active: !t.active }),
                            t.active ? 'تم إيقاف الطاولة للصيانة' : 'تم إعادة تفعيل الطاولة'
                          )
                        }
                        className={`px-3 py-1.5 rounded-xl text-xs font-bold flex items-center gap-1 ${
                          t.active ? 'bg-amber-50 text-amber-700' : 'bg-emerald-50 text-emerald-700'
                        }`}
                      >
                        <Power size={13} /> {t.active ? 'تعطيل' : 'تفعيل'}
                      </button>
                      <button
                        onClick={() =>
                          runAction(() => axiosClient.post(`/org/tables/${t.id}/regenerate-qr`), 'تم تجديد رمز QR')
                        }
                        className="px-3 py-1.5 rounded-xl bg-slate-100 text-slate-600 text-xs font-bold flex items-center gap-1"
                        title="تجديد رمز QR"
                      >
                        <RefreshCw size={13} />
                      </button>
                      <button
                        onClick={() => {
                          if (window.confirm(`هل أنت متأكد من حذف طاولة "${t.tableNumber}" نهائياً؟`)) {
                            runAction(() => axiosClient.delete(`/org/tables/${t.id}`), 'تم حذف الطاولة');
                          }
                        }}
                        className="px-3 py-1.5 rounded-xl bg-red-50 text-red-600 text-xs font-bold flex items-center gap-1"
                        title="حذف الطاولة"
                      >
                        <Trash2 size={13} /> حذف
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {tab === 'branches' && (
          <div className="card p-6 space-y-4">
            <h4 className="font-black text-lg">إضافة فرع جديد</h4>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3 items-end">
              <input
                className="input-fancy p-2.5"
                placeholder="اسم الفرع *"
                value={newBranch.name}
                onChange={(e) => setNewBranch({ ...newBranch, name: e.target.value })}
              />
              <input
                className="input-fancy p-2.5"
                placeholder="المدينة"
                value={newBranch.city}
                onChange={(e) => setNewBranch({ ...newBranch, city: e.target.value })}
              />
              <input
                className="input-fancy p-2.5"
                placeholder="العنوان"
                value={newBranch.address}
                onChange={(e) => setNewBranch({ ...newBranch, address: e.target.value })}
              />
              <button
                className="btn-primary py-2.5 font-bold"
                onClick={() =>
                  runAction(
                    () => axiosClient.post('/org/branches', newBranch).then(() =>
                      setNewBranch({ name: '', city: '', address: '', phone: '' })
                    ),
                    'تمت إضافة الفرع'
                  )
                }
              >
                + إضافة فرع
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-2">
              {branches.map((b) => (
                <div key={b.id} className="p-4 rounded-2xl bg-white border border-slate-200">
                  <p className="font-bold text-slate-800">{b.name}</p>
                  <p className="text-xs text-slate-500 mt-1">{b.city || ''} - {b.address || 'بدون عنوان'}</p>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {editingTable &&
        createPortal(
          <div className="modal-backdrop flex items-center justify-center p-4" dir="rtl" onClick={() => setEditingTable(null)}>
            <div className="bg-white rounded-3xl p-7 w-[400px] max-w-full shadow-2xl space-y-4" onClick={(e) => e.stopPropagation()}>
              <div className="flex justify-between items-center">
                <h3 className="text-xl font-black text-slate-800">تعديل بيانات الطاولة</h3>
                <button onClick={() => setEditingTable(null)}><X size={20} /></button>
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-600 mb-1">رقم / اسم الطاولة</label>
                <input
                  className="input-fancy w-full p-3 font-bold"
                  value={editingTable.tableNumber}
                  onChange={(e) => setEditingTable({ ...editingTable, tableNumber: e.target.value })}
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-600 mb-1">عدد المقاعد</label>
                <input
                  type="number"
                  min="1"
                  className="input-fancy w-full p-3 font-bold"
                  value={editingTable.capacity}
                  onChange={(e) => setEditingTable({ ...editingTable, capacity: Number(e.target.value) })}
                />
              </div>
              <label className="flex items-center gap-3 p-3 rounded-2xl border border-slate-200 bg-slate-50 cursor-pointer">
                <input
                  type="checkbox"
                  checked={editingTable.active}
                  onChange={(e) => setEditingTable({ ...editingTable, active: e.target.checked })}
                  className="w-5 h-5 accent-indigo-600"
                />
                <span className="font-bold text-sm text-slate-700">الطاولة متاحة للاستخدام (غير معطلة)</span>
              </label>
              <div className="flex gap-3 pt-2">
                <button
                  onClick={() =>
                    runAction(
                      () => axiosClient.put(`/org/tables/${editingTable.id}`, {
                        tableNumber: editingTable.tableNumber,
                        capacity: editingTable.capacity,
                        active: editingTable.active,
                      }).then(() => setEditingTable(null)),
                      'تم تحديث بيانات الطاولة'
                    )
                  }
                  className="btn-primary flex-1 py-3 font-bold"
                >
                  حفظ التعديلات
                </button>
                <button onClick={() => setEditingTable(null)} className="flex-1 bg-slate-100 text-slate-600 font-bold py-3 rounded-xl">
                  إلغاء
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}
    </DashboardLayout>
  );
}