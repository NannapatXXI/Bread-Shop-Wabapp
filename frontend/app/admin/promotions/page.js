'use client';

// app/admin/promotions/page.js
// จัดการโค้ดส่วนลด — ดูรายการ, สร้างใหม่, เปิด/ปิด

import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { adminGetPromotions, adminCreatePromotion, adminTogglePromotion, adminDeletePromotion } from '@/services/auth.service';
import { exportCSV } from '@/lib/exportCSV';
import Spinner from '@/app/components/Spinner';
import { SkeletonRows } from '@/app/components/Skeleton';

const EMPTY_FORM = {
  code: '', name: '', discountType: 'FIXED',
  discountValue: '', minOrderAmount: '', maxDiscount: '',
  usageLimit: '', startedAt: '', expiredAt: '',
};

export default function AdminPromotionsPage() {
  const [promos,   setPromos]   = useState([]);
  const [loading,  setLoading]  = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form,     setForm]     = useState(EMPTY_FORM);
  const [saving,   setSaving]   = useState(false);

  useEffect(() => { fetchPromos(); }, []);

  const fetchPromos = async () => {
    try {
      const res = await adminGetPromotions();
      setPromos(res.data ?? []);
    } catch { toast.error('โหลดโปรโมชั่นไม่สำเร็จ'); }
    finally  { setLoading(false); }
  };

  const handleToggle = async (id) => {
    try {
      await adminTogglePromotion(id);
      setPromos(prev => prev.map(p =>
        p.id === id ? { ...p, isActive: !p.isActive } : p
      ));
    } catch { toast.error('อัปเดตไม่สำเร็จ'); }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      // แปลง datetime-local string → ISO format ที่ backend รับได้
      const payload = {
        ...form,
        discountValue:  Number(form.discountValue),
        minOrderAmount: form.minOrderAmount ? Number(form.minOrderAmount) : null,
        maxDiscount:    form.maxDiscount    ? Number(form.maxDiscount)    : null,
        usageLimit:     form.usageLimit     ? Number(form.usageLimit)     : null,
        startedAt:      form.startedAt  + ':00',
        expiredAt:      form.expiredAt  + ':00',
      };
      await adminCreatePromotion(payload);
      toast.success('สร้างโปรโมชั่นสำเร็จ');
      setShowForm(false);
      setForm(EMPTY_FORM);
      fetchPromos();
    } catch (err) {
      toast.error(err?.message ?? 'สร้างไม่สำเร็จ');
    } finally { setSaving(false); }
  };

  const handleDelete = async (id, code) => {
    if (!window.confirm(`ยืนยันการลบโปรโมชั่น "${code}" ?`)) return;
    try {
      await adminDeletePromotion(id);
      setPromos(prev => prev.filter(p => p.id !== id));
      toast.success('ลบโปรโมชั่นสำเร็จ');
    } catch { toast.error('ลบไม่สำเร็จ'); }
  };

  const isExpired = (dt) => dt && new Date(dt) < new Date();

  const fmt = (dt) => dt ? new Date(dt).toLocaleDateString('th-TH', { dateStyle: 'medium' }) : '-';

  return (
    <div className="bg-[#EEF4FB] min-h-screen p-6">

      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">Promotion Management</h1>
          <p className="text-gray-500 text-sm">สร้างและจัดการโค้ดส่วนลด</p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <button
            onClick={() => exportCSV(
              promos,
              ['โค้ด', 'ชื่อ', 'ประเภท', 'ส่วนลด', 'ยอดขั้นต่ำ', 'ใช้ไปแล้ว', 'จำกัดสิทธิ์', 'วันเริ่ม', 'วันหมดอายุ', 'สถานะ'],
              ['code', 'name', 'discountType', 'discountValue', 'minOrderAmount', 'usedCount', 'usageLimit', 'startedAt', 'expiredAt', 'isActive'],
              'promotions'
            )}
            className="flex items-center gap-2 px-4 py-2 border border-gray-300 bg-white rounded-xl text-sm font-medium text-gray-600 hover:bg-gray-50 transition">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            Export
          </button>
          <button onClick={() => setShowForm(true)}
            className="px-4 py-2 bg-[#0B1F33] text-[#A8CEFF] font-semibold rounded-xl hover:bg-blue-900 transition text-sm whitespace-nowrap">
            + สร้างโปรโมชั่น
          </button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-5">
        {[
          { label: 'ทั้งหมด',   value: promos.length,                         color: 'text-gray-800' },
          { label: 'ใช้งานอยู่', value: promos.filter(p => p.isActive).length, color: 'text-green-600' },
          { label: 'ปิดใช้งาน', value: promos.filter(p => !p.isActive).length, color: 'text-red-500'   },
        ].map(s => (
          <div key={s.label} className="bg-white rounded-xl p-4 shadow-sm text-center">
            <p className={`text-2xl font-bold ${s.color}`}>{s.value}</p>
            <p className="text-xs text-gray-500 mt-0.5">{s.label}</p>
          </div>
        ))}
      </div>

      {/* Table */}
      <div className="bg-white rounded-2xl shadow-sm overflow-x-auto">
        {loading ? (
          <table className="w-full text-sm min-w-[600px]">
            <tbody><SkeletonRows rows={6} cols={7} /></tbody>
          </table>
        ) : promos.length === 0 ? (
          <div className="text-center py-16 text-gray-400">ยังไม่มีโปรโมชั่น</div>
        ) : (
          <table className="w-full text-sm min-w-[600px]">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr>
                <th className="px-4 py-3 text-left">โค้ด</th>
                <th className="px-4 py-3 text-left">ชื่อ</th>
                <th className="px-4 py-3 text-center">ประเภท / ส่วนลด</th>
                <th className="px-4 py-3 text-center">ใช้ไปแล้ว</th>
                <th className="px-4 py-3 text-center">วันหมดอายุ</th>
                <th className="px-4 py-3 text-center">สถานะ</th>
                <th className="px-4 py-3 text-center">จัดการ</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {promos.map(p => {
                const expired = isExpired(p.expiredAt);
                return (
                <tr key={p.id} className={`hover:bg-gray-50 transition ${expired ? 'bg-red-50/40' : ''}`}>
                  <td className="px-4 py-3">
                    <span className={`font-mono font-bold tracking-wide ${expired ? 'text-red-500' : 'text-gray-800'}`}>
                      {p.code}
                    </span>
                    {expired && (
                      <span className="ml-2 text-xs bg-red-100 text-red-600 px-1.5 py-0.5 rounded font-medium">หมดอายุ</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-gray-600">{p.name}</td>
                  <td className="px-4 py-3 text-center">
                    <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${p.discountType === 'FIXED' ? 'bg-blue-100 text-blue-700' : 'bg-orange-100 text-orange-700'}`}>
                      {p.discountType === 'FIXED' ? `ลด ฿${Number(p.discountValue).toLocaleString()}` : `ลด ${p.discountValue}%`}
                    </span>
                    {p.minOrderAmount && (
                      <p className="text-xs text-gray-400 mt-0.5">ขั้นต่ำ ฿{Number(p.minOrderAmount).toLocaleString()}</p>
                    )}
                  </td>
                  <td className="px-4 py-3 text-center text-gray-600">
                    {p.usedCount}{p.usageLimit ? `/${p.usageLimit}` : ''}
                  </td>
                  <td className={`px-4 py-3 text-center ${expired ? 'text-red-500 font-medium' : 'text-gray-500'}`}>
                    {fmt(p.expiredAt)}
                  </td>
                  <td className="px-4 py-3 text-center">
                    <button onClick={() => handleToggle(p.id)}
                      className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors
                        ${p.isActive ? 'bg-green-500' : 'bg-gray-300'}`}>
                      <span className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform
                        ${p.isActive ? 'translate-x-6' : 'translate-x-1'}`} />
                    </button>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <button
                      onClick={() => handleDelete(p.id, p.code)}
                      className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition"
                      title="ลบโปรโมชั่น">
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                          d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  </td>
                </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* ── Create Form Modal ─────────────────────────────────── */}
      {showForm && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4 overflow-y-auto"
          onClick={e => e.target === e.currentTarget && setShowForm(false)}>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg p-6 my-4">

            <div className="flex justify-between items-center mb-5">
              <h3 className="font-bold text-gray-800 text-lg">สร้างโปรโมชั่นใหม่</h3>
              <button onClick={() => setShowForm(false)} className="text-gray-400 hover:text-gray-600 text-xl">✕</button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-xs font-semibold text-gray-600 block mb-1">โค้ด *</label>
                  <input required value={form.code}
                    onChange={e => setForm(f => ({...f, code: e.target.value.toUpperCase()}))}
                    placeholder="BREAD50" className="w-full border rounded-lg px-3 py-2 text-sm uppercase font-mono focus:outline-none focus:ring-2 focus:ring-[#0B1F33]" />
                </div>
                <div>
                  <label className="text-xs font-semibold text-gray-600 block mb-1">ชื่อโปรโมชั่น *</label>
                  <input required value={form.name}
                    onChange={e => setForm(f => ({...f, name: e.target.value}))}
                    placeholder="ลด 50 บาท" className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#0B1F33]" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-xs font-semibold text-gray-600 block mb-1">ประเภทส่วนลด *</label>
                  <select value={form.discountType}
                    onChange={e => setForm(f => ({...f, discountType: e.target.value}))}
                    className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#0B1F33]">
                    <option value="FIXED">FIXED — ลดคงที่ (บาท)</option>
                    <option value="PERCENT">PERCENT — ลดเป็น %</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-semibold text-gray-600 block mb-1">
                    {form.discountType === 'FIXED' ? 'ลดกี่บาท *' : 'ลดกี่ % *'}
                  </label>
                  <input required type="number" min="0" value={form.discountValue}
                    onChange={e => setForm(f => ({...f, discountValue: e.target.value}))}
                    placeholder={form.discountType === 'FIXED' ? '50' : '10'}
                    className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#0B1F33]" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-xs font-semibold text-gray-600 block mb-1">ยอดขั้นต่ำ (บาท)</label>
                  <input type="number" min="0" value={form.minOrderAmount}
                    onChange={e => setForm(f => ({...f, minOrderAmount: e.target.value}))}
                    placeholder="200 (ไม่บังคับ)" className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#0B1F33]" />
                </div>
                <div>
                  <label className="text-xs font-semibold text-gray-600 block mb-1">
                    {form.discountType === 'PERCENT' ? 'ลดสูงสุดไม่เกิน (บาท)' : 'จำนวนสิทธิ์ (ครั้ง)'}
                  </label>
                  <input type="number" min="0"
                    value={form.discountType === 'PERCENT' ? form.maxDiscount : form.usageLimit}
                    onChange={e => setForm(f => form.discountType === 'PERCENT'
                      ? {...f, maxDiscount: e.target.value}
                      : {...f, usageLimit: e.target.value})}
                    placeholder="ไม่บังคับ" className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#0B1F33]" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-xs font-semibold text-gray-600 block mb-1">วันเริ่ม *</label>
                  <input required type="datetime-local" value={form.startedAt}
                    onChange={e => setForm(f => ({...f, startedAt: e.target.value}))}
                    className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#0B1F33]" />
                </div>
                <div>
                  <label className="text-xs font-semibold text-gray-600 block mb-1">วันหมดอายุ *</label>
                  <input required type="datetime-local" value={form.expiredAt}
                    onChange={e => setForm(f => ({...f, expiredAt: e.target.value}))}
                    className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-[#0B1F33]" />
                </div>
              </div>

              <div className="flex gap-3 pt-2">
                <button type="button" onClick={() => setShowForm(false)}
                  className="flex-1 py-2.5 border rounded-xl text-gray-600 hover:bg-gray-50 text-sm font-medium">
                  ยกเลิก
                </button>
                <button type="submit" disabled={saving}
                  className="flex-1 py-2.5 bg-[#0B1F33] text-[#A8CEFF] rounded-xl font-semibold text-sm hover:bg-blue-900 disabled:opacity-50">
                  {saving
                    ? <span className="flex items-center justify-center gap-2"><Spinner size={16} color="#A8CEFF" /> กำลังสร้าง...</span>
                    : 'สร้างโปรโมชั่น'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
