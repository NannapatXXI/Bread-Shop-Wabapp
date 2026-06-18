'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '../../context/AuthContext';
import { getProducts, getMyOrders } from '@/services/auth.service';

const API_URL = process.env.NEXT_PUBLIC_API_URL;

const getGreeting = () => {
  const h = new Date().getHours();
  if (h < 12) return 'สวัสดีตอนเช้า';
  if (h < 17) return 'สวัสดีตอนบ่าย';
  return 'สวัสดีตอนเย็น';
};

const statusLabel = (status) => {
  const map = {
    PENDING:    { label: 'รอดำเนินการ', bg: '#fef9c3', color: '#854d0e' },
    CONFIRMED:  { label: 'ยืนยันแล้ว',  bg: '#dbeafe', color: '#1e40af' },
    PROCESSING: { label: 'กำลังเตรียม', bg: '#ede9fe', color: '#5b21b6' },
    SHIPPED:    { label: 'จัดส่งแล้ว',  bg: '#dbeafe', color: '#1e40af' },
    DELIVERED:  { label: 'สำเร็จ',      bg: '#dcfce7', color: '#166534' },
    CANCELLED:  { label: 'ยกเลิก',      bg: '#fee2e2', color: '#991b1b' },
    REFUNDED:   { label: 'คืนเงิน',     bg: '#fce7f3', color: '#9d174d' },
  };
  return map[status] || { label: status, bg: '#f1f5f9', color: '#475569' };
};

