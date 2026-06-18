'use client';

import Link from 'next/link';
import Image from 'next/image';
import { useEffect, useState } from 'react';
import { getProducts } from "@/services/auth.service";

const API_URL = process.env.NEXT_PUBLIC_API_URL;

export default function LandingPage() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getProducts()
      .then(res => setProducts(Array.isArray(res.data) ? res.data : []))
      .catch(() => setProducts([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="min-h-screen bg-[#EEF4FB] font-sans">

      {/* ── Navbar ─────────────────────────────────────────── */}
      <nav className="bg-[#0B1F33] sticky top-0 z-50 h-16 flex items-center justify-between px-4 md:px-8">
        <div className="flex items-center gap-2.5">
          <Image src="/logo.png" alt="Peak Pung Logo" width={38} height={38}
            className="rounded-full object-cover flex-shrink-0" />
          <div className="flex flex-col justify-center mt-1">
            <span className="text-white text-sm font-bold leading-tight">Peak Pung</span>
            <span className="text-[#8ba6ca] text-[11px] leading-tight">by Mom Hmee</span>
          </div>
        </div>
        <div className="flex gap-2">
          <Link href="/login">
            <button className="border border-[#2a4a6a] text-[#8ba6ca] px-3 md:px-4 py-1.5 rounded-lg text-xs md:text-sm cursor-pointer bg-transparent">
              เข้าสู่ระบบ
            </button>
          </Link>
          <Link href="/register">
            <button className="bg-[#A8CEFF] text-[#0B1F33] px-3 md:px-4 py-1.5 rounded-lg text-xs md:text-sm font-semibold cursor-pointer border-none">
              สมัครสมาชิก
            </button>
          </Link>
        </div>
      </nav>

      {/* ── Hero ───────────────────────────────────────────── */}
      <div className="bg-[#0B1F33] px-5 py-12 md:py-16 text-center relative overflow-hidden">
        {/* decorative circles */}
        <div className="absolute -top-14 -right-14 w-56 h-56 rounded-full bg-[rgba(58,123,213,0.12)] pointer-events-none" />
        <div className="absolute -bottom-10 -left-10 w-44 h-44 rounded-full bg-[rgba(168,206,255,0.07)] pointer-events-none" />

        <p className="text-[#8ba6ca] text-[11px] tracking-widest mb-2.5">PRE-ORDER BAKERY</p>
        <h1 className="text-white text-2xl md:text-4xl font-semibold leading-snug mb-3">
          สั่งทำขนมปังสดใหม่<br />
          <span className="text-[#A8CEFF]">ตามแบบที่คุณต้องการ</span>
        </h1>
        <p className="text-[#5a7a9a] text-sm max-w-sm mx-auto leading-relaxed mb-7">
          อบสดทุกวัน รับออเดอร์ล่วงหน้า 1-2 วัน เลือกไส้และขนาดได้เอง ส่งตรงถึงมือคุณหรือรับที่ร้าน
        </p>

        <div className="flex gap-2.5 justify-center flex-wrap">
          <Link href="/login">
            <button className="bg-[#A8CEFF] text-[#0B1F33] px-6 py-3 rounded-xl text-sm font-semibold cursor-pointer border-none">
              สั่งซื้อเลย →
            </button>
          </Link>
          <a href="#products">
            <button className="bg-transparent border border-[#2a4a6a] text-[#8ba6ca] px-6 py-3 rounded-xl text-sm cursor-pointer">
              ดูสินค้า
            </button>
          </a>
        </div>

        {/* Stats */}
        <div className="flex justify-center gap-6 md:gap-8 mt-8 flex-wrap">
          {[
            { value: '50+', label: 'เมนูให้เลือก' },
            { value: 'อบสด', label: 'ทุกวัน' },
            { value: '1-2 วัน', label: 'สั่งล่วงหน้า' },
          ].map((s, i) => (
            <div key={i} className="flex items-center gap-6 md:gap-8">
              {i > 0 && <div className="w-px h-7 bg-[#1a3a5c]" />}
              <div className="text-center">
                <p className="text-white text-lg md:text-xl font-semibold m-0">{s.value}</p>
                <p className="text-[#5a7a9a] text-[11px] mt-1 m-0">{s.label}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* ── Products ───────────────────────────────────────── */}
      <div id="products" className="px-4 md:px-6 py-7">
        <div className="flex justify-between items-center mb-4">
          <div>
            <h2 className="text-lg md:text-xl font-semibold text-[#0B1F33] m-0">สินค้าแนะนำ</h2>
            <p className="text-[#8ba6ca] text-xs mt-1 m-0">เลือกและสั่งล่วงหน้าได้เลย</p>
          </div>
          <Link href="/login">
            <span className="text-xs text-[#378ADD] cursor-pointer">ดูทั้งหมด →</span>
          </Link>
        </div>

        {loading ? (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="animate-pulse bg-white rounded-2xl overflow-hidden border border-[#dce8f0]">
                <div className="h-32 md:h-44 bg-[#e8f0f8]" />
                <div className="p-3 space-y-2">
                  <div className="h-3 bg-[#e8f0f8] rounded w-3/4" />
                  <div className="flex justify-between items-center pt-1">
                    <div className="h-3 bg-[#e8f0f8] rounded w-1/3" />
                    <div className="h-6 w-12 bg-[#e8f0f8] rounded" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {products.slice(0, 4).map(product => (
              <div key={product.id} className="bg-white rounded-2xl overflow-hidden border border-[#dce8f0]">
                <div className="h-32 md:h-44 bg-[#f0f4f8] relative overflow-hidden">
                  {product.imageUrl ? (
                    <img src={`${API_URL}/${product.imageUrl}`} alt={product.name}
                      className="w-full h-full object-cover" />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center text-4xl">🍞</div>
                  )}
                  {product.stock === 0 && (
                    <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
                      <span className="text-white text-xs font-semibold">หมดแล้ว</span>
                    </div>
                  )}
                </div>
                <div className="p-2.5 md:p-3">
                  <p className="text-xs md:text-sm font-semibold text-[#0B1F33] mb-1 truncate">{product.name}</p>
                  <p className="text-[10px] md:text-xs text-[#8ba6ca] mb-2 truncate">
                    {product.description || product.category}
                  </p>
                  <div className="flex items-center justify-between">
                    <span className="text-sm md:text-base font-semibold text-[#0B1F33]">฿{product.price}</span>
                    <Link href="/login">
                      <button className="bg-[#0B1F33] text-[#A8CEFF] px-2 md:px-2.5 py-1 rounded-lg text-[10px] md:text-xs cursor-pointer border-none">
                        + เพิ่ม
                      </button>
                    </Link>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ── How to order ───────────────────────────────────── */}
      <div className="px-4 md:px-6 pb-8">
        <div className="bg-[#0B1F33] rounded-2xl p-5 md:p-7 flex flex-col md:flex-row md:items-center md:justify-between gap-5">
          <div className="flex-1">
            <p className="text-[#A8CEFF] text-xs tracking-wider mb-1.5">วิธีสั่งซื้อ Pre-order</p>
            <h3 className="text-white text-base md:text-lg font-semibold mb-4">ง่ายแค่ 3 ขั้นตอน</h3>
            <div className="flex flex-col gap-3">
              {[
                'เลือกสินค้าและกำหนดวันรับ',
                'ชำระเงินและยืนยันออเดอร์',
                'รับขนมปังอบสดในวันที่นัด',
              ].map((step, i) => (
                <div key={i} className="flex items-center gap-3">
                  <div className="w-6 h-6 bg-[#1a3a5c] rounded-full flex items-center justify-center flex-shrink-0">
                    <span className="text-[#A8CEFF] text-[11px] font-semibold">{i + 1}</span>
                  </div>
                  <span className="text-[#8ba6ca] text-sm">{step}</span>
                </div>
              ))}
            </div>
          </div>
          <Link href="/register" className="flex-shrink-0">
            <button className="w-full md:w-auto bg-[#A8CEFF] text-[#0B1F33] px-6 py-3 rounded-xl text-sm font-semibold cursor-pointer border-none whitespace-nowrap">
              สมัครและสั่งซื้อเลย →
            </button>
          </Link>
        </div>
      </div>

      {/* ── Footer ─────────────────────────────────────────── */}
      <div className="bg-[#0B1F33] py-5 text-center">
        <p className="text-[#5a7a9a] text-xs m-0">© 2026 BreadShop · ขนมปังอบสด Pre-order</p>
      </div>

    </div>
  );
}
