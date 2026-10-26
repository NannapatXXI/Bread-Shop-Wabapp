'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Icon from '@/app/components/Icon';
import {
  getLogSummary,
  getActivityLogs, getAuditLogs, getOrderLogs
} from '@/services/log.service';
import toast from 'react-hot-toast';

// ─────────────────────────────────────────────────────────────────────────────
// Helpers / shared UI
// ─────────────────────────────────────────────────────────────────────────────

const ACTION_BG = {
  LOGIN: 'b-green', LOGOUT: '', REGISTER: 'b-blue', UPDATE_PROFILE: 'b-violet',
  ADD_TO_CART: 'b-amber', CHECKOUT: 'b-blue', PAYMENT: 'b-green',
};
const AUDIT_ACTION_BG = {
  CREATE: 'b-green', UPDATE: 'b-blue', DELETE: 'b-red', CHANGE_ROLE: 'b-violet',
  CHANGE_ORDER_STATUS: 'b-amber', BAN_USER: 'b-red',
};
const ORDER_STATUS_BG = {
  PENDING: '', CONFIRMED: 'b-blue', PROCESSING: 'b-violet', SHIPPED: 'b-violet',
  DELIVERED: 'b-green', CANCELLED: 'b-red', REFUNDED: 'b-amber',
};

function ActionBadge({ action, map = ACTION_BG }) {
  return <span className={`badge plain ${map[action] ?? ''}`}>{action}</span>;
}
function StatusBadge({ status }) {
  return <span className={`badge ${status === 'SUCCESS' ? 'b-green' : 'b-red'}`}>{status}</span>;
}
function SummaryCard({ label, value, note }) {
  return (
    <div className="stat">
      <div className="k">{label}</div>
      <div className="v">{value?.toLocaleString() ?? '—'}</div>
      {note && <div className="d">{note}</div>}
    </div>
  );
}
function EmptyState({ text = 'ยังไม่มีข้อมูล' }) {
  return <div className="muted" style={{ textAlign: 'center', padding: '40px 16px', fontSize: 14 }}>{text}</div>;
}
function LoadingRow({ cols }) {
  return Array.from({ length: 8 }).map((_, i) => (
    <tr key={i}>
      {Array.from({ length: cols }).map((__, j) => (
        <td key={j}><div className="sk" style={{ height: 14, width: '75%' }} /></td>
      ))}
    </tr>
  ));
}
function Pagination({ page, totalPages, totalItems, pageSize, onPrev, onNext, onPage }) {
  if (totalPages <= 1) return null;
  const from = totalItems === 0 ? 0 : page * pageSize + 1;
  const to   = Math.min((page + 1) * pageSize, totalItems ?? 0);
  const pageNums = Array.from({ length: totalPages }, (_, i) => i)
    .filter(p => p === 0 || p === totalPages - 1 || Math.abs(p - page) <= 1)
    .reduce((acc, p, idx, arr) => {
      if (idx > 0 && p - arr[idx - 1] > 1) acc.push('...');
      acc.push(p);
      return acc;
    }, []);
  return (
    <div className="pager">
      <span>แสดง {from}–{to} จาก {totalItems?.toLocaleString()} รายการ</span>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
        <button className="btn btn-outline sm" onClick={onPrev} disabled={page === 0}>ก่อนหน้า</button>
        {pageNums.map((p, i) =>
          p === '...'
            ? <span key={`dot-${i}`} className="faint">…</span>
            : <button key={p} className={`btn sm ${page === p ? 'btn-dark' : 'btn-outline'}`} onClick={() => onPage?.(p)}
                aria-current={page === p ? 'page' : undefined}>{p + 1}</button>
        )}
        <button className="btn btn-outline sm" onClick={onNext} disabled={page >= totalPages - 1}>ถัดไป</button>
      </div>
    </div>
  );
}