export default function HomePage() {
  const { user } = useAuth();
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [loadingProducts, setLoadingProducts] = useState(true);
  const [loadingOrders, setLoadingOrders] = useState(true);

  useEffect(() => {
    getProducts()
      .then(res => setProducts(res.data))
      .catch(() => {})
      .finally(() => setLoadingProducts(false));
  }, []);

  // โหลด orders เมื่อ user พร้อม
  useEffect(() => {
    if (!user) return;
    getMyOrders()
      .then(res => setOrders(res.data ?? []))
      .catch(() => {})
      .finally(() => setLoadingOrders(false));
  }, [user]);

  const pendingOrders = orders.filter(o => o.status === 'PENDING' || o.status === 'PROCESSING' || o.status === 'CONFIRMED');
  const latestOrder = [...orders].reverse()[0];

  return (
    <div style={{ minHeight: '100vh', background: '#EEF4FB', padding: '1.25rem 1.5rem', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>

      {/* Welcome card */}
      <div style={{ background: '#0B1F33', borderRadius: '14px', padding: '1.25rem 1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <p style={{ color: '#8ba6ca', fontSize: '12px', margin: '0 0 4px' }}>{getGreeting()}</p>
          <h2 style={{ color: 'white', fontSize: '18px', fontWeight: 500, margin: '0 0 6px' }}>
            {user?.username || 'ผู้ใช้'} 👋
          </h2>
          {pendingOrders.length > 0 ? (
            <p style={{ color: '#5a7a9a', fontSize: '13px', margin: 0 }}>
              มีออเดอร์ที่รอดำเนินการ <span style={{ color: '#A8CEFF', fontWeight: 500 }}>{pendingOrders.length} รายการ</span>
            </p>
          ) : (
            <p style={{ color: '#5a7a9a', fontSize: '13px', margin: 0 }}>ยังไม่มีออเดอร์ที่รอดำเนินการ</p>
          )}
        </div>
        <div style={{ display: 'flex', gap: '1.5rem' }}>
          <div style={{ textAlign: 'center' }}>
            <p style={{ color: 'white', fontSize: '22px', fontWeight: 500, margin: 0 }}>{orders.length}</p>
            <p style={{ color: '#5a7a9a', fontSize: '11px', margin: '4px 0 0' }}>ออเดอร์ทั้งหมด</p>
          </div>
          <div style={{ width: '1px', background: '#1a3a5c' }} />
          <div style={{ textAlign: 'center' }}>
            <p style={{ color: '#A8CEFF', fontSize: '22px', fontWeight: 500, margin: 0 }}>{pendingOrders.length}</p>
            <p style={{ color: '#5a7a9a', fontSize: '11px', margin: '4px 0 0' }}>รอดำเนินการ</p>
          </div>
        </div>
      </div>

      {/* Latest order alert */}
      {latestOrder && (
        <div style={{ background: 'white', borderRadius: '12px', border: '0.5px solid #dce8f0', padding: '1rem 1.25rem', display: 'flex', alignItems: 'center', gap: '12px' }}>
          {/* รูปสินค้าแรกในออเดอร์ */}
          <div style={{ width: '44px', height: '44px', borderRadius: '8px', overflow: 'hidden', flexShrink: 0, background: '#EEF4FB' }}>
            {latestOrder.orderLines?.[0]?.productImageUrl ? (
              <img
                src={`${API_URL}/${latestOrder.orderLines[0].productImageUrl}`}
                alt={latestOrder.orderLines[0].productName}
                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
            ) : (
              <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <span style={{ fontSize: '20px' }}>🍞</span>
              </div>
            )}
          </div>
          <div style={{ flex: 1 }}>
            <p style={{ fontSize: '13px', fontWeight: 500, color: '#0B1F33', margin: '0 0 2px' }}>
              {latestOrder.orderNo} · {statusLabel(latestOrder.status).label}
            </p>
            <p style={{ fontSize: '12px', color: '#8ba6ca', margin: 0 }}>
              {latestOrder.orderLines?.map(l => l.productName).join(', ')} · ฿{latestOrder.totalAmount}
            </p>
          </div>
          <Link href="/history">
            <span style={{ fontSize: '12px', color: '#378ADD', cursor: 'pointer', whiteSpace: 'nowrap' }}>ดูรายละเอียด →</span>
          </Link>
        </div>
      )}

      {/* Products */}
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
          <h3 style={{ fontSize: '16px', fontWeight: 500, color: '#0B1F33', margin: 0 }}>สินค้าแนะนำ</h3>
          <Link href="/product">
            <span style={{ fontSize: '12px', color: '#378ADD', cursor: 'pointer' }}>ดูทั้งหมด →</span>
          </Link>
        </div>

        {loadingProducts ? (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '10px' }}>
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} style={{ background: 'white', borderRadius: '10px', overflow: 'hidden', border: '0.5px solid #dce8f0' }}>
                <div style={{ height: '100px', background: '#e8f0f8' }} className="animate-pulse" />
                <div style={{ padding: '8px 10px' }}>
                  <div style={{ height: '12px', background: '#e8f0f8', borderRadius: '4px', marginBottom: '8px', width: '70%' }} className="animate-pulse" />
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ height: '12px', background: '#e8f0f8', borderRadius: '4px', width: '35%' }} className="animate-pulse" />
                    <div style={{ height: '22px', width: '44px', background: '#e8f0f8', borderRadius: '5px' }} className="animate-pulse" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '10px' }}>
            {products.slice(0, 4).map(product => (
              <div key={product.id} style={{ background: 'white', borderRadius: '10px', overflow: 'hidden', border: '0.5px solid #dce8f0' }}>
                <div style={{ height: '100px', background: '#f0f4f8', overflow: 'hidden', position: 'relative' }}>
                  {product.imageUrl ? (
                    <img src={`${API_URL}/${product.imageUrl}`} alt={product.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  ) : (
                    <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <span style={{ fontSize: '32px' }}>🍞</span>
                    </div>
                  )}
                  {product.stock === 0 && (
                    <div style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <span style={{ color: 'white', fontSize: '11px', fontWeight: 500 }}>หมดแล้ว</span>
                    </div>
                  )}
                </div>
                <div style={{ padding: '8px 10px' }}>
                  <p style={{ fontSize: '12px', fontWeight: 500, color: '#0B1F33', margin: '0 0 6px' }}>{product.name}</p>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: '13px', fontWeight: 500, color: '#0B1F33' }}>฿{product.price}</span>
                    <button
                      disabled={product.stock === 0}
                      style={{ background: product.stock === 0 ? '#e2e8f0' : '#0B1F33', border: 'none', color: product.stock === 0 ? '#94a3b8' : '#A8CEFF', padding: '3px 8px', borderRadius: '5px', fontSize: '11px', cursor: product.stock === 0 ? 'not-allowed' : 'pointer' }}
                    >
                      + เพิ่ม
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Recent orders */}
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
          <h3 style={{ fontSize: '16px', fontWeight: 500, color: '#0B1F33', margin: 0 }}>ออเดอร์ล่าสุด</h3>
          <Link href="/history">
            <span style={{ fontSize: '12px', color: '#378ADD', cursor: 'pointer' }}>ดูทั้งหมด →</span>
          </Link>
        </div>

        {loadingOrders ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {Array.from({ length: 2 }).map((_, i) => (
              <div key={i} className="animate-pulse" style={{ background: 'white', borderRadius: '10px', border: '0.5px solid #dce8f0', padding: '0.875rem 1rem', display: 'flex', alignItems: 'center', gap: '12px' }}>
                <div style={{ width: '40px', height: '40px', borderRadius: '8px', background: '#e8f0f8', flexShrink: 0 }} />
                <div style={{ flex: 1 }}>
                  <div style={{ height: '12px', background: '#e8f0f8', borderRadius: '4px', width: '40%', marginBottom: '6px' }} />
                  <div style={{ height: '11px', background: '#e8f0f8', borderRadius: '4px', width: '60%' }} />
                </div>
                <div style={{ height: '22px', width: '64px', background: '#e8f0f8', borderRadius: '12px' }} />
              </div>
            ))}
          </div>
        ) : orders.length === 0 ? (
          <div style={{ background: 'white', borderRadius: '10px', border: '0.5px solid #dce8f0', padding: '2rem', textAlign: 'center' }}>
            <p style={{ color: '#8ba6ca', fontSize: '13px', margin: 0 }}>ยังไม่มีออเดอร์</p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {[...orders].reverse().slice(0, 3).map(order => {
              const s = statusLabel(order.status);
              return (
                <div key={order.id} style={{ background: 'white', borderRadius: '10px', border: '0.5px solid #dce8f0', padding: '0.875rem 1rem', display: 'flex', alignItems: 'center', gap: '12px' }}>
                  {/* รูปสินค้าแรกในออเดอร์ */}
                  <div style={{ width: '40px', height: '40px', borderRadius: '8px', overflow: 'hidden', flexShrink: 0, background: '#EEF4FB' }}>
                    {order.orderLines?.[0]?.productImageUrl ? (
                      <img
                        src={`${API_URL}/${order.orderLines[0].productImageUrl}`}
                        alt={order.orderLines[0].productName}
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      />
                    ) : (
                      <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <span style={{ fontSize: '18px' }}>🍞</span>
                      </div>
                    )}
                  </div>
                  <div style={{ flex: 1 }}>
                    <p style={{ fontSize: '13px', fontWeight: 500, color: '#0B1F33', margin: '0 0 2px' }}>{order.orderNo}</p>
                    <p style={{ fontSize: '11px', color: '#8ba6ca', margin: 0 }}>
                      {order.orderLines?.map(l => l.productName).join(', ')} · {order.orderLines?.length} รายการ
                    </p>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <p style={{ fontSize: '13px', fontWeight: 500, color: '#0B1F33', margin: '0 0 4px' }}>฿{order.totalAmount}</p>
                    <span style={{ fontSize: '11px', background: s.bg, color: s.color, padding: '2px 8px', borderRadius: '20px' }}>{s.label}</span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

    </div>
  );
}