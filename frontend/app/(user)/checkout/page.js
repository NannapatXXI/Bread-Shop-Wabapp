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

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const SHIPPING_FEE = 0; // ฟรีค่าจัดส่งสำหรับตอนนี้

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
    getAddresses(user.id)
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
  }, [items]);

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
        shippingFee:   SHIPPING_FEE,
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
  // SUCCESS MODAL
  // ════════════════════════════════════════════════════════════
  if (successOrder) {
    return (
      <div className="min-h-screen bg-[#EEF4FB] flex items-center justify-center p-6">
        <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-8 text-center">
          {/* Icon */}
          <div className="w-20 h-20 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <span className="text-4xl">✅</span>
          </div>

          <h2 className="text-2xl font-bold text-gray-800 mb-1">สั่งซื้อสำเร็จ!</h2>
          <p className="text-gray-500 text-sm mb-4">ขอบคุณที่ใช้บริการ BreadShop</p>

          {/* Order number */}
          <div className="bg-[#EEF4FB] rounded-xl p-4 mb-6">
            <p className="text-xs text-gray-400 mb-1">หมายเลขคำสั่งซื้อ</p>
            <p className="text-lg font-bold text-[#0B1F33] tracking-wider">{successOrder.orderNo}</p>
          </div>

          {/* ยอดรวม */}
          <div className="flex justify-between text-sm text-gray-600 mb-1">
            <span>ยอดรวมทั้งสิ้น</span>
            <span className="font-bold text-[#0B1F33]">฿{Number(successOrder.totalAmount).toLocaleString()}</span>
          </div>
          <div className="flex justify-between text-sm text-gray-400 mb-6">
            <span>สถานะ</span>
            <span className="text-yellow-600 font-medium">รอดำเนินการ</span>
          </div>

          {/* ปุ่ม */}
          <div className="flex gap-3">
            <Link href="/history" className="flex-1">
              <button className="w-full py-3 bg-[#0B1F33] text-[#A8CEFF] font-semibold rounded-xl hover:bg-blue-900 transition">
                ดูประวัติการสั่งซื้อ
              </button>
            </Link>
            <Link href="/product" className="flex-1">
              <button className="w-full py-3 border border-gray-200 text-gray-600 font-semibold rounded-xl hover:bg-gray-50 transition">
                ซื้อต่อ
              </button>
            </Link>
          </div>
        </div>
      </div>
    );
  }

  // ════════════════════════════════════════════════════════════
  // CHECKOUT FORM
  // ════════════════════════════════════════════════════════════
  return (
    <div className="bg-[#EEF4FB] min-h-screen p-6">

      {/* Header */}
      <div className="mb-6 flex items-center gap-4">
        <button
          onClick={() => router.back()}
          className="flex items-center justify-center w-9 h-9 rounded-xl bg-white border border-gray-200 text-gray-500 hover:bg-[#0F2235] hover:text-white hover:border-[#0F2235] transition shadow-sm"
        >
          <svg xmlns="http://www.w3.org/2000/svg" className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <div>
          <h1 className="text-2xl font-bold text-gray-800">ยืนยันคำสั่งซื้อ</h1>
          <p className="text-gray-500 text-sm">ตรวจสอบรายการและเลือกที่อยู่จัดส่ง</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* ════════════════ LEFT ════════════════ */}
        <div className="lg:col-span-2 space-y-5">

          {/* ── เลือกที่อยู่ ──────────────────────────────────── */}
          <section className="bg-white rounded-2xl p-5 shadow-sm">
            <h2 className="font-bold text-gray-800 mb-4 flex items-center gap-2">
              <span className="w-6 h-6 bg-[#0B1F33] text-[#A8CEFF] rounded-full text-xs flex items-center justify-center font-bold">1</span>
              ที่อยู่จัดส่ง
            </h2>

            {addresses.length === 0 ? (
              <div className="text-center py-6">
                <p className="text-gray-400 mb-3">ยังไม่มีที่อยู่ กรุณาเพิ่มที่อยู่ก่อน</p>
                <Link href="/profile">
                  <button className="px-5 py-2 bg-[#0B1F33] text-[#A8CEFF] rounded-lg text-sm font-semibold hover:bg-blue-900 transition">
                    + เพิ่มที่อยู่ใน Profile
                  </button>
                </Link>
              </div>
            ) : (
              <div className="space-y-3">
                {addresses.map(addr => (
                  <label
                    key={addr.id}
                    className={`flex items-start gap-3 p-4 rounded-xl border-2 cursor-pointer transition
                      ${selectedAddress === addr.id
                        ? 'border-[#0B1F33] bg-blue-50'
                        : 'border-gray-100 hover:border-gray-300'}`}
                  >
                    <input
                      type="radio"
                      name="address"
                      value={addr.id}
                      checked={selectedAddress === addr.id}
                      onChange={() => setSelectedAddress(addr.id)}
                      className="mt-1 accent-[#0B1F33]"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-0.5">
                        <span className="font-semibold text-gray-800">{addr.name}</span>
                        {addr.isDefault && (
                          <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded-full font-medium">
                            ค่าเริ่มต้น
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-gray-600">{addr.recipientName} · {addr.phone}</p>
                      <p className="text-sm text-gray-500 mt-0.5 leading-relaxed">
                        {addr.address} แขวง{addr.subdistrict} เขต{addr.district} {addr.province} {addr.postcode}
                      </p>
                    </div>
                  </label>
                ))}

                <Link href="/profile">
                  <button className="text-sm text-blue-600 hover:underline mt-1">
                    + เพิ่ม / แก้ไขที่อยู่
                  </button>
                </Link>
              </div>
            )}
          </section>

          {/* ── โค้ดส่วนลด ────────────────────────────────────── */}
          <section className="bg-white rounded-2xl p-5 shadow-sm">
            <h2 className="font-bold text-gray-800 mb-4 flex items-center gap-2">
              <span className="w-6 h-6 bg-[#0B1F33] text-[#A8CEFF] rounded-full text-xs flex items-center justify-center font-bold">2</span>
              โค้ดส่วนลด
              <span className="text-gray-400 text-xs font-normal">(ถ้ามี)</span>
            </h2>

            {promoResult ? (
              /* โค้ดใช้ได้แล้ว */
              <div className="flex items-center justify-between bg-green-50 border border-green-200 rounded-xl p-3">
                <div>
                  <p className="font-semibold text-green-700">🎉 {promoResult.name}</p>
                  <p className="text-sm text-green-600">
                    ลด ฿{Number(promoResult.discountAmount).toLocaleString()}
                  </p>
                </div>
                <button
                  onClick={handleRemovePromo}
                  className="text-red-400 hover:text-red-500 text-sm underline"
                >
                  ยกเลิก
                </button>
              </div>
            ) : (
              /* กรอกโค้ด */
              <div className="space-y-2">
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={promoCode}
                    onChange={e => { setPromoCode(e.target.value.toUpperCase()); setPromoError(''); }}
                    onKeyDown={e => e.key === 'Enter' && handleValidatePromo()}
                    placeholder="ใส่โค้ดส่วนลด เช่น BREAD50"
                    className="flex-1 border rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#0B1F33] uppercase"
                  />
                  <button
                    onClick={handleValidatePromo}
                    disabled={!promoCode.trim() || promoLoading}
                    className="px-5 py-2.5 bg-[#0B1F33] text-[#A8CEFF] text-sm font-semibold rounded-xl hover:bg-blue-900 disabled:opacity-50 transition"
                  >
                    {promoLoading ? '...' : 'ใช้โค้ด'}
                  </button>
                </div>
                {promoError && (
                  <p className="text-red-500 text-xs">{promoError}</p>
                )}
              </div>
            )}
          </section>

          {/* ── หมายเหตุ ──────────────────────────────────────── */}
          <section className="bg-white rounded-2xl p-5 shadow-sm">
            <h2 className="font-bold text-gray-800 mb-4 flex items-center gap-2">
              <span className="w-6 h-6 bg-[#0B1F33] text-[#A8CEFF] rounded-full text-xs flex items-center justify-center font-bold">3</span>
              หมายเหตุ
              <span className="text-gray-400 text-xs font-normal">(ถ้ามี)</span>
            </h2>
            <textarea
              value={note}
              onChange={e => setNote(e.target.value)}
              placeholder="เช่น ขอไม่ใส่ถุง, โทรก่อนส่ง..."
              rows={3}
              className="w-full border rounded-xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-[#0B1F33] resize-none"
            />
          </section>
        </div>

        {/* ════════════════ RIGHT: สรุปคำสั่งซื้อ ════════════ */}
        <div className="space-y-5">

          {/* รายการสินค้า */}
          <section className="bg-white rounded-2xl p-5 shadow-sm">
            <h2 className="font-bold text-gray-800 mb-4">
              รายการสินค้า ({items.length} รายการ)
            </h2>

            <div className="space-y-3 max-h-72 overflow-y-auto pr-1">
              {items.map(({ product, quantity }) => (
                <div key={product.id} className="flex items-center gap-3">
                  {/* รูป */}
                  <div className="w-14 h-14 flex-shrink-0 rounded-lg overflow-hidden bg-gray-100">
                    {product.imageUrl ? (
                      <img
                        src={`${API_URL}/${product.imageUrl}`}
                        alt={product.name}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center text-xl">🍞</div>
                    )}
                  </div>
                  {/* ข้อมูล */}
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-gray-800 truncate">{product.name}</p>
                    <p className="text-xs text-gray-400">x{quantity}</p>
                  </div>
                  <span className="text-sm font-bold text-gray-700 flex-shrink-0">
                    ฿{(product.price * quantity).toLocaleString()}
                  </span>
                </div>
              ))}
            </div>
          </section>

          {/* สรุปยอด */}
          <section className="bg-white rounded-2xl p-5 shadow-sm">
            <h2 className="font-bold text-gray-800 mb-4">สรุปยอด</h2>

            <div className="space-y-2 text-sm">
              <div className="flex justify-between text-gray-600">
                <span>ราคาสินค้า</span>
                <span>฿{cartTotal.toLocaleString()}</span>
              </div>

              {discount > 0 && (
                <div className="flex justify-between text-green-600">
                  <span>ส่วนลด ({promoResult?.code})</span>
                  <span>−฿{discount.toLocaleString()}</span>
                </div>
              )}

              <div className="flex justify-between text-gray-600">
                <span>ค่าจัดส่ง</span>
                <span className="text-green-600 font-medium">ฟรี</span>
              </div>

              <div className="border-t pt-2 mt-2 flex justify-between items-center">
                <span className="font-bold text-gray-800">ยอดรวมทั้งสิ้น</span>
                <span className="text-xl font-bold text-[#0B1F33]">
                  ฿{totalAmount.toLocaleString()}
                </span>
              </div>
            </div>

            {/* ปุ่ม ยืนยัน */}
            <button
              onClick={handleSubmit}
              disabled={submitting || addresses.length === 0 || !selectedAddress}
              className="
                w-full mt-5 py-3.5
                bg-[#0B1F33] text-[#A8CEFF]
                font-bold rounded-xl text-base
                hover:bg-blue-900 transition
                disabled:opacity-50 disabled:cursor-not-allowed
                flex items-center justify-center gap-2
              "
            >
              {submitting ? (
                <>
                  <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
                  </svg>
                  กำลังส่งคำสั่งซื้อ...
                </>
              ) : (
                '✓ ยืนยันการสั่งซื้อ'
              )}
            </button>

            {addresses.length === 0 && (
              <p className="text-red-400 text-xs text-center mt-2">
                กรุณาเพิ่มที่อยู่จัดส่งก่อน
              </p>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
