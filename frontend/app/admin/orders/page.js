'use client';

import { useEffect, useState, useMemo } from 'react';
import toast from 'react-hot-toast';
import { adminGetAllOrders, adminUpdateOrderStatus } from '@/services/auth.service';
import { exportCSV } from '@/lib/exportCSV';
import TaxInvoiceModal from '@/app/components/TaxInvoiceModal';
import { SkeletonRows } from '@/app/components/Skeleton';
import Icon from '@/app/components/Icon';
import StatusBadge from '@/app/components/StatusBadge';

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

  const tabs = [
    { key: 'ALL', label: 'ทั้งหมด', count: orders.length },
    ...ALL_STATUSES.map(s => ({ key: s, label: STATUS_CONFIG[s].label, count: orders.filter(o => o.status === s).length })),
  ];

  const pageItems = Array.from({ length: totalPages }, (_, i) => i + 1)
    .filter(p => p === 1 || p === totalPages || Math.abs(p - page) <= 1)
    .reduce((acc, p, idx, arr) => {
      if (idx > 0 && p - arr[idx - 1] > 1) acc.push('...');
      acc.push(p);
      return acc;
    }, []);

  return (
    <>
      <div className="page-head">
        <div><h1>ออเดอร์</h1><p>จัดการคำสั่งซื้อและอัปเดตสถานะการจัดส่ง</p></div>
        <div className="actions">
          <button className="btn btn-outline" onClick={() => exportCSV(
            filtered,
            ['เลขออเดอร์', 'ชื่อผู้รับ', 'เบอร์โทร', 'ที่อยู่', 'จังหวัด', 'ยอดรวม', 'สถานะ', 'Tracking', 'หมายเหตุ', 'วันที่สั่ง'],
            ['orderNo', 'shippingName', 'shippingPhone', 'shippingAddress', 'shippingProvince', 'totalAmount', 'status', 'trackingNo', 'note', 'createdAt'],
            'orders'
          )}>
            <Icon name="download" size="sm" />Export
          </button>
        </div>
      </div>

      <section className="card">
        <div className="tabs" style={{ padding: '0 16px' }} role="tablist">
          {tabs.map(t => (
            <button key={t.key} role="tab" aria-selected={filterStatus === t.key}
              className={`tab${filterStatus === t.key ? ' on' : ''}`}
              onClick={() => { setFilterStatus(t.key); setPage(1); }}>
              {t.label} <span className={`badge plain${t.key === 'PENDING' && t.count > 0 ? ' b-amber' : ''}`}>{t.count}</span>
            </button>
          ))}
        </div>
        <div className="toolbar">
          <label className="search" style={{ flex: 1, maxWidth: 360 }}>
            <Icon name="search" size="sm" />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="ค้นหาเลขออเดอร์ ชื่อ หรือเบอร์โทร" aria-label="ค้นหาออเดอร์" />
          </label>
        </div>

        <div className="table-wrap">
          {loading ? (
            <table className="t"><tbody><SkeletonRows rows={8} cols={6} /></tbody></table>
          ) : filtered.length === 0 ? (
            <div className="empty" style={{ border: 0 }}>
              <div className="ic"><Icon name="receipt" /></div>
              <h3>ไม่พบออเดอร์</h3>
              <p>ลองเปลี่ยนสถานะหรือคำค้นหา</p>
            </div>
          ) : (
            <table className="t">
              <thead>
                <tr><th>เลขออเดอร์</th><th className="hide-m">ลูกค้า</th><th className="hide-m">วันที่สั่ง</th><th className="right">ยอดรวม</th><th>สถานะ</th><th></th></tr>
              </thead>
              <tbody>
                {paginated.map(order => {
                  const cfg = STATUS_CONFIG[order.status] ?? STATUS_CONFIG.PENDING;
                  const { date, time } = fmtDate(order.createdAt);
                  return (
                    <tr key={order.id}>
                      <td><span className="mono">{order.orderNo ?? '-'}</span><span className="sub">{date} · {order.shippingName}</span></td>
                      <td className="hide-m"><b style={{ fontWeight: 600 }}>{order.shippingName}</b><span className="sub">{order.shippingPhone}</span></td>
                      <td className="num hide-m">{date}<span className="sub">{time}</span></td>
                      <td className="num right"><b style={{ fontWeight: 600 }}>฿{Number(order.totalAmount).toLocaleString()}</b></td>
                      <td><StatusBadge status={order.status} label={cfg.label} /></td>
                      <td>
                        <div className="row-actions">
                          <button className="btn btn-outline sm" onClick={() => openModal(order)}>จัดการ</button>
                          <button className="icon-btn sm hide-m" onClick={() => setInvoiceOrder(order)} aria-label="ออกใบกำกับภาษี" title="ออกใบกำกับภาษี">
                            <Icon name="note" size="sm" />
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

        {!loading && filtered.length > 0 && (
          <div className="pager">
            <span>แสดง {(page - 1) * PAGE_SIZE + 1}–{Math.min(page * PAGE_SIZE, filtered.length)} จาก {filtered.length} ออเดอร์</span>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
              <button className="btn btn-outline sm" onClick={() => setPage(p => Math.max(1, p - 1))} disabled={page === 1}>ก่อนหน้า</button>
              {pageItems.map((p, i) => p === '...'
                ? <span key={`dot-${i}`} className="faint">…</span>
                : <button key={p} className={`btn sm ${page === p ? 'btn-dark' : 'btn-outline'}`} onClick={() => setPage(p)}
                    aria-current={page === p ? 'page' : undefined}>{p}</button>)}
              <button className="btn btn-outline sm" onClick={() => setPage(p => Math.min(totalPages, p + 1))} disabled={page === totalPages}>ถัดไป</button>
            </div>
          </div>
        )}
      </section>

      {invoiceOrder && (
        <TaxInvoiceModal order={invoiceOrder} onClose={() => setInvoiceOrder(null)} />
      )}

      {selectedOrder && (
        <div className="modal-scrim" onClick={e => e.target === e.currentTarget && setSelectedOrder(null)}>
          <div className="modal" role="dialog" aria-label="จัดการออเดอร์" style={{ maxWidth: 520 }}>
            <div className="modal-head">
              <div>
                <h2>จัดการออเดอร์</h2>
                <span className="mono" style={{ fontFamily: 'ui-monospace,Menlo,monospace', fontSize: 13, color: 'var(--blue)' }}>{selectedOrder.orderNo}</span>
              </div>
              <button className="icon-btn" onClick={() => setSelectedOrder(null)} aria-label="ปิด"><Icon name="x" /></button>
            </div>
            <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ background: 'var(--bg)', borderRadius: 'var(--r-md)', padding: 14, fontSize: 14 }}>
                <b style={{ fontWeight: 600 }}>{selectedOrder.shippingName} · {selectedOrder.shippingPhone}</b>
                <div className="muted">
                  {selectedOrder.shippingAddress} ต.{selectedOrder.shippingSubdistrict} อ.{selectedOrder.shippingDistrict} {selectedOrder.shippingProvince} {selectedOrder.shippingPostcode}
                </div>
                {selectedOrder.note && (
                  <div style={{ color: 'var(--amber)', marginTop: 6, display: 'flex', gap: 6, alignItems: 'center' }}><Icon name="note" size="sm" />{selectedOrder.note}</div>
                )}
              </div>

              <div className="rows" style={{ maxHeight: 160, overflowY: 'auto' }}>
                {(selectedOrder.orderLines ?? []).map(line => (
                  <div className="r" key={line.id}>
                    <span>{line.productName} <span className="muted">× {line.quantity}</span></span>
                    <span className="num">฿{Number(line.totalPrice).toLocaleString()}</span>
                  </div>
                ))}
                <div className="r total"><span>รวมทั้งสิ้น</span><b className="num">฿{Number(selectedOrder.totalAmount).toLocaleString()}</b></div>
              </div>

              <div className="field">
                <label htmlFor="ord-status">สถานะ</label>
                <div className="control">
                  <select id="ord-status" value={newStatus} onChange={e => setNewStatus(e.target.value)}>
                    {ALL_STATUSES.map(s => <option key={s} value={s}>{STATUS_CONFIG[s].label}</option>)}
                  </select>
                </div>
              </div>
              <div className="field">
                <label htmlFor="ord-track">Tracking Number <span className="faint" style={{ fontWeight: 400 }}>(ถ้ามี)</span></label>
                <div className="control">
                  <input id="ord-track" type="text" value={trackingNo} onChange={e => setTrackingNo(e.target.value)} placeholder="เช่น TH123456789TH" />
                </div>
              </div>
            </div>
            <div className="modal-foot">
              <button className="btn btn-outline" style={{ flex: 1 }} onClick={() => setSelectedOrder(null)}>ยกเลิก</button>
              <button className="btn btn-primary" style={{ flex: 1 }} onClick={handleSave} disabled={saving}>
                {saving ? 'กำลังบันทึก...' : 'บันทึก'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
