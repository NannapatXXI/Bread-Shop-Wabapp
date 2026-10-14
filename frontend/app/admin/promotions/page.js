'use client';

// app/admin/promotions/page.js
// จัดการโค้ดส่วนลด — ดูรายการ, สร้างใหม่, เปิด/ปิด

import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { adminGetPromotions, adminCreatePromotion, adminTogglePromotion, adminDeletePromotion } from '@/services/auth.service';
import { exportCSV } from '@/lib/exportCSV';
import Spinner from '@/app/components/Spinner';
import { SkeletonRows } from '@/app/components/Skeleton';
import Icon from '@/app/components/Icon';

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

  const expiredActive = promos.filter(p => p.isActive && isExpired(p.expiredAt));

  return (
    <>
      <div className="page-head">
        <div><h1>โปรโมชั่น</h1><p>สร้างและจัดการโค้ดส่วนลด</p></div>
        <div className="actions">
          <button className="btn btn-outline" onClick={() => exportCSV(
            promos,
            ['โค้ด', 'ชื่อ', 'ประเภท', 'ส่วนลด', 'ยอดขั้นต่ำ', 'ใช้ไปแล้ว', 'จำกัดสิทธิ์', 'วันเริ่ม', 'วันหมดอายุ', 'สถานะ'],
            ['code', 'name', 'discountType', 'discountValue', 'minOrderAmount', 'usedCount', 'usageLimit', 'startedAt', 'expiredAt', 'isActive'],
            'promotions'
          )}>
            <Icon name="download" size="sm" />Export
          </button>
          <button className="btn btn-primary" onClick={() => setShowForm(true)}><Icon name="plus" size="sm" />สร้างโค้ด</button>
        </div>
      </div>

      <div className="stats" style={{ gridTemplateColumns: 'repeat(3,1fr)' }}>
        <div className="stat"><div className="k">โค้ดทั้งหมด</div><div className="v">{promos.length}</div></div>
        <div className="stat"><div className="k">ใช้งานอยู่</div><div className="v" style={{ color: 'var(--green)' }}>{promos.filter(p => p.isActive).length}</div></div>
        <div className="stat"><div className="k">ปิดใช้งาน</div><div className="v">{promos.filter(p => !p.isActive).length}</div></div>
      </div>

      {expiredActive.length > 0 && (
        <div role="status" style={{ display: 'flex', gap: 12, alignItems: 'flex-start', padding: '14px 16px', borderRadius: 'var(--r-md)', background: 'var(--amber-soft)', color: 'var(--amber)', marginBottom: 16, fontSize: 14 }}>
          <Icon name="alert" />
          <div>
            <b>โค้ด {expiredActive.map(p => p.code).join(', ')} หมดอายุแล้วแต่ยังเปิดอยู่</b>
            <div style={{ color: 'var(--ink)' }}>ลูกค้าจะใช้โค้ดนี้ไม่ได้ ปิดโค้ดได้จากสวิตช์ในตาราง</div>
          </div>
        </div>
      )}

      <section className="card">
        <div className="table-wrap">
          {loading ? (
            <table className="t"><tbody><SkeletonRows rows={6} cols={7} /></tbody></table>
          ) : promos.length === 0 ? (
            <div className="empty" style={{ border: 0 }}>
              <div className="ic"><Icon name="tag" /></div>
              <h3>ยังไม่มีโปรโมชั่น</h3>
              <p>สร้างโค้ดส่วนลดแรกให้ลูกค้าใช้ตอนชำระเงิน</p>
              <button className="btn btn-primary" onClick={() => setShowForm(true)}><Icon name="plus" size="sm" />สร้างโค้ด</button>
            </div>
          ) : (
            <table className="t">
              <thead>
                <tr><th>โค้ด</th><th className="hide-m">ชื่อ</th><th>ส่วนลด</th><th>ใช้ไปแล้ว</th><th>หมดอายุ</th><th>เปิดใช้</th><th></th></tr>
              </thead>
              <tbody>
                {promos.map(p => {
                  const expired = isExpired(p.expiredAt);
                  return (
                    <tr key={p.id}>
                      <td>
                        <span style={{ fontFamily: 'ui-monospace,Menlo,monospace', fontWeight: 600, padding: '4px 8px', border: '1px dashed var(--line-2)', borderRadius: 6, color: expired ? 'var(--red)' : undefined }}>
                          {p.code}
                        </span>
                      </td>
                      <td className="hide-m">{p.name}</td>
                      <td>
                        <span className="badge plain b-blue">
                          {p.discountType === 'FIXED' ? `ลด ฿${Number(p.discountValue).toLocaleString()}` : `ลด ${p.discountValue}%`}
                        </span>
                        {p.minOrderAmount && <span className="sub">ขั้นต่ำ ฿{Number(p.minOrderAmount).toLocaleString()}</span>}
                      </td>
                      <td>
                        <div className="num" style={{ fontSize: 14 }}>{p.usedCount}{p.usageLimit ? ` / ${p.usageLimit}` : ''}</div>
                        {p.usageLimit ? (
                          <div style={{ height: 6, width: 96, borderRadius: 3, background: 'var(--bg)', marginTop: 4 }}>
                            <div style={{ width: `${Math.min(100, (p.usedCount / p.usageLimit) * 100)}%`, height: '100%', background: 'var(--blue)', borderRadius: 3 }} />
                          </div>
                        ) : null}
                      </td>
                      <td>
                        <span className="num">{fmt(p.expiredAt)}</span>
                        {expired && <span className="sub" style={{ color: 'var(--red)' }}>หมดอายุแล้ว</span>}
                      </td>
                      <td>
                        <button className={`switch${p.isActive ? ' on' : ''}`} onClick={() => handleToggle(p.id)}
                          aria-label={`เปิดใช้โค้ด ${p.code}`} aria-pressed={!!p.isActive} />
                      </td>
                      <td>
                        <div className="row-actions">
                          <button className="icon-btn sm" onClick={() => handleDelete(p.id, p.code)} aria-label={`ลบโปรโมชั่น ${p.code}`} title="ลบโปรโมชั่น" style={{ color: 'var(--red)' }}>
                            <Icon name="trash" size="sm" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </section>

      {showForm && (
        <div className="modal-scrim" onClick={e => e.target === e.currentTarget && setShowForm(false)}>
          <div className="modal" role="dialog" aria-label="สร้างโปรโมชั่นใหม่" style={{ maxWidth: 560 }}>
            <div className="modal-head">
              <h2>สร้างโปรโมชั่นใหม่</h2>
              <button className="icon-btn" onClick={() => setShowForm(false)} aria-label="ปิด"><Icon name="x" /></button>
            </div>
            <form onSubmit={handleSubmit}>
              <div className="modal-body grid-2" style={{ gap: 16 }}>
                <div className="field">
                  <label htmlFor="pr-code">โค้ด <span className="req">*</span></label>
                  <div className="control"><input id="pr-code" required value={form.code} placeholder="BREAD50" style={{ textTransform: 'uppercase', fontFamily: 'ui-monospace,Menlo,monospace' }}
                    onChange={e => setForm(f => ({...f, code: e.target.value.toUpperCase()}))} /></div>
                </div>
                <div className="field">
                  <label htmlFor="pr-name">ชื่อโปรโมชั่น <span className="req">*</span></label>
                  <div className="control"><input id="pr-name" required value={form.name} placeholder="ลด 50 บาท"
                    onChange={e => setForm(f => ({...f, name: e.target.value}))} /></div>
                </div>
                <div className="field">
                  <label htmlFor="pr-type">ประเภทส่วนลด <span className="req">*</span></label>
                  <div className="control"><select id="pr-type" value={form.discountType} onChange={e => setForm(f => ({...f, discountType: e.target.value}))}>
                    <option value="FIXED">ลดคงที่ (บาท)</option>
                    <option value="PERCENT">ลดเป็น %</option>
                  </select></div>
                </div>
                <div className="field">
                  <label htmlFor="pr-value">{form.discountType === 'FIXED' ? 'ลดกี่บาท' : 'ลดกี่ %'} <span className="req">*</span></label>
                  <div className="control"><input id="pr-value" required type="number" min="0" value={form.discountValue}
                    placeholder={form.discountType === 'FIXED' ? '50' : '10'}
                    onChange={e => setForm(f => ({...f, discountValue: e.target.value}))} /></div>
                </div>
                <div className="field">
                  <label htmlFor="pr-min">ยอดขั้นต่ำ (บาท)</label>
                  <div className="control"><input id="pr-min" type="number" min="0" value={form.minOrderAmount} placeholder="ไม่บังคับ"
                    onChange={e => setForm(f => ({...f, minOrderAmount: e.target.value}))} /></div>
                </div>
                <div className="field">
                  <label htmlFor="pr-extra">{form.discountType === 'PERCENT' ? 'ลดสูงสุดไม่เกิน (บาท)' : 'จำนวนสิทธิ์ (ครั้ง)'}</label>
                  <div className="control"><input id="pr-extra" type="number" min="0" placeholder="ไม่บังคับ"
                    value={form.discountType === 'PERCENT' ? form.maxDiscount : form.usageLimit}
                    onChange={e => setForm(f => form.discountType === 'PERCENT'
                      ? {...f, maxDiscount: e.target.value}
                      : {...f, usageLimit: e.target.value})} /></div>
                </div>
                <div className="field">
                  <label htmlFor="pr-start">วันเริ่ม <span className="req">*</span></label>
                  <div className="control"><input id="pr-start" required type="datetime-local" value={form.startedAt}
                    onChange={e => setForm(f => ({...f, startedAt: e.target.value}))} /></div>
                </div>
                <div className="field">
                  <label htmlFor="pr-end">วันหมดอายุ <span className="req">*</span></label>
                  <div className="control"><input id="pr-end" required type="datetime-local" value={form.expiredAt}
                    onChange={e => setForm(f => ({...f, expiredAt: e.target.value}))} /></div>
                </div>
              </div>
              <div className="modal-foot">
                <button type="button" className="btn btn-outline" style={{ flex: 1 }} onClick={() => setShowForm(false)}>ยกเลิก</button>
                <button type="submit" className="btn btn-primary" style={{ flex: 1 }} disabled={saving}>
                  {saving ? <><Spinner size={16} /> กำลังสร้าง...</> : 'สร้างโปรโมชั่น'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
