'use client';

// [Claude] หน้าประวัติคำสั่งซื้อ — แสดงรายการ order ทั้งหมดของ user พร้อม status และรายการสินค้า

import { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import api from '@/lib/api';
import { cancelOrder } from '@/services/auth.service';
import TaxInvoiceModal from '../../components/TaxInvoiceModal';
import ConfirmModal from '../../components/ConfirmModal';
import toast from 'react-hot-toast';

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

  // [Claude] ดึง order ทั้งหมดของ user จาก backend
  const fetchOrders = async () => {
    try {
      const res = await api.get(`/api/orders?userId=${user.id}`);
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
      await cancelOrder(confirmCancelId, user.id);
      toast.success('ยกเลิกออเดอร์สำเร็จ');
      setConfirmCancelId(null);
      fetchOrders();
    } catch (err) {
      toast.error(err?.response?.data?.message ?? 'ยกเลิกไม่สำเร็จ');
      setConfirmCancelId(null);
    }
  };

  if (loading || fetchLoading) {
    return <div className="text-center py-10 text-gray-400">กำลังโหลด...</div>;
  }

  return (
    <div className="max-w-2xl mx-auto flex flex-col gap-4">
      <h1 className="text-lg font-semibold text-gray-800">ประวัติคำสั่งซื้อ</h1>

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
        <div className="bg-white rounded-2xl shadow-sm p-10 text-center text-gray-400 text-sm">
          ยังไม่มีคำสั่งซื้อ
        </div>
      )}

      {orders.map((order) => {
        const s = getStatus(order.status);
        const isOpen = expandedId === order.id;

        return (
          <div key={order.id} className="bg-white rounded-2xl shadow-sm overflow-hidden">

            {/* ─── Header ของแต่ละ order ─── */}
            <div
              className="flex items-center justify-between px-6 py-4 cursor-pointer hover:bg-gray-50 transition-colors"
              onClick={() => toggleExpand(order.id)}
            >
              <div className="flex flex-col gap-0.5">
                <span className="text-sm font-semibold text-gray-800">#{order.orderNo}</span>
                <span className="text-xs text-gray-400">{formatDate(order.createdAt)}</span>
              </div>

              <div className="flex items-center gap-3">
                <span className={`text-xs px-2.5 py-1 rounded-full font-medium ${s.bg} ${s.text}`}>
                  {s.label}
                </span>
                <span className="text-sm font-semibold text-gray-700">
                  ฿{Number(order.totalAmount).toLocaleString('th-TH', { minimumFractionDigits: 2 })}
                </span>
                {/* [Claude] ลูกศรบอก expand state */}
                <span className={`text-gray-400 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`}>
                  ▾
                </span>
              </div>
            </div>

            {/* ─── รายละเอียดเมื่อ expand ─── */}
            {isOpen && (
              <div className="border-t border-gray-100 px-6 py-4">

                {/* ที่อยู่จัดส่ง */}
                <div className="mb-4">
                  <p className="text-xs text-gray-400 font-medium mb-1">ที่อยู่จัดส่ง</p>
                  <p className="text-sm text-gray-700">
                    {order.shippingName} · {order.shippingPhone}
                  </p>
                  <p className="text-xs text-gray-500">
                    {order.shippingAddress} {order.shippingSubdistrict} {order.shippingDistrict} {order.shippingProvince} {order.shippingPostcode}
                  </p>
                </div>

                {/* รายการสินค้า */}
                <p className="text-xs text-gray-400 font-medium mb-2">รายการสินค้า</p>
                <div className="flex flex-col gap-2 mb-4">
                  {order.orderLines?.map((line) => (
                    <div key={line.id} className="flex justify-between items-center text-sm">
                      <span className="text-gray-700">
                        {line.productName}
                        <span className="text-gray-400 ml-1">× {line.quantity}</span>
                      </span>
                      <span className="text-gray-700 font-medium">
                        ฿{Number(line.totalPrice).toLocaleString('th-TH', { minimumFractionDigits: 2 })}
                      </span>
                    </div>
                  ))}
                </div>

                {/* สรุปราคา */}
                <div className="border-t border-gray-100 pt-3 flex flex-col gap-1 text-sm">
                  <div className="flex justify-between text-gray-500">
                    <span>ราคาสินค้า</span>
                    <span>฿{Number(order.subtotal).toLocaleString('th-TH', { minimumFractionDigits: 2 })}</span>
                  </div>
                  {Number(order.discountAmount) > 0 && (
                    <div className="flex justify-between text-green-600">
                      <span>ส่วนลด {order.promotionCode && `(${order.promotionCode})`}</span>
                      <span>-฿{Number(order.discountAmount).toLocaleString('th-TH', { minimumFractionDigits: 2 })}</span>
                    </div>
                  )}
                  <div className="flex justify-between text-gray-500">
                    <span>ค่าจัดส่ง</span>
                    <span>฿{Number(order.shippingFee).toLocaleString('th-TH', { minimumFractionDigits: 2 })}</span>
                  </div>
                  <div className="flex justify-between font-semibold text-gray-800 pt-1 border-t border-gray-100">
                    <span>รวมทั้งหมด</span>
                    <span>฿{Number(order.totalAmount).toLocaleString('th-TH', { minimumFractionDigits: 2 })}</span>
                  </div>
                </div>

                {/* tracking */}
                {order.trackingNo && (
                  <p className="text-xs text-gray-400 mt-3">
                    Tracking: <span className="text-gray-600 font-medium">{order.trackingNo}</span>
                  </p>
                )}

                {/* ── Actions ── */}
                <div className="mt-4 pt-3 border-t border-gray-100 flex items-center justify-between gap-3 flex-wrap">

                  {/* ปุ่มออกใบกำกับภาษี */}
                  <button
                    onClick={() => setInvoiceOrder(order)}
                    className="flex items-center gap-1.5 text-xs text-[#0B1F33] border border-[#0B1F33] px-3 py-1.5 rounded-lg hover:bg-[#0B1F33] hover:text-white transition"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
                    </svg>
                    ออกใบกำกับภาษี
                  </button>

                  {/* ปุ่ม / ข้อความยกเลิก */}
                  {order.status === 'PENDING' && (
                    <div className="flex flex-col items-end gap-1">
                      <button
                        onClick={() => setConfirmCancelId(order.id)}
                        className="flex items-center gap-1.5 text-xs text-red-600 border border-red-300 px-3 py-1.5 rounded-lg hover:bg-red-50 transition"
                      >
                        <svg xmlns="http://www.w3.org/2000/svg" className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                        </svg>
                        ยกเลิกออเดอร์
                      </button>
                      <p className="text-[10px] text-gray-400">ยกเลิกได้ก่อนที่ร้านจะเริ่มเตรียมสินค้า</p>
                    </div>
                  )}

                  {['PROCESSING', 'SHIPPED', 'DELIVERED'].includes(order.status) && (
                    <div className="flex items-center gap-1.5 text-xs text-gray-400 bg-gray-50 border border-gray-200 px-3 py-1.5 rounded-lg">
                      <svg xmlns="http://www.w3.org/2000/svg" className="w-3.5 h-3.5 text-orange-400 shrink-0" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
                      </svg>
                      ไม่สามารถยกเลิกได้ — ร้านเปลี่ยนสถานะเป็น &ldquo;
                      {order.status === 'PROCESSING' ? 'กำลังเตรียมสินค้า' :
                       order.status === 'SHIPPED'    ? 'จัดส่งแล้ว' : 'ได้รับแล้ว'}&rdquo; แล้ว
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