function ExportButton({ data, filename }) {
  const exportCSV = () => {
    if (!data?.length) return;
    const keys = Object.keys(data[0]);
    const csv = [keys.join(','), ...data.map(r => keys.map(k => JSON.stringify(r[k] ?? '')).join(','))].join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    Object.assign(document.createElement('a'), { href: url, download: filename + '.csv' }).click();
    URL.revokeObjectURL(url);
  };
  const exportJSON = () => {
    if (!data?.length) return;
    const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }));
    Object.assign(document.createElement('a'), { href: url, download: filename + '.json' }).click();
    URL.revokeObjectURL(url);
  };
  return (
    <div style={{ display: 'flex', gap: 8 }}>
      <button onClick={exportCSV} className="btn btn-outline sm"><Icon name="download" size="sm" />CSV</button>
      <button onClick={exportJSON} className="btn btn-outline sm"><Icon name="download" size="sm" />JSON</button>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB: Dashboard
// ─────────────────────────────────────────────────────────────────────────────

// log ของระบบ (Logback) + สถิติ API ย้ายไปดูใน Grafana — ดู docker-compose.monitoring.yml
const GRAFANA_URL = process.env.NEXT_PUBLIC_GRAFANA_URL || 'http://localhost:3001';

function DashboardTab({ summary }) {
  const cards = [
    { label: 'User Activity',        value: summary?.totalActivity,    note: 'ทั้งหมด' },
    { label: 'Audit Logs',           value: summary?.totalAudit,       note: 'ทั้งหมด' },
    { label: 'เข้าสู่ระบบ',            value: summary?.loginCount,       note: 'ทั้งหมด' },
    { label: 'ผู้ใช้วันนี้',            value: summary?.activeUsersToday, note: 'active' },
  ];

  return (
    <div className="space-y-6">
      <div className="stats" style={{ marginBottom: 0 }}>
        {cards.map((c, i) => <SummaryCard key={i} {...c} />)}
      </div>

      <section className="card pad" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h2 style={{ margin: 0 }}>Log ของระบบและสถิติ API</h2>
          <p className="muted" style={{ margin: '4px 0 0' }}>error ของ server, เวลาตอบ, request ต่อวินาที — ดูแบบ realtime ใน Grafana</p>
        </div>
        <a className="btn btn-outline" href={`${GRAFANA_URL}/d/breadshop-backend`} target="_blank" rel="noopener noreferrer">
          เปิด Grafana <Icon name="right" size="sm" />
        </a>
      </section>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB: User Activity Log
// ─────────────────────────────────────────────────────────────────────────────

function UserActivityTab() {
  const [data, setData]           = useState([]);
  const [loading, setLoading]     = useState(false);
  const [keyword, setKeyword]     = useState('');
  const [action, setAction]       = useState('');
  const [status, setStatus]       = useState('');
  const [page, setPage]             = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getActivityLogs({ keyword, action: action || undefined, status: status || undefined, page, size: 20 });
      setData(res.content || []);
      setTotalPages(res.totalPages || 0);
      setTotalItems(res.totalElements || 0);
    } catch { toast.error('โหลด Activity Log ไม่สำเร็จ'); }
    finally { setLoading(false); }
  }, [keyword, action, status, page]);

  useEffect(() => { load(); }, [load]);

  return (
    <div className="space-y-4">
      <div className="card toolbar" style={{ justifyContent: 'flex-start' }}>
        <label className="search" style={{ flex: 1, minWidth: 192 }}>
          <Icon name="search" size="sm" />
          <input placeholder="ค้นหา username / details..." aria-label="ค้นหา username / details..."
            value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} />
        </label>
        <select className="control" value={action} onChange={e => { setAction(e.target.value); setPage(0); }}>
          <option value="">All Actions</option>
          {['LOGIN','LOGOUT','REGISTER','UPDATE_PROFILE','ADD_TO_CART','CHECKOUT','PAYMENT'].map(a => <option key={a}>{a}</option>)}
        </select>
        <select className="control" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
          <option value="">All Status</option>
          <option>SUCCESS</option><option>FAILURE</option>
        </select>
        <button onClick={load} className="btn btn-outline sm" aria-label="รีเฟรช" title="รีเฟรช"><Icon name="refresh" size="sm" /></button>
        <ExportButton data={data} filename="user_activity_logs" />
      </div>

      <div className="card table-wrap">
        <table className="t" style={{ minWidth: 700 }}>
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>User</th>
              <th>Action</th>
              <th>IP Address</th>
              <th>Details</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {loading ? <LoadingRow cols={6} />
              : data.length === 0 ? <tr><td colSpan={6}><EmptyState /></td></tr>
              : data.map(log => (
                <tr key={log.id}>
                  <td className="num" style={{ whiteSpace: 'nowrap', fontSize: 13, color: 'var(--muted)' }}>{log.createdAt?.replace('T',' ').slice(0,19)}</td>
                  <td style={{ fontWeight: 600 }}>{log.username || `#${log.userId}`}</td>
                  <td><ActionBadge action={log.action} /></td>
                  <td className="mono" style={{ color: 'var(--muted)' }}>{log.ipAddress || '—'}</td>
                  <td style={{ fontSize: 13, maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{log.details || '—'}</td>
                  <td><StatusBadge status={log.status} /></td>
                </tr>
              ))
            }
          </tbody>
        </table>
        <Pagination page={page} totalPages={totalPages} totalItems={totalItems} pageSize={20} onPrev={() => setPage(p => p-1)} onNext={() => setPage(p => p+1)} onPage={setPage} />
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB: Audit Log
// ─────────────────────────────────────────────────────────────────────────────

function AuditLogTab() {
  const [data, setData]           = useState([]);
  const [loading, setLoading]     = useState(false);
  const [keyword, setKeyword]     = useState('');
  const [action, setAction]       = useState('');
  const [entityType, setEntityType] = useState('');
  const [expanded, setExpanded]   = useState(null);
  const [page, setPage]             = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getAuditLogs({ keyword, action: action || undefined, entityType: entityType || undefined, page, size: 20 });
      setData(res.content || []);
      setTotalPages(res.totalPages || 0);
      setTotalItems(res.totalElements || 0);
    } catch { toast.error('โหลด Audit Log ไม่สำเร็จ'); }
    finally { setLoading(false); }
  }, [keyword, action, entityType, page]);

  useEffect(() => { load(); }, [load]);

  return (
    <div className="space-y-4">
      <div className="card toolbar" style={{ justifyContent: 'flex-start' }}>
        <label className="search" style={{ flex: 1, minWidth: 192 }}>
          <Icon name="search" size="sm" />
          <input placeholder="ค้นหา actor / details..." aria-label="ค้นหา actor / details..."
            value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0); }} />
        </label>
        <select className="control" value={action} onChange={e => { setAction(e.target.value); setPage(0); }}>
          <option value="">All Actions</option>
          {['CREATE','UPDATE','DELETE','CHANGE_ROLE','CHANGE_ORDER_STATUS','BAN_USER'].map(a => <option key={a}>{a}</option>)}
        </select>
        <select className="control" value={entityType} onChange={e => { setEntityType(e.target.value); setPage(0); }}>
          <option value="">All Entities</option>
          {['Product','Order','User','Promotion'].map(e => <option key={e}>{e}</option>)}
        </select>
        <button onClick={load} className="btn btn-outline sm" aria-label="รีเฟรช" title="รีเฟรช"><Icon name="refresh" size="sm" /></button>
        <ExportButton data={data} filename="audit_logs" />
      </div>

      <div className="card table-wrap">
        <table className="t" style={{ minWidth: 700 }}>
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>ผู้ดำเนินการ</th>
              <th>Role</th>
              <th>Action</th>
              <th>Entity</th>
              <th>Details</th>
              <th style={{ width: 32 }}></th>
            </tr>
          </thead>
          <tbody>
            {loading ? <LoadingRow cols={7} />
              : data.length === 0 ? <tr><td colSpan={7}><EmptyState /></td></tr>
              : data.map(log => (
                <React.Fragment key={log.id}>
                  <tr>
                    <td className="num" style={{ whiteSpace: 'nowrap', fontSize: 13, color: 'var(--muted)' }}>{log.createdAt?.replace('T',' ').slice(0,19)}</td>
                    <td style={{ fontWeight: 600 }}>{log.actorName || `#${log.actorId}`}</td>
                    <td><span className={`badge plain ${log.actorRole==='ADMIN'?'b-violet':''}`}>{log.actorRole}</span></td>
                    <td><ActionBadge action={log.action} map={AUDIT_ACTION_BG} /></td>
                    <td style={{ fontSize: 13 }}>{log.entityType} #{log.entityId}</td>
                    <td style={{ fontSize: 13, maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{log.details || '—'}</td>
                    <td>
                      {(log.oldValue || log.newValue) && (
                        <button onClick={() => setExpanded(expanded === log.id ? null : log.id)} className="icon-btn sm" aria-label="ดูค่าเก่า/ใหม่">
                          <Icon name={expanded === log.id ? 'minus' : 'plus'} size="sm" />
                        </button>
                      )}
                    </td>
                  </tr>
                  {expanded === log.id && (
                    <tr style={{ background: 'var(--blue-soft)' }}>
                      <td colSpan={7}>
                        <div className="grid-2" style={{ fontSize: 12, fontFamily: 'ui-monospace,Menlo,monospace' }}>
                          <div><p className="muted" style={{ fontFamily: 'var(--font)', margin: '0 0 4px' }}>Old Value</p><pre style={{ background: 'var(--surface)', padding: 8, borderRadius: 6, border: '1px solid var(--line)', whiteSpace: 'pre-wrap', margin: 0 }}>{log.oldValue || '—'}</pre></div>
                          <div><p className="muted" style={{ fontFamily: 'var(--font)', margin: '0 0 4px' }}>New Value</p><pre style={{ background: 'var(--surface)', padding: 8, borderRadius: 6, border: '1px solid var(--line)', whiteSpace: 'pre-wrap', margin: 0 }}>{log.newValue || '—'}</pre></div>
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))
            }
          </tbody>
        </table>
        <Pagination page={page} totalPages={totalPages} totalItems={totalItems} pageSize={20} onPrev={() => setPage(p => p-1)} onNext={() => setPage(p => p+1)} onPage={setPage} />
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB: Order Logs
// ─────────────────────────────────────────────────────────────────────────────

function OrderLogTab() {
  const [data, setData]           = useState([]);
  const [loading, setLoading]     = useState(false);
  const [orderNo, setOrderNo]     = useState('');
  const [status, setStatus]       = useState('');
  const [page, setPage]             = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getOrderLogs({ orderNo: orderNo || undefined, status: status || undefined, page, size: 20 });
      setData(res.content || []);
      setTotalPages(res.totalPages || 0);
      setTotalItems(res.totalElements || 0);
    } catch { toast.error('โหลด Order Log ไม่สำเร็จ'); }
    finally { setLoading(false); }
  }, [orderNo, status, page]);

  useEffect(() => { load(); }, [load]);

  return (
    <div className="space-y-4">
      <div className="card toolbar" style={{ justifyContent: 'flex-start' }}>
        <label className="search" style={{ flex: 1, minWidth: 192 }}>
          <Icon name="search" size="sm" />
          <input placeholder="ค้นหาเลข Order..." aria-label="ค้นหาเลข Order..."
            value={orderNo} onChange={e => { setOrderNo(e.target.value); setPage(0); }} />
        </label>
        <select className="control" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
          <option value="">All Status</option>
          {['PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED','REFUNDED'].map(s => <option key={s}>{s}</option>)}
        </select>
        <button onClick={load} className="btn btn-outline sm" aria-label="รีเฟรช" title="รีเฟรช"><Icon name="refresh" size="sm" /></button>
        <ExportButton data={data} filename="order_logs" />
      </div>

      <div className="card table-wrap">
        <table className="t" style={{ minWidth: 700 }}>
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Order No</th>
              <th>Old Status</th>
              <th>New Status</th>
              <th>ผู้เปลี่ยน</th>
              <th>Tracking No</th>
            </tr>
          </thead>
          <tbody>
            {loading ? <LoadingRow cols={6} />
              : data.length === 0 ? <tr><td colSpan={6}><EmptyState /></td></tr>
              : data.map(log => (
                <tr key={log.id}>
                  <td className="num" style={{ whiteSpace: 'nowrap', fontSize: 13, color: 'var(--muted)' }}>{log.createdAt?.replace('T',' ').slice(0,19)}</td>
                  <td><span className="mono">{log.orderNo}</span></td>
                  <td><span className={`badge plain ${ORDER_STATUS_BG[log.oldStatus] ?? ''}`}>{log.oldStatus||'—'}</span></td>
                  <td><span className={`badge plain ${ORDER_STATUS_BG[log.newStatus] ?? ''}`}>{log.newStatus}</span></td>
                  <td style={{ fontSize: 14 }}>{log.changedByName||'system'}</td>
                  <td className="mono" style={{ color: 'var(--muted)' }}>{log.trackingNo||'—'}</td>
                </tr>
              ))
            }
          </tbody>
        </table>
        <Pagination page={page} totalPages={totalPages} totalItems={totalItems} pageSize={20} onPrev={() => setPage(p => p-1)} onNext={() => setPage(p => p+1)} onPage={setPage} />
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Page
// ─────────────────────────────────────────────────────────────────────────────

const TABS = [
  { id: 'dashboard', label: 'ภาพรวม',       icon: 'grid' },
  { id: 'activity',  label: 'User Activity', icon: 'user' },
  { id: 'audit',     label: 'Audit',        icon: 'shield' },
  { id: 'orders',    label: 'Order',        icon: 'receipt' },
];

export default function AdminLogsPage() {
  const [activeTab, setActiveTab] = useState('dashboard');
  const [summary, setSummary]     = useState(null);

  useEffect(() => {
    getLogSummary().then(setSummary).catch(() => {});
  }, []);

  const renderTab = () => {
    switch (activeTab) {
      case 'dashboard': return <DashboardTab summary={summary} />;
      case 'activity':  return <UserActivityTab />;
      case 'audit':     return <AuditLogTab />;
      case 'orders':    return <OrderLogTab />;
    }
  };

  return (
    <>
      <div className="page-head">
        <div><h1>Logs</h1><p>ติดตามการใช้งานและความผิดปกติของระบบ</p></div>
      </div>

      <div className="tabs" style={{ marginBottom: 24 }} role="tablist">
        {TABS.map(tab => (
          <button key={tab.id} role="tab" aria-selected={activeTab === tab.id}
            className={`tab${activeTab === tab.id ? ' on' : ''}`} onClick={() => setActiveTab(tab.id)}>
            <Icon name={tab.icon} size="sm" />{tab.label}
          </button>
        ))}
      </div>

      {renderTab()}
    </>
  );
}
