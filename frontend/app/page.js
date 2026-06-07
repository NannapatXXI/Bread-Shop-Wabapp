'use client';

import Link from 'next/link';
import Image from 'next/image';
import { useEffect, useState } from 'react';
import api from '@/lib/api';
import { getProducts } from "@/services/auth.service";

const API_URL = process.env.NEXT_PUBLIC_API_URL;

export default function LandingPage() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchProducts();
  }, []);


  const fetchProducts = async () => {
    try {
      const res = await getProducts();
      // interceptor unwrap แล้ว: res.data คือ array ตรงๆ
      setProducts(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      // ถ้าโหลดสินค้าไม่ได้ (backend ยังไม่ start / network error)
      // ให้แสดงหน้าปกติโดยไม่มีสินค้า ห้าม redirect ออกจาก landing page
      console.warn("[LandingPage] ไม่สามารถโหลดสินค้าได้:", err?.message ?? err);
      setProducts([]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', background: '#EEF4FB', fontFamily: 'sans-serif' }}>

      {/* Navbar */}
      <nav style={{
        background: '#0B1F33', padding: '0 2rem', height: '64px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        position: 'sticky', top: 0, zIndex: 50,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Image src="/logo.png" alt="Peak Pung Logo" width={40} height={40} style={{ objectFit: 'cover', borderRadius: '50%', flexShrink: 0 }} />
          <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', marginTop: '6px' }}>
            <span style={{ color: 'white', fontSize: '14px', fontWeight: 700, lineHeight: 1.2 }}>Peak Pung</span>
            <span style={{ color: '#8ba6ca', fontSize: '11px', lineHeight: 1.2 }}>by Mom Hmee</span>
          </div>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <Link href="/login">
            <button style={{
              background: 'none', border: '1px solid #2a4a6a', color: '#8ba6ca',
              padding: '7px 16px', borderRadius: '8px', fontSize: '13px', cursor: 'pointer',
            }}>
              เข้าสู่ระบบ
            </button>
          </Link>
          <Link href="/register">
            <button style={{
              background: '#A8CEFF', border: 'none', color: '#0B1F33',
              padding: '7px 16px', borderRadius: '8px', fontSize: '13px',
              fontWeight: 600, cursor: 'pointer',
            }}>
              สมัครสมาชิก
            </button>
          </Link>
        </div>
      </nav>

      {/* Hero */}
      <div style={{
        background: '#0B1F33', padding: '4rem 2rem', textAlign: 'center',
        position: 'relative', overflow: 'hidden',
      }}>
        <div style={{
          position: 'absolute', top: '-60px', right: '-60px', width: '250px', height: '250px',
          borderRadius: '50%', background: 'rgba(58,123,213,0.12)', pointerEvents: 'none',
        }} />
        <div style={{
          position: 'absolute', bottom: '-40px', left: '-40px', width: '180px', height: '180px',
          borderRadius: '50%', background: 'rgba(168,206,255,0.07)', pointerEvents: 'none',
        }} />

        <p style={{ color: '#8ba6ca', fontSize: '12px', letterSpacing: '0.12em', margin: '0 0 10px' }}>
          PRE-ORDER BAKERY
        </p>
        <h1 style={{
          color: 'white', fontSize: '32px', fontWeight: 600,
          margin: '0 0 12px', lineHeight: 1.3,
        }}>
          สั่งทำขนมปังสดใหม่<br />
          <span style={{ color: '#A8CEFF' }}>ตามแบบที่คุณต้องการ</span>
        </h1>
        <p style={{
          color: '#5a7a9a', fontSize: '14px', margin: '0 auto 2rem',
          maxWidth: '440px', lineHeight: 1.7,
        }}>
          อบสดทุกวัน รับออเดอร์ล่วงหน้า 1-2 วัน เลือกไส้และขนาดได้เอง
          ส่งตรงถึงมือคุณหรือรับที่ร้าน
        </p>

        <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', flexWrap: 'wrap' }}>
          <Link href="/login">
            <button style={{
              background: '#A8CEFF', border: 'none', color: '#0B1F33',
              padding: '12px 28px', borderRadius: '10px', fontSize: '14px',
              fontWeight: 600, cursor: 'pointer',
            }}>
              สั่งซื้อเลย →
            </button>
          </Link>
          <a href="#products">
            <button style={{
              background: 'none', border: '1px solid #2a4a6a', color: '#8ba6ca',
              padding: '12px 28px', borderRadius: '10px', fontSize: '14px', cursor: 'pointer',
            }}>
              ดูสินค้า
            </button>
          </a>
        </div>

        {/* Stats */}
        <div style={{
          display: 'flex', gap: '2rem', justifyContent: 'center',
          marginTop: '2.5rem', flexWrap: 'wrap',
        }}>
          {[
            { value: '50+', label: 'เมนูให้เลือก' },
            { value: 'อบสด', label: 'ทุกวัน' },
            { value: '1-2 วัน', label: 'สั่งล่วงหน้า' },
          ].map((s, i) => (
            <div key={i} style={{ textAlign: 'center', display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
              {i > 0 && <div style={{ width: '1px', height: '30px', background: '#1a3a5c' }} />}
              <div>
                <p style={{ color: 'white', fontSize: '20px', fontWeight: 600, margin: 0 }}>{s.value}</p>
                <p style={{ color: '#5a7a9a', fontSize: '12px', margin: '4px 0 0' }}>{s.label}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Products */}
      <div id="products" style={{ padding: '2rem 1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <div>
            <h2 style={{ fontSize: '20px', fontWeight: 600, color: '#0B1F33', margin: 0 }}>สินค้าแนะนำ</h2>
            <p style={{ fontSize: '13px', color: '#8ba6ca', margin: '4px 0 0' }}>เลือกและสั่งล่วงหน้าได้เลย</p>
          </div>
          <Link href="/login">
            <span style={{ fontSize: '13px', color: '#378ADD', cursor: 'pointer' }}>ดูทั้งหมด →</span>
          </Link>
        </div>

        {loading ? (
          <p style={{ textAlign: 'center', color: '#8ba6ca', padding: '2rem' }}>กำลังโหลด...</p>
        ) : (
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(4, 1fr)',  // ← 4 คอลัมน์พอดี
            gap: '12px',
          }}>
            {products.slice(0, 4).map(product => (
              <div key={product.id} style={{
                background: 'white', borderRadius: '14px',
                overflow: 'hidden', border: '0.5px solid #dce8f0',
              }}>
                {/* รูปสินค้า */}
                <div style={{
                  height: '180px', background: '#f0f4f8',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  overflow: 'hidden', position: 'relative',
                }}>
                  {product.imageUrl ? (
                    <img
                      src={`${API_URL}/${product.imageUrl}`}
                      alt={product.name}
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                  ) : (
                    <span style={{ fontSize: '40px' }}>🍞</span>
                  )}
                  {product.stock === 0 && (
                    <div style={{
                      position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.4)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                    }}>
                      <span style={{ color: 'white', fontSize: '12px', fontWeight: 600 }}>หมดแล้ว</span>
                    </div>
                  )}
                </div>

                <div style={{ padding: '10px 12px' }}>
                  <p style={{ fontSize: '13px', fontWeight: 600, color: '#0B1F33', margin: '0 0 2px' }}>
                    {product.name}
                  </p>
                  <p style={{ fontSize: '11px', color: '#8ba6ca', margin: '0 0 8px' }}>
                    {product.description || product.category}
                  </p>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: '15px', fontWeight: 600, color: '#0B1F33' }}>
                      ฿{product.price}
                    </span>
                    <Link href="/login">
                      <button style={{
                        background: '#0B1F33', border: 'none', color: '#A8CEFF',
                        padding: '5px 10px', borderRadius: '7px', fontSize: '12px', cursor: 'pointer',
                      }}>
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

      {/* How to order */}
      <div style={{ padding: '0 1.5rem 2rem' }}>
        <div style={{
          background: '#0B1F33', borderRadius: '16px', padding: '1.75rem',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          flexWrap: 'wrap', gap: '1.25rem',
        }}>
          <div style={{ minWidth: '280px', flex: 1 }}>
            <p style={{ color: '#A8CEFF', fontSize: '12px', margin: '0 0 6px', letterSpacing: '0.05em' }}>
              วิธีสั่งซื้อ Pre-order
            </p>
            <h3 style={{ color: 'white', fontSize: '17px', fontWeight: 600, margin: '0 0 1rem' }}>
              ง่ายแค่ 3 ขั้นตอน
            </h3>
            <div style={{
        display: 'flex',
        flexDirection: 'row',   // แนวนอนตามปกติ
        flexWrap: 'wrap',       // ถ้าพื้นที่น้อย ให้ตัดบรรทัด
        gap: '8px 16px',
      }}>
              {[
                'เลือกสินค้าและกำหนดวันรับ',
                'ชำระเงินและยืนยันออเดอร์',
                'รับขนมปังอบสดในวันที่นัด',
              ].map((step, i) => (
                <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <div style={{
                    width: '22px', height: '22px', background: '#1a3a5c',
                    borderRadius: '50%', display: 'flex', alignItems: 'center',
                    justifyContent: 'center', flexShrink: 0,
                  }}>
                    <span style={{ color: '#A8CEFF', fontSize: '11px', fontWeight: 600 }}>{i + 1}</span>
                  </div>
                  <span style={{ color: '#8ba6ca', fontSize: '13px' }}>{step}</span>
                </div>
              ))}
            </div>
          </div>
          <Link href="/register">
            <button style={{
              background: '#A8CEFF', border: 'none', color: '#0B1F33',
              padding: '12px 24px', borderRadius: '10px', fontSize: '14px',
              fontWeight: 600, cursor: 'pointer', whiteSpace: 'nowrap',
            }}>
              สมัครและสั่งซื้อเลย →
            </button>
          </Link>
        </div>
      </div>

      {/* Footer */}
      <div style={{
        background: '#0B1F33', padding: '1.5rem 2rem',
        textAlign: 'center',
      }}>
        <p style={{ color: '#5a7a9a', fontSize: '12px', margin: 0 }}>
          © 2026 BreadShop · ขนมปังอบสด Pre-order
        </p>
      </div>

    </div>
  );
}