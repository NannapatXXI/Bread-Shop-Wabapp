'use client';

// [Claude] หน้าประวัติคำสั่งซื้อ — แสดงรายการ order ทั้งหมดของ user พร้อม status และรายการสินค้า

import { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import api from '@/lib/api';
import { cancelOrder } from '@/services/auth.service';
import TaxInvoiceModal from '../../components/TaxInvoiceModal';
import ConfirmModal from '../../components/ConfirmModal';
import toast from 'react-hot-toast';
import Link from 'next/link';
import Icon from '../../components/Icon';
import StatusBadge from '../../components/StatusBadge';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

// [Claude] แปลง status เป็นภาษาไทยพร้อม style สี
const statusConfig = {
  PENDING:    { label: 'รอดำเนินการ', bg: 'bg-yellow-100', text: 'text-yellow-800' },
  CONFIRMED:  { label: 'ยืนยันแล้ว',  bg: 'bg-blue-100',   text: 'text-blue-800'   },
  PROCESSING: { label: 'กำลังเตรียม', bg: 'bg-purple-100', text: 'text-purple-800' },
  SHIPPED:    { label: 'จัดส่งแล้ว',  bg: 'bg-blue-100',   text: 'text-blue-800'   },
  DELIVERED:  { label: 'สำเร็จ',      bg: 'bg-green-100',  text: 'text-green-800'  },
  CANCELLED:  { label: 'ยกเลิก',      bg: 'bg-red-100',    text: 'text-red-800'    },
  REFUNDED:   { label: 'คืนเงิน',     bg: 'bg-pink-100',   text: 'text-pink-800'   },
};

const getStatus = (status) =>
  statusConfig[status] || { label: status, bg: 'bg-gray-100', text: 'text-gray-600' };

const formatDate = (dateStr) => {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleDateString('th-TH', {
    year: 'numeric', month: 'short', day: 'numeric',
  });
};

export default function HistoryPage() {
  const { user, loading } = useAuth();
  const [orders, setOrders] = useState([]);
  const [fetchLoading, setFetchLoading] = useState(true);
  const [expandedId, setExpandedId] = useState(null);
  const [invoiceOrder, setInvoiceOrder] = useState(null);
  const [confirmCancelId, setConfirmCancelId] = useState(null);

  useEffect(() => {
    if (!user) return;
    fetchOrders();
  }, [user]);

  // [Claude] ดึง order ทั้งหมดของ user จาก backend — userId มาจาก JWT ฝั่ง server
  const fetchOrders = async () => {
    try {
      const res = await api.get('/api/v1/orders');
      // เรียงล่าสุดก่อน
      const sorted = res.data.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
      setOrders(sorted);
    } catch {
      // ไม่แสดง error ที่ซับซ้อน แค่ทำให้ list ว่าง
    } finally {
      setFetchLoading(false);
    }
  };

  // [Claude] toggle expand/collapse รายการสินค้าใน order
  const toggleExpand = (id) => {
    setExpandedId((prev) => (prev === id ? null : id));
  };

  const handleCancel = async () => {
    try {
      await cancelOrder(confirmCancelId);
      toast.success('ยกเลิกออเดอร์สำเร็จ');
      setConfirmCancelId(null);
      fetchOrders();
    } catch (err) {
      toast.error(err?.response?.data?.message ?? 'ยกเลิกไม่สำเร็จ');
      setConfirmCancelId(null);
    }
  };

  const money = (n) => Number(n).toLocaleString('th-TH', { minimumFractionDigits: 2 });

  if (loading || fetchLoading) {
    return (
      <div style={{ maxWidth: 960, margin: '0 auto' }}>
        <div className="page-head"><div><h1>ประวัติการสั่งซื้อ</h1><p>ติดตามสถานะคำสั่งซื้อของคุณ</p></div></div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="card pad" style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
              <div className="sk" style={{ width: 44, height: 44 }} />
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 8 }}>
                <div className="sk" style={{ height: 12, width: '30%' }} /><div className="sk" style={{ height: 12, width: '50%' }} />
              </div>
              <div className="sk" style={{ height: 24, width: 80, borderRadius: 12 }} />
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 960, margin: '0 auto' }}>
      <div className="page-head"><div><h1>ประวัติการสั่งซื้อ</h1><p>ติดตามสถานะคำสั่งซื้อของคุณ</p></div></div>

      {invoiceOrder && (
        <TaxInvoiceModal order={invoiceOrder} onClose={() => setInvoiceOrder(null)} />
      )}

      {confirmCancelId && (
        <ConfirmModal
          title="ยืนยันการยกเลิกออเดอร์"
          description="เมื่อยกเลิกแล้วจะไม่สามารถย้อนกลับได้ และสินค้าจะถูกคืนสต็อก"
          confirmLabel="ยกเลิกออเดอร์"
          cancelLabel="ไม่ยกเลิก"
          confirmColor="red"
          onConfirm={handleCancel}
          onCancel={() => setConfirmCancelId(null)}
        />
      )}

      {orders.length === 0 && (
        <div className="empty">
          <div className="ic"><Icon name="receipt" /></div>
          <h3>ยังไม่มีคำสั่งซื้อ</h3>
          <p>เมื่อสั่งซื้อแล้ว รายการจะแสดงที่นี่พร้อมสถานะการจัดส่ง</p>
          <Link className="btn btn-primary" href="/product">เลือกซื้อขนม</Link>
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {orders.map((order) => {
          const s = getStatus(order.status);
          const isOpen = expandedId === order.id;
          const lines = order.orderLines ?? [];
          const first = lines[0];

          return (
            <article key={order.id} className="card">
              <div className="card-head">
                <div>
                  <b className="mono" style={{ fontFamily: 'ui-monospace,Menlo,monospace', fontSize: 13, color: 'var(--blue)' }}>{order.orderNo}</b>
                  <div className="muted" style={{ fontSize: 13 }}>{formatDate(order.createdAt)}</div>
                </div>
                <StatusBadge status={order.status} label={s.label} />
              </div>
              <div className="pad" style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
                {first?.productImageUrl
                  ? <img className="thumb" src={`${API_URL}/${first.productImageUrl}`} alt={first.productName} />
                  : <span className="thumb" />}
                <div style={{ flex: 1, minWidth: 160 }}>
                  <b style={{ fontWeight: 600 }}>{first ? `${first.productName} × ${first.quantity}` : '-'}{lines.length > 1 && <span className="muted" style={{ fontWeight: 400 }}> และอีก {lines.length - 1} รายการ</span>}</b>
                  <div className="muted" style={{ fontSize: 13 }}>{lines.length} รายการ</div>
                </div>
                <b className="head num" style={{ fontSize: 18 }}>฿{money(order.totalAmount)}</b>
                <button className="btn btn-outline sm" onClick={() => toggleExpand(order.id)} aria-expanded={isOpen}>
                  {isOpen ? 'ซ่อนรายละเอียด' : 'ดูรายละเอียด'}
                </button>
              </div>

              {isOpen && (
                <div className="pad" style={{ borderTop: '1px solid var(--line)', display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <div>
                    <div className="label muted" style={{ fontWeight: 500, fontSize: 13 }}>ที่อยู่จัดส่ง</div>
                    <div>{order.shippingName} · {order.shippingPhone}</div>
                    <div className="muted" style={{ fontSize: 14 }}>
                      {order.shippingAddress} {order.shippingSubdistrict} {order.shippingDistrict} {order.shippingProvince} {order.shippingPostcode}
                    </div>
                  </div>

                  <div>
                    <div className="label muted" style={{ fontWeight: 500, fontSize: 13, marginBottom: 6 }}>รายการสินค้า</div>
                    <div className="rows">
                      {lines.map((line) => (
                        <div className="r" key={line.id}>
                          <span>{line.productName} <span className="muted">× {line.quantity}</span></span>
                          <span className="num">฿{money(line.totalPrice)}</span>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="rows" style={{ borderTop: '1px solid var(--line)', paddingTop: 12 }}>
                    <div className="r"><span className="muted">ราคาสินค้า</span><span className="num">฿{money(order.subtotal)}</span></div>
                    {Number(order.discountAmount) > 0 && (
                      <div className="r" style={{ color: 'var(--green)' }}>
                        <span>ส่วนลด {order.promotionCode && `(${order.promotionCode})`}</span>
                        <span className="num">-฿{money(order.discountAmount)}</span>
                      </div>
                    )}
                    <div className="r"><span className="muted">ค่าจัดส่ง</span><span className="num">฿{money(order.shippingFee)}</span></div>
                    <div className="r total"><span>รวมทั้งหมด</span><b className="num">฿{money(order.totalAmount)}</b></div>
                  </div>

                  {order.trackingNo && (
                    <p className="hint" style={{ margin: 0 }}>Tracking: <b style={{ color: 'var(--ink)' }}>{order.trackingNo}</b></p>
                  )}

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap', borderTop: '1px solid var(--line)', paddingTop: 16 }}>
                    <button className="btn btn-outline sm" onClick={() => setInvoiceOrder(order)}>
                      <Icon name="note" size="sm" />ออกใบกำกับภาษี
                    </button>

                    {order.status === 'PENDING' && (
                      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
                        <button className="btn btn-outline sm btn-danger-text" onClick={() => setConfirmCancelId(order.id)}>
                          <Icon name="x" size="sm" />ยกเลิกออเดอร์
                        </button>
                        <span className="faint" style={{ fontSize: 12 }}>ยกเลิกได้ก่อนที่ร้านจะเริ่มเตรียมสินค้า</span>
                      </div>
                    )}

                    {['PROCESSING', 'SHIPPED', 'DELIVERED'].includes(order.status) && (
                      <span className="hint" style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                        <Icon name="alert" size="sm" />
                        ไม่สามารถยกเลิกได้ — ร้านเปลี่ยนสถานะเป็น &ldquo;
                        {order.status === 'PROCESSING' ? 'กำลังเตรียมสินค้า' :
                         order.status === 'SHIPPED'    ? 'จัดส่งแล้ว' : 'ได้รับแล้ว'}&rdquo; แล้ว
                      </span>
                    )}
                  </div>
                </div>
              )}
            </article>
          );
        })}
      </div>
    </div>
  );
}
