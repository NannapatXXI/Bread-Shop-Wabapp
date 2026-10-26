'use client';

// app/(user)/checkout/page.js
// หน้ายืนยันการสั่งซื้อ
// Flow: เลือกที่อยู่ → ใส่โค้ดส่วนลด (optional) → ใส่หมายเหตุ → ยืนยัน

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import toast from 'react-hot-toast';

import { useCart } from '../../CartContext';
import { useAuth } from '../../context/AuthContext';
import { getAddresses, validatePromoCode, createOrder } from '@/services/auth.service';
import Icon from '../../components/Icon';
import Spinner from '../../components/Spinner';
import StatusBadge from '../../components/StatusBadge';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const SHIPPING_FEE = 0; // แสดงผลเท่านั้น — ค่าส่งจริง server เป็นคนกำหนด (OrderService.SHIPPING_FEE)

export default function CheckoutPage() {
  const router            = useRouter();
  const { user }          = useAuth();
  const { items, cartTotal, clearCart } = useCart();

  // ── State ──────────────────────────────────────────────────────
  const [addresses, setAddresses]       = useState([]);
  const [selectedAddress, setSelectedAddress] = useState(null);
  const [promoCode, setPromoCode]       = useState('');
  const [promoResult, setPromoResult]   = useState(null);  // { code, name, discountAmount }
  const [promoError, setPromoError]     = useState('');
  const [promoLoading, setPromoLoading] = useState(false);
  const [note, setNote]                 = useState('');
  const [submitting, setSubmitting]     = useState(false);
  const [successOrder, setSuccessOrder] = useState(null); // OrderResponse หลัง submit

  // ── ดึงที่อยู่ของ user ────────────────────────────────────────
  useEffect(() => {
    if (!user) return;
    getAddresses()
      .then(res => {
        const list = res.data ?? [];
        setAddresses(list);
        // auto-select ที่อยู่ default (ถ้ามี) หรืออันแรก
        const def = list.find(a => a.isDefault) ?? list[0] ?? null;
        setSelectedAddress(def?.id ?? null);
      })
      .catch(() => toast.error('โหลดที่อยู่ไม่สำเร็จ'));
  }, [user]);

  // ── ถ้าตะกร้าว่าง redirect กลับ ──────────────────────────────
  useEffect(() => {
    if (items.length === 0 && !successOrder) {
      router.replace('/product');
    }
  }, [items, successOrder, router]);

  // ── ตรวจโค้ดส่วนลด ───────────────────────────────────────────
  const handleValidatePromo = async () => {
    if (!promoCode.trim()) return;
    setPromoLoading(true);
    setPromoError('');
    setPromoResult(null);
    try {
      const res = await validatePromoCode(promoCode.trim(), cartTotal);
      setPromoResult(res.data);
      toast.success(`ใช้โค้ด "${res.data.name}" ได้เลย!`);
    } catch (err) {
      // err.response?.data คือ ApiResponse จาก backend { success, message, data }
      const backendMsg = err?.response?.data?.message ?? err?.message ?? 'โค้ดไม่ถูกต้อง';
      setPromoError(backendMsg);
    } finally {
      setPromoLoading(false);
    }
  };

  const handleRemovePromo = () => {
    setPromoResult(null);
    setPromoCode('');
    setPromoError('');
  };

  // ── คำนวณยอด ─────────────────────────────────────────────────
  const discount    = promoResult ? Number(promoResult.discountAmount) : 0;
  // [Claude] ส่วนลดห้ามเกินยอดสินค้า — ถ้าติดลบให้ตีเป็น 0
  const totalAmount = Math.max(0, cartTotal - discount + SHIPPING_FEE);

  // ── Submit ────────────────────────────────────────────────────
  const handleSubmit = async () => {
    if (!selectedAddress) {
      toast.error('กรุณาเลือกที่อยู่จัดส่งก่อน');
      return;
    }
    setSubmitting(true);
    try {
      const payload = {
        userId:        user.id,
        addressId:     selectedAddress,
        promotionCode: promoResult?.code ?? null,
        note:          note.trim() || null,
        items: items.map(({ product, quantity }) => ({
          productId: product.id,
          quantity,
        })),
      };

      const res = await createOrder(payload);
      setSuccessOrder(res.data);
      clearCart(); // เคลียร์ตะกร้า
    } catch (err) {
      toast.error(err?.message ?? 'สั่งซื้อไม่สำเร็จ กรุณาลองใหม่');
    } finally {
      setSubmitting(false);
    }
  };

  // ════════════════════════════════════════════════════════════
  // SUCCESS
  // ════════════════════════════════════════════════════════════
  if (successOrder) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', padding: '48px 0' }}>
        <div className="card pad" style={{ maxWidth: 440, width: '100%', textAlign: 'center' }}>
          <div className="empty" style={{ border: 0, padding: '8px 0 0', background: 'none' }}>
            <div className="ic" style={{ background: 'var(--green-soft)', color: 'var(--green)', width: 64, height: 64 }}><Icon name="check" /></div>
          </div>
          <h1 style={{ margin: '0 0 4px', fontSize: 24, fontWeight: 600 }}>สั่งซื้อสำเร็จ!</h1>
          <p className="muted" style={{ margin: '0 0 20px', fontSize: 14 }}>ขอบคุณที่ใช้บริการ BreadShop</p>
          <div style={{ background: 'var(--bg)', borderRadius: 'var(--r-md)', padding: 16, marginBottom: 20 }}>
            <div className="faint" style={{ fontSize: 12 }}>หมายเลขคำสั่งซื้อ</div>
            <b className="mono" style={{ fontFamily: 'ui-monospace,Menlo,monospace', fontSize: 18, color: 'var(--blue)' }}>{successOrder.orderNo}</b>
          </div>
          <div className="rows" style={{ marginBottom: 24, textAlign: 'left' }}>
            <div className="r"><span className="muted">ยอดรวมทั้งสิ้น</span><b className="num">฿{Number(successOrder.totalAmount).toLocaleString()}</b></div>
            <div className="r"><span className="muted">สถานะ</span><StatusBadge status="PENDING" label="รอดำเนินการ" /></div>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <Link href="/history" className="btn btn-primary" style={{ flex: 1 }}>ดูประวัติการสั่งซื้อ</Link>
            <Link href="/product" className="btn btn-outline" style={{ flex: 1 }}>ซื้อต่อ</Link>
          </div>
        </div>
      </div>
    );
  }

  const canSubmit = !(submitting || addresses.length === 0 || !selectedAddress);
  const submitLabel = submitting ? <><Spinner size={16} /> กำลังส่งคำสั่งซื้อ...</> : 'ยืนยันคำสั่งซื้อ';

  // ════════════════════════════════════════════════════════════
  // CHECKOUT FORM
  // ════════════════════════════════════════════════════════════
  return (
    <div style={{ maxWidth: 1080, margin: '0 auto' }}>
      <div className="page-head">
        <div className="back">
          <button className="icon-btn" onClick={() => router.back()} aria-label="กลับ" style={{ border: '1px solid var(--line)', background: 'var(--surface)' }}>
            <Icon name="left" />
          </button>
          <div><h1>ยืนยันคำสั่งซื้อ</h1><p>ตรวจสอบรายการและเลือกที่อยู่จัดส่ง</p></div>
        </div>
      </div>

      <div className="co" style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 24, alignItems: 'start' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16, minWidth: 0 }}>

          <section className="card">
            <div className="card-head"><h2><span className="step">1</span>ที่อยู่จัดส่ง</h2></div>
            <div className="pad">
              {addresses.length === 0 ? (
                <div className="empty compact">
                  <div className="ic"><Icon name="pin" /></div>
                  <h3>ยังไม่มีที่อยู่</h3>
                  <p>เพิ่มที่อยู่ก่อน แล้วค่อยกลับมายืนยันคำสั่งซื้อ</p>
                  <Link className="btn btn-outline sm" href="/profile"><Icon name="plus" size="sm" />เพิ่มที่อยู่</Link>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {addresses.map(addr => (
                    <label key={addr.id} className={`opt${selectedAddress === addr.id ? ' on' : ''}`}>
                      <input type="radio" name="address" value={addr.id}
                        checked={selectedAddress === addr.id} onChange={() => setSelectedAddress(addr.id)} />
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <b style={{ fontWeight: 600 }}>{addr.name}</b>
                          {addr.isDefault && <span className="badge b-green plain">ค่าเริ่มต้น</span>}
                        </div>
                        <div className="muted" style={{ fontSize: 14 }}>{addr.recipientName} · {addr.phone}</div>
                        <div className="muted" style={{ fontSize: 14 }}>
                          {addr.address} แขวง{addr.subdistrict} เขต{addr.district} {addr.province} {addr.postcode}
                        </div>
                      </div>
                    </label>
                  ))}
                  <Link className="link" href="/profile" style={{ fontSize: 14, alignSelf: 'flex-start' }}>+ เพิ่ม / แก้ไขที่อยู่</Link>
                </div>
              )}
            </div>
          </section>

          <section className="card">
            <div className="card-head"><h2><span className="step">2</span>โค้ดส่วนลด <span className="faint" style={{ fontWeight: 400, fontSize: 14, fontFamily: 'var(--font)' }}>(ถ้ามี)</span></h2></div>
            <div className="pad">
              {promoResult ? (
                <div className="alert-ok" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
                  <div>
                    <b>{promoResult.name}</b>
                    <div style={{ fontSize: 13 }}>ลด ฿{Number(promoResult.discountAmount).toLocaleString()}</div>
                  </div>
                  <button className="btn btn-ghost sm btn-danger-text" onClick={handleRemovePromo}>ยกเลิก</button>
                </div>
              ) : (
                <>
                  <div style={{ display: 'flex', gap: 10 }}>
                    <div className="control" style={{ flex: 1 }}>
                      <Icon name="ticket" size="sm" />
                      <input value={promoCode} placeholder="เช่น BREAD50" aria-label="โค้ดส่วนลด"
                        onChange={e => { setPromoCode(e.target.value.toUpperCase()); setPromoError(''); }}
                        onKeyDown={e => e.key === 'Enter' && handleValidatePromo()} />
                    </div>
                    <button className="btn btn-outline" onClick={handleValidatePromo} disabled={!promoCode.trim() || promoLoading}>
                      {promoLoading ? <Spinner size={16} /> : 'ใช้โค้ด'}
                    </button>
                  </div>
                  {promoError && <p className="alert-error" style={{ margin: '10px 0 0' }}>{promoError}</p>}
                </>
              )}
            </div>
          </section>

          <section className="card">
            <div className="card-head"><h2><span className="step">3</span>หมายเหตุถึงร้าน <span className="faint" style={{ fontWeight: 400, fontSize: 14, fontFamily: 'var(--font)' }}>(ถ้ามี)</span></h2></div>
            <div className="pad">
              <textarea className="control" style={{ width: '100%' }} value={note} onChange={e => setNote(e.target.value)}
                placeholder="เช่น ไม่ใส่ถุง, โทรก่อนส่ง" aria-label="หมายเหตุถึงร้าน" />
            </div>
          </section>
        </div>

        <aside className="card" style={{ position: 'sticky', top: 88 }}>
          <div className="card-head"><h2>สรุปคำสั่งซื้อ</h2><span className="muted" style={{ fontSize: 14 }}>{items.length} รายการ</span></div>
          <div className="pad" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12, maxHeight: 288, overflowY: 'auto' }}>
              {items.map(({ product, quantity }) => (
                <div key={product.id} style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                  {product.imageUrl
                    ? <img className="thumb" src={`${API_URL}/${product.imageUrl}`} alt={product.name} />
                    : <span className="thumb" />}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <b style={{ fontWeight: 600 }}>{product.name}</b>
                    <div className="muted" style={{ fontSize: 13 }}>× {quantity}</div>
                  </div>
                  <span className="num">฿{(product.price * quantity).toLocaleString()}</span>
                </div>
              ))}
            </div>
            <div className="rows" style={{ borderTop: '1px solid var(--line)', paddingTop: 16 }}>
              <div className="r"><span className="muted">ราคาสินค้า</span><span className="num">฿{cartTotal.toLocaleString()}</span></div>
              {discount > 0 && (
                <div className="r" style={{ color: 'var(--green)' }}><span>ส่วนลด ({promoResult?.code})</span><span className="num">−฿{discount.toLocaleString()}</span></div>
              )}
              <div className="r"><span className="muted">ค่าจัดส่ง</span><span style={{ color: 'var(--green)', fontWeight: 600 }}>ฟรี</span></div>
              <div className="r total"><span>ยอดรวม</span><b className="num">฿{totalAmount.toLocaleString()}</b></div>
            </div>
            <button className="btn btn-primary btn-block" onClick={handleSubmit} disabled={!canSubmit}>{submitLabel}</button>
            {addresses.length === 0 && (
              <p className="hint" style={{ margin: '-6px 0 0', textAlign: 'center', display: 'flex', gap: 6, justifyContent: 'center', alignItems: 'center' }}>
                <Icon name="info" size="sm" />เพิ่มที่อยู่จัดส่งก่อนยืนยัน
              </p>
            )}
          </div>
        </aside>
      </div>

      <div className="paybar m-only" style={{ display: 'none' }}>
        <div><div className="muted" style={{ fontSize: 13 }}>ยอดรวม {items.length} รายการ</div><b className="num">฿{totalAmount.toLocaleString()}</b></div>
        <button className="btn btn-primary" onClick={handleSubmit} disabled={!canSubmit}>{submitLabel}</button>
      </div>
      <style>{`@media(max-width:760px){.paybar{display:flex!important}main.shop{padding-bottom:150px}}@media(max-width:900px){.co{grid-template-columns:1fr!important}.co aside{position:static!important}}`}</style>
    </div>
  );
}
