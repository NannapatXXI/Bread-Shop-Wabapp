'use client';

import { useEffect, useState, useMemo } from 'react';
import toast from 'react-hot-toast';
import { adminGetAllOrders, adminUpdateOrderStatus } from '@/services/auth.service';
import { exportCSV } from '@/lib/exportCSV';
import TaxInvoiceModal from '@/app/components/TaxInvoiceModal';
import { SkeletonRows } from '@/app/components/Skeleton';

// ── Config สถานะ ────────────────────────────────────────────
const STATUS_CONFIG = {
  PENDING:    { label: 'รอดำเนินการ', dot: 'bg-orange-400', badge: 'text-orange-600 bg-orange-50',  card: 'border-orange-400',  text: 'text-orange-500' },
  PROCESSING: { label: 'กำลังเตรียม', dot: 'bg-blue-400',   badge: 'text-blue-600   bg-blue-50',    card: 'border-blue-400',    text: 'text-blue-500'   },
  SHIPPED:    { label: 'จัดส่งแล้ว',  dot: 'bg-purple-400', badge: 'text-purple-600 bg-purple-50',  card: 'border-purple-400',  text: 'text-purple-500' },
  DELIVERED:  { label: 'ได้รับแล้ว',  dot: 'bg-green-400',  badge: 'text-green-600  bg-green-50',   card: 'border-green-400',   text: 'text-green-500'  },
  CANCELLED:  { label: 'ยกเลิก',      dot: 'bg-red-400',    badge: 'text-red-600    bg-red-50',     card: 'border-red-400',     text: 'text-red-500'    },
};
const ALL_STATUSES = ['PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
const PAGE_SIZE = 7;

export default function AdminOrdersPage() {
  const [orders,        setOrders]        = useState([]);
  const [loading,       setLoading]       = useState(true);
  const [filterStatus,  setFilterStatus]  = useState('ALL');
  const [search,        setSearch]        = useState('');
  const [page,          setPage]          = useState(1);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [newStatus,     setNewStatus]     = useState('');
  const [trackingNo,    setTrackingNo]    = useState('');
  const [saving,        setSaving]        = useState(false);
  const [invoiceOrder,  setInvoiceOrder]  = useState(null);

  useEffect(() => { fetchOrders(); }, []);

  const fetchOrders = async () => {
    try {
      const res = await adminGetAllOrders();
      setOrders(res.data ?? []);
    } catch { toast.error('โหลด orders ไม่สำเร็จ'); }
    finally  { setLoading(false); }
  };

  // ── Filter + Search ──────────────────────────────────────
  const filtered = useMemo(() => {
    return orders.filter(o => {
      const matchStatus = filterStatus === 'ALL' || o.status === filterStatus;
      const q = search.toLowerCase();
      const matchSearch = !q ||
        o.orderNo?.toLowerCase().includes(q) ||
        o.shippingName?.toLowerCase().includes(q) ||
        o.shippingPhone?.includes(q);
      return matchStatus && matchSearch;
    });
  }, [orders, filterStatus, search]);

  // ── Pagination ───────────────────────────────────────────
  const totalPages  = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const paginated   = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  // reset หน้า 1 เมื่อ filter เปลี่ยน
  useEffect(() => { setPage(1); }, [filterStatus, search]);

  // ── Modal ────────────────────────────────────────────────
  const openModal = (order) => {
    setSelectedOrder(order);
    setNewStatus(order.status);
    setTrackingNo(order.trackingNo ?? '');
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await adminUpdateOrderStatus(selectedOrder.id, newStatus, trackingNo.trim() || undefined);
      toast.success('อัปเดตสถานะสำเร็จ');
      setSelectedOrder(null);
      fetchOrders();
    } catch (err) {
      toast.error(err?.message ?? 'อัปเดตไม่สำเร็จ');
    } finally { setSaving(false); }
  };

  // ── Date format ──────────────────────────────────────────
  const fmtDate = (dt) => {
    if (!dt) return { date: '-', time: '' };
    const d = new Date(dt);
    const date = d.toLocaleDateString('th-TH', { day: '2-digit', month: '2-digit', year: '2-digit' });
    const time = d.toLocaleTimeString('th-TH', { hour: '2-digit', minute: '2-digit' });
    return { date, time };
  };

  // ── Stats cards ──────────────────────────────────────────
  const statCards = [
    { key: 'ALL',       label: 'ออเดอร์ทั้งหมด', count: orders.length,                              color: 'border-indigo-500', text: 'text-indigo-600' },
    ...ALL_STATUSES.map(s => ({
      key: s, label: STATUS_CONFIG[s].label,
      count: orders.filter(o => o.status === s).length,
      color: STATUS_CONFIG[s].card,
      text:  STATUS_CONFIG[s].text,
    })),
  ];

  return (
    <div className="bg-[#EEF4FB] min-h-screen p-6">

      {/* ── Header ──────────────────────────────────────── */}
      <div className="flex flex-wrap items-start justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">Order Management</h1>
          <p className="text-gray-500 text-sm mt-0.5">จัดการคำสั่งซื้อและอัปเดตสถานะการจัดส่ง</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => exportCSV(
              filtered,
              ['เลขออเดอร์', 'ชื่อผู้รับ', 'เบอร์โทร', 'ที่อยู่', 'จังหวัด', 'ยอดรวม', 'สถานะ', 'Tracking', 'หมายเหตุ', 'วันที่สั่ง'],
              ['orderNo', 'shippingName', 'shippingPhone', 'shippingAddress', 'shippingProvince', 'totalAmount', 'status', 'trackingNo', 'note', 'createdAt'],
              'orders'
            )}
            className="flex items-center gap-2 px-4 py-2 border border-gray-300 rounded-xl text-sm font-medium text-gray-600 hover:bg-gray-50 transition bg-white">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            Export
          </button>

        </div>
      </div>

      {/* ── Stats Cards ─────────────────────────────────── */}
      <div className="grid grid-cols-3 md:grid-cols-6 gap-3 mb-5">
        {statCards.map(card => (
          <button key={card.key}
            onClick={() => { setFilterStatus(card.key); setPage(1); }}
            className={`bg-white rounded-xl px-4 pt-4 pb-3 text-left shadow-sm border-b-4 transition hover:shadow-md
              ${filterStatus === card.key ? card.color + ' shadow-md' : 'border-transparent'}`}>
            <p className={`text-2xl font-bold ${card.text}`}>{card.count}</p>
            <p className="text-xs text-gray-500 mt-1 leading-tight">{card.label}</p>
          </button>
        ))}
      </div>

      {/* ── Filter Bar ──────────────────────────────────── */}
      <div className="bg-white rounded-xl shadow-sm px-4 py-3 mb-4 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
        {/* Status tabs — scroll on mobile */}
        <div className="flex gap-1 overflow-x-auto flex-1 min-w-0">
          {[{ key: 'ALL', label: 'ทั้งหมด' }, ...ALL_STATUSES.map(s => ({ key: s, label: STATUS_CONFIG[s].label }))].map(t => (
            <button key={t.key}
              onClick={() => { setFilterStatus(t.key); setPage(1); }}
              className={`px-3 py-1.5 rounded-lg text-sm font-medium transition whitespace-nowrap shrink-0
                ${filterStatus === t.key
                  ? 'bg-[#0B1F33] text-white'
                  : 'text-gray-500 hover:bg-gray-100'}`}>
              {t.label}
            </button>
          ))}
        </div>

        {/* Search */}
        <div className="relative shrink-0">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <input value={search} onChange={e => setSearch(e.target.value)}
            placeholder="ค้นหาออเดอร์หรือลูกค้า..."
            className="pl-9 pr-4 py-2 border rounded-xl text-sm w-full sm:w-56 focus:outline-none focus:ring-2 focus:ring-[#0B1F33]/30" />
        </div>
      </div>

      {/* ── Table ───────────────────────────────────────── */}
      <div className="bg-white rounded-2xl shadow-sm overflow-x-auto">
        {loading ? (
          <table className="w-full text-sm min-w-[600px]">
            <tbody><SkeletonRows rows={8} cols={7} /></tbody>
          </table>
        ) : filtered.length === 0 ? (
          <div className="text-center py-20 text-gray-400">ไม่พบ order</div>
        ) : (
          <>
            <table className="w-full text-sm min-w-[600px]">
              <thead>
                <tr className="border-b border-gray-100">
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">Order No.</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">ลูกค้า</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">วันที่สั่ง</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-gray-500 uppercase tracking-wide">ยอดรวม</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase tracking-wide">สถานะ</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase tracking-wide">จัดการ</th>
                </tr>
              </thead>
              <tbody>
                {paginated.map(order => {
                  const cfg = STATUS_CONFIG[order.status] ?? STATUS_CONFIG.PENDING;
                  const { date, time } = fmtDate(order.createdAt);
                  return (
                    <tr key={order.id} className="border-b border-gray-50 hover:bg-gray-50/80 transition">

                      {/* Order No. */}
                      <td className="px-4 py-3.5">
                        <span className="text-indigo-600 font-mono text-xs font-semibold hover:underline cursor-pointer">
                          {order.orderNo?.replace(/^(ORD-\d{8})/, '$1-\n') ?? '-'}
                        </span>
                      </td>

                      {/* ลูกค้า */}
                      <td className="px-4 py-3.5">
                        <p className="font-semibold text-gray-800 text-sm">{order.shippingName}</p>
                        <p className="text-xs text-gray-400 mt-0.5">{order.shippingPhone}</p>
                      </td>

                      {/* วันที่ */}
                      <td className="px-4 py-3.5">
                        <p className="text-gray-700 text-sm">{date}</p>
                        <p className="text-xs text-gray-400 mt-0.5">{time}</p>
                      </td>

                      {/* ยอดรวม */}
                      <td className="px-4 py-3.5 text-right">
                        <span className="font-bold text-gray-800">฿{Number(order.totalAmount).toLocaleString()}</span>
                      </td>

                      {/* สถานะ */}
                      <td className="px-4 py-3.5 text-center">
                        <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold ${cfg.badge}`}>
                          <span className={`w-1.5 h-1.5 rounded-full ${cfg.dot}`} />
                          {cfg.label}
                        </span>
                      </td>

                      {/* จัดการ */}
                      <td className="px-4 py-3.5 text-center">
                        <div className="flex items-center justify-center gap-1.5">
                          <button onClick={() => openModal(order)}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-[#0B1F33] text-[#A8CEFF] text-xs font-semibold rounded-lg hover:bg-blue-900 transition">
                            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                            </svg>
                            จัดการ
                          </button>
                          <button onClick={() => setInvoiceOrder(order)}
                            title="ออกใบกำกับภาษี"
                            className="inline-flex items-center justify-center w-7 h-7 border border-gray-300 rounded-lg text-gray-500 hover:bg-gray-100 transition">
                            <svg xmlns="http://www.w3.org/2000/svg" className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
                            </svg>
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {/* ── Pagination ───────────────────────────── */}
            <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
              <p className="text-xs text-gray-500">
                แสดง {filtered.length === 0 ? 0 : (page - 1) * PAGE_SIZE + 1}–{Math.min(page * PAGE_SIZE, filtered.length)} จาก {filtered.length} ออเดอร์
              </p>
              <div className="flex items-center gap-1">
                {/* Prev */}
                <button onClick={() => setPage(p => Math.max(1, p - 1))} disabled={page === 1}
                  className="w-8 h-8 flex items-center justify-center rounded-lg text-gray-500 hover:bg-gray-100 disabled:opacity-30 transition text-sm">
                  ‹
                </button>

                {/* Pages */}
                {Array.from({ length: totalPages }, (_, i) => i + 1)
                  .filter(p => p === 1 || p === totalPages || Math.abs(p - page) <= 1)
                  .reduce((acc, p, idx, arr) => {
                    if (idx > 0 && p - arr[idx - 1] > 1) acc.push('...');
                    acc.push(p);
                    return acc;
                  }, [])
                  .map((p, i) =>
                    p === '...'
                      ? <span key={`dot-${i}`} className="w-8 h-8 flex items-center justify-center text-gray-400 text-xs">…</span>
                      : <button key={p} onClick={() => setPage(p)}
                          className={`w-8 h-8 flex items-center justify-center rounded-lg text-sm transition
                            ${page === p
                              ? 'border border-[#0B1F33] text-[#0B1F33] font-semibold'
                              : 'text-gray-600 hover:bg-gray-100'}`}>
                          {p}
                        </button>
                  )}

                {/* Next */}
                <button onClick={() => setPage(p => Math.min(totalPages, p + 1))} disabled={page === totalPages}
                  className="w-8 h-8 flex items-center justify-center rounded-lg text-gray-500 hover:bg-gray-100 disabled:opacity-30 transition text-sm">
                  ›
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      {/* ── Tax Invoice Modal ───────────────────────────── */}
      {invoiceOrder && (
        <TaxInvoiceModal order={invoiceOrder} onClose={() => setInvoiceOrder(null)} />
      )}

      {/* ── Modal: จัดการ Order ──────────────────────────── */}
      {selectedOrder && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4"
          onClick={e => e.target === e.currentTarget && setSelectedOrder(null)}>
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg p-6">

            <div className="flex justify-between items-start mb-5">
              <div>
                <h3 className="font-bold text-gray-800 text-lg">จัดการ Order</h3>
                <p className="text-gray-400 text-xs font-mono mt-0.5">{selectedOrder.orderNo}</p>
              </div>
              <button onClick={() => setSelectedOrder(null)} className="text-gray-400 hover:text-gray-600 text-xl leading-none">✕</button>
            </div>

            {/* ที่อยู่ */}
            <div className="bg-gray-50 rounded-xl p-4 mb-4 text-sm">
              <p className="font-semibold text-gray-700">{selectedOrder.shippingName} · {selectedOrder.shippingPhone}</p>
              <p className="text-gray-500 mt-0.5 text-xs">
                {selectedOrder.shippingAddress} ต.{selectedOrder.shippingSubdistrict} อ.{selectedOrder.shippingDistrict} {selectedOrder.shippingProvince} {selectedOrder.shippingPostcode}
              </p>
              {selectedOrder.note && <p className="text-orange-500 mt-1.5 text-xs">📝 {selectedOrder.note}</p>}
            </div>

            {/* รายการสินค้า */}
            <div className="mb-4 space-y-1.5 max-h-40 overflow-y-auto">
              {(selectedOrder.orderLines ?? []).map(line => (
                <div key={line.id} className="flex justify-between text-sm text-gray-600">
                  <span>{line.productName} × {line.quantity}</span>
                  <span className="font-medium">฿{Number(line.totalPrice).toLocaleString()}</span>
                </div>
              ))}
              <div className="border-t pt-1.5 flex justify-between font-bold text-gray-800 text-sm">
                <span>รวมทั้งสิ้น</span>
                <span>฿{Number(selectedOrder.totalAmount).toLocaleString()}</span>
              </div>
            </div>

            {/* เปลี่ยนสถานะ */}
            <div className="mb-3">
              <label className="text-xs font-semibold text-gray-600 block mb-1.5">สถานะ</label>
              <select value={newStatus} onChange={e => setNewStatus(e.target.value)}
                className="w-full border rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#0B1F33]/30">
                {ALL_STATUSES.map(s => (
                  <option key={s} value={s}>{STATUS_CONFIG[s].label}</option>
                ))}
              </select>
            </div>

            {/* Tracking */}
            <div className="mb-5">
              <label className="text-xs font-semibold text-gray-600 block mb-1.5">
                Tracking Number <span className="text-gray-400 font-normal">(ถ้ามี)</span>
              </label>
              <input type="text" value={trackingNo} onChange={e => setTrackingNo(e.target.value)}
                placeholder="เช่น TH123456789TH"
                className="w-full border rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#0B1F33]/30" />
            </div>

            <div className="flex gap-3">
              <button onClick={() => setSelectedOrder(null)}
                className="flex-1 py-2.5 border rounded-xl text-gray-600 hover:bg-gray-50 text-sm font-medium transition">
                ยกเลิก
              </button>
              <button onClick={handleSave} disabled={saving}
                className="flex-1 py-2.5 bg-[#0B1F33] text-[#A8CEFF] rounded-xl font-semibold text-sm hover:bg-blue-900 disabled:opacity-50 transition">
                {saving ? 'กำลังบันทึก...' : 'บันทึก'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
