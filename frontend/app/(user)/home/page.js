'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '../../context/AuthContext';
import { useCart } from '../../CartContext';
import { getProducts, getMyOrders } from '@/services/auth.service';
import Icon from '../../components/Icon';
import ProductCard, { CATEGORY_LABEL } from '../../components/ProductCard';
import StatusBadge from '../../components/StatusBadge';

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
  const { addToCart, items } = useCart();
  const getCartQty = (productId) => items.find(i => i.product.id === productId)?.quantity ?? 0;
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

  const heroImage = products.find(p => p.imageUrl)?.imageUrl;
  const categories = Object.entries(
    products.reduce((acc, p) => {
      if (!p.category) return acc;
      acc[p.category] ??= { count: 0, imageUrl: null };
      acc[p.category].count += 1;
      acc[p.category].imageUrl ??= p.imageUrl || null;
      return acc;
    }, {})
  ).slice(0, 3);

  return (
    <>
      <section className="hero" style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 24, margin: '32px 0 40px' }}>
        <div className="hero-box" style={{ background: 'var(--navy)', color: '#fff', borderRadius: 'var(--r-lg)', padding: heroImage ? '40px 300px 40px 40px' : 40, display: 'flex', flexDirection: 'column', justifyContent: 'center', minHeight: 260 }}>
          {heroImage && <img className="hero-img" src={`${API_URL}/${heroImage}`} alt="" />}
          <p style={{ color: '#aab6ca', fontSize: 14, margin: '0 0 6px' }}>{getGreeting()}, {user?.username || 'ผู้ใช้'}</p>
          <h1 style={{ margin: 0, fontSize: 36, lineHeight: 1.25, fontWeight: 600, maxWidth: 540 }}>ขนมอบใหม่ทุกเช้า พร้อมส่งถึงคุณ</h1>
          <p style={{ margin: '12px 0 24px', color: '#c5cfdd', maxWidth: 460 }}>เลือกเค้ก ขนมปัง และขนมอบที่ทำใหม่ทุกวัน แล้วสั่งให้ส่งถึงบ้าน</p>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Link className="btn btn-primary" href="/product">ดูสินค้าทั้งหมด</Link>
            <Link className="btn btn-ghost-dark" href="/history">ดูออเดอร์ของฉัน</Link>
          </div>
        </div>
        <aside className="card pad" style={{ display: 'flex', flexDirection: 'column' }} aria-label="สรุปออเดอร์">
          <h2 style={{ margin: '0 0 16px', fontSize: 16, fontWeight: 600 }}>ออเดอร์ของฉัน</h2>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div style={{ background: 'var(--bg)', borderRadius: 'var(--r-md)', padding: 16 }}>
              <b className="head num" style={{ display: 'block', fontSize: 28, lineHeight: 1.1 }}>{orders.length}</b>
              <span className="muted" style={{ fontSize: 13 }}>ทั้งหมด</span>
            </div>
            <div style={{ background: 'var(--bg)', borderRadius: 'var(--r-md)', padding: 16 }}>
              <b className="head num" style={{ display: 'block', fontSize: 28, lineHeight: 1.1, color: pendingOrders.length > 0 ? 'var(--blue)' : undefined }}>{pendingOrders.length}</b>
              <span className="muted" style={{ fontSize: 13 }}>รอดำเนินการ</span>
            </div>
          </div>
          <p className="muted" style={{ margin: 'auto 0 0', paddingTop: 16, fontSize: 13 }}>
            {pendingOrders.length > 0
              ? <>มีออเดอร์ที่รอดำเนินการ <b style={{ color: 'var(--ink)' }}>{pendingOrders.length} รายการ</b></>
              : 'ยังไม่มีออเดอร์ที่รอดำเนินการ'}
          </p>
        </aside>
      </section>

      {categories.length > 0 && (
        <nav className="cats" aria-label="หมวดหมู่">
          {categories.map(([cat, info]) => (
            <Link key={cat} className="cat" href="/product">
              {info.imageUrl ? <img src={`${API_URL}/${info.imageUrl}`} alt="" /> : <span className="ci"><Icon name="tag" /></span>}
              <span>{CATEGORY_LABEL[cat] ?? cat}<small>{info.count} รายการ</small></span>
            </Link>
          ))}
          <Link className="cat" href="/product">
            <span className="ci"><Icon name="shop" /></span>
            <span>ทั้งหมด<small>{products.length} รายการ</small></span>
          </Link>
        </nav>
      )}

      <section aria-labelledby="rec" style={{ marginBottom: 48 }}>
        <div className="sec-head">
          <h2 id="rec">สินค้าแนะนำ</h2>
          <Link className="link" href="/product" style={{ display: 'flex', gap: 4, alignItems: 'center', fontSize: 14 }}>ดูทั้งหมด <Icon name="right" size="sm" /></Link>
        </div>
        <div className="pgrid" style={{ marginTop: 20 }}>
          {loadingProducts
            ? Array.from({ length: 4 }).map((_, i) => (
              <div key={i}><div className="sk" style={{ aspectRatio: '1/1', borderRadius: 'var(--r-md)' }} /><div className="sk" style={{ height: 14, width: '60%', marginTop: 12 }} /></div>
            ))
            : products.slice(0, 4).map(product => (
              <ProductCard key={product.id} product={product} imageSrc={`${API_URL}/${product.imageUrl}`}
                inCart={getCartQty(product.id)} onAdd={addToCart} />
            ))}
        </div>
      </section>

      <section aria-labelledby="recent">
        <div className="sec-head">
          <h2 id="recent">ออเดอร์ล่าสุด</h2>
          <Link className="link" href="/history" style={{ display: 'flex', gap: 4, alignItems: 'center', fontSize: 14 }}>ดูประวัติทั้งหมด <Icon name="right" size="sm" /></Link>
        </div>
        {loadingOrders ? (
          <div className="card pad" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {Array.from({ length: 2 }).map((_, i) => <div key={i} className="sk" style={{ height: 44 }} />)}
          </div>
        ) : orders.length === 0 ? (
          <div className="empty">
            <div className="ic"><Icon name="receipt" /></div>
            <h3>ยังไม่มีออเดอร์</h3>
            <p>สั่งครั้งแรกแล้ว ออเดอร์จะแสดงที่นี่พร้อมสถานะ</p>
            <Link className="btn btn-outline" href="/product">เริ่มเลือกสินค้า</Link>
          </div>
        ) : (
          <div className="card">
            {[...orders].reverse().slice(0, 3).map((order, idx) => {
              const s = statusLabel(order.status);
              const first = order.orderLines?.[0];
              return (
                <Link key={order.id} href="/history" style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 20px', borderTop: idx ? '1px solid var(--line)' : 0 }}>
                  {first?.productImageUrl
                    ? <img className="thumb" src={`${API_URL}/${first.productImageUrl}`} alt={first.productName} />
                    : <span className="thumb" />}
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <span className="mono" style={{ fontFamily: 'ui-monospace,Menlo,monospace', fontSize: 13, color: 'var(--blue)', fontWeight: 500 }}>{order.orderNo}</span>
                    <div className="muted" style={{ fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {order.orderLines?.map(l => l.productName).join(', ')} · {order.orderLines?.length} รายการ
                    </div>
                  </div>
                  <div style={{ textAlign: 'right', display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
                    <b className="head num">฿{order.totalAmount}</b>
                    <StatusBadge status={order.status} label={s.label} />
                  </div>
                </Link>
              );
            })}
          </div>
        )}
      </section>
      <style>{`@media(max-width:1024px){.hero{grid-template-columns:1fr!important}}`}</style>
    </>
  );
}
